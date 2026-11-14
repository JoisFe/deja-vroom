(() => {
  // The key is injected into <meta name="kakao-js-key"> at deploy time (GitHub Actions) or by scripts/roadview/serve.py.
  const appKey = ((document.querySelector('meta[name="kakao-js-key"]') || {}).content || "").trim();
  // Nearest-panorama search radii in meters: tight first so the view matches the junction, then one wider retry.
  const PANO_SEARCH_RADII = [50, 100];
  const status = document.getElementById("status");
  const container = document.getElementById("roadview");
  let sdkReady = false;
  let initialized = false;
  let pendingConfig = null;
  let roadview;
  let nativeArrowObserver;
  let nativeArrowScaleObserver;
  let lastScaledArrowCount = 0;
  let overlayAttempted = false;
  let junctionOverlayAttempted = false;
  let junctionOverlayRendered = false;
  let candidateOverlayRendered = false;
  const routeOverlays = [];

  const send = (message) => {
    if (window.DejaVroomBridge && typeof window.DejaVroomBridge.postMessage === "function") {
      window.DejaVroomBridge.postMessage(message);
    }
  };
  const fail = (reason, text) => {
    status.textContent = text;
    document.body.classList.add("failed");
    if (document.body.classList.contains("thumbnail")) status.classList.add("thumbnail-error");
    send(`fail:${reason}`);
  };

  window.addEventListener("securitypolicyviolation", (event) => send(`csp-blocked:${event.violatedDirective}:${event.blockedURI}`));

  if (!window.DejaVroomBridge) {
    const params = new URLSearchParams(window.location.search);
    const latitude = Number(params.get("lat"));
    const longitude = Number(params.get("lng"));
    const thumbnail = params.has("thumb");
    if (thumbnail) document.body.classList.add("thumbnail");
    const validCoordinate = params.has("lat") && params.has("lng") &&
      Number.isFinite(latitude) && latitude >= -90 && latitude <= 90 &&
      Number.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    if (!validCoordinate) {
      fail("config", "lat, lng 파라미터가 필요합니다. 예: ?lat=37.497952&lng=127.027619");
      return;
    }
    pendingConfig = { latitude, longitude, heading: Number(params.get("heading")) || 0, thumbnail };
    status.textContent = "Kakao Roadview를 준비하고 있습니다.";
  }

  if (!appKey || appKey.includes("__KAKAO_")) {
    fail("config", "Kakao JavaScript 키가 페이지에 주입되지 않았습니다. docs/roadview-setup.md를 확인하세요.");
    return;
  }

  const sdk = document.createElement("script");
  sdk.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
  sdk.async = true;
  const sdkLoadTimer = window.setTimeout(() => fail("sdk-load", "Kakao SDK 응답 시간이 초과되었습니다. 키·도메인·네트워크를 확인하세요."), 10000);
  sdk.onerror = () => {
    window.clearTimeout(sdkLoadTimer);
    fail("sdk-load", "Kakao SDK를 불러오지 못했습니다. 키·도메인·네트워크를 확인하세요.");
  };
  sdk.onload = () => {
    window.clearTimeout(sdkLoadTimer);
    send("sdk-loaded");
    if (!window.kakao || !window.kakao.maps) {
      fail("sdk-load", "Kakao Maps SDK를 사용할 수 없습니다.");
      return;
    }
    window.kakao.maps.load(() => {
      sdkReady = true;
      send("sdk-ready");
      if (pendingConfig) initializeRoadview(pendingConfig);
    });
  };
  document.head.appendChild(sdk);

  function initializeRoadview(config) {
    if (initialized || !sdkReady) return;
    initialized = true;
    status.textContent = "가까운 파노라마를 찾고 있습니다.";
    const position = new window.kakao.maps.LatLng(config.latitude, config.longitude);
    const client = new window.kakao.maps.RoadviewClient();
    const timer = window.setTimeout(() => fail("timeout", "파노라마 조회 시간이 초과되었습니다."), 8000);
    const showPano = (panoId, selectedPosition = position) => {
      if (!panoId) {
        window.clearTimeout(timer);
        fail("no-pano", `선택 지점 반경 ${PANO_SEARCH_RADII[PANO_SEARCH_RADII.length - 1]}m 안에 로드뷰가 없습니다.`);
        return;
      }
      try {
        roadview = new window.kakao.maps.Roadview(container);
        window.kakao.maps.event.addListener(roadview, "init", () => {
          window.clearTimeout(timer);
          roadview.relayout();
          roadview.setViewpoint({ pan: Number(config.heading) || 0, tilt: 0, zoom: 0 });
          inspectNativeArrowLayer();
          renderSelectedCandidate(config);
          status.textContent = config.selectedCandidate
            ? `${config.selectedCandidate.name} · 복잡 교차로 후보 표시 중`
            : "Kakao Roadview";
          if (config.thumbnail) status.style.display = "none";
          send("shown");
        });
        roadview.setPanoId(panoId, selectedPosition);
      } catch (failure) {
        window.clearTimeout(timer);
        console.error(`roadview-render-failed ${failure && failure.stack || failure}`);
        fail("render", pendingConfig && pendingConfig.thumbnail
          ? `Roadview 미리보기를 열지 못했습니다${failure && failure.message ? `: ${failure.message}` : ""}`
          : "파노라마 화면을 표시하지 못했습니다.");
      }
    };
    if (config.panoId) showPano(config.panoId);
    else if (Array.isArray(config.candidates) && config.candidates.length > 0) {
      findCandidatePanorama(client, config, config.candidates.slice(0, 5), 0, timer, showPano);
    } else findNearestPano(client, position, (panoId) => showPano(panoId, position));
  }

  function findNearestPano(client, position, callback, radiusIndex = 0) {
    client.getNearestPanoId(position, PANO_SEARCH_RADII[radiusIndex], (panoId) => {
      if (panoId || radiusIndex >= PANO_SEARCH_RADII.length - 1) callback(panoId);
      else findNearestPano(client, position, callback, radiusIndex + 1);
    });
  }

  function findCandidatePanorama(client, config, candidates, index, timer, showPano) {
    if (index >= candidates.length) {
      window.clearTimeout(timer);
      status.textContent = "분석된 후보 주변에서 Kakao Roadview를 찾지 못했습니다.";
      send("candidate-no-coverage");
      return;
    }
    const candidate = candidates[index];
    const position = new window.kakao.maps.LatLng(candidate.latitude, candidate.longitude);
    findNearestPano(client, position, (panoId) => {
      if (!panoId) return findCandidatePanorama(client, config, candidates, index + 1, timer, showPano);
      config.latitude = candidate.latitude;
      config.longitude = candidate.longitude;
      config.heading = candidate.headingDegrees;
      config.selectedCandidate = candidate;
      config.selectedCandidateIndex = Number.isInteger(candidate.displayIndex) ? candidate.displayIndex : index;
      config.panoId = panoId;
      showPano(panoId, position);
    });
  }

  function renderSelectedCandidate(config) {
    const candidate = config && config.selectedCandidate;
    if (candidateOverlayRendered || !candidate || !Number.isFinite(candidate.latitude) || !Number.isFinite(candidate.longitude)) return;
    candidateOverlayRendered = true;
    renderLaneGuidance(candidate);
    const marker = document.createElement("span");
    marker.className = "firstlook-junction-mark caution";
    marker.textContent = "!";
    marker.setAttribute("aria-hidden", "true");
    const overlay = new window.kakao.maps.CustomOverlay({
      position: new window.kakao.maps.LatLng(candidate.latitude, candidate.longitude),
      content: marker,
      xAnchor: 0.5,
      yAnchor: 0.5,
      clickable: false,
    });
    overlay.setRange(120);
    overlay.setMap(roadview);
    routeOverlays.push(overlay);
    const evidence = (candidate.evidence || []).slice(0, 2).join(" · ");
    const label = document.createElement("div");
    label.className = "firstlook-candidate-label";
    label.textContent = `${candidate.name} · 복잡도 ${Math.round(candidate.score * 100)}점${evidence ? ` · ${evidence}` : ""}`;
    document.body.appendChild(label);
    document.getElementById("attribution").textContent = "Kakao Maps · © OpenStreetMap contributors · ODbL 1.0 · OSRM";
    send(`route-candidate-selected:${config.selectedCandidateIndex}`);
  }

  function renderLaneGuidance(candidate) {
    const panel = document.getElementById("segment-context");
    const lanes = Array.isArray(candidate.routeLanes) ? candidate.routeLanes : [];
    const validLanes = lanes.filter((lane) => lane.validForRoute === true);
    document.getElementById("lane-title").textContent = candidate.name || "선택한 경로 구간";
    const evidence = Array.isArray(candidate.evidence) ? candidate.evidence.slice(0, 2).join(" · ") : "";
    const score = Math.round((Number(candidate.score) || 0) * 100);
    document.getElementById("lane-subtitle").textContent = [
      `경로 분석 점수 ${score}점`,
      evidence,
      validLanes.length ? "초록색 차로가 경로에 유효합니다." : "노면 화살표가 경로 진행 방향을 나타냅니다."
    ].filter(Boolean).join(" · ");
    const laneList = document.getElementById("lane-list");
    laneList.replaceChildren();
    laneList.hidden = lanes.length === 0;
    for (const [index, lane] of lanes.entries()) {
      const slot = document.createElement("span");
      slot.className = `lane-slot${lane.validForRoute ? " valid" : ""}`;
      slot.textContent = (lane.indications || []).map(indicationArrow).join("") || "·";
      slot.title = `왼쪽에서 ${index + 1}번째 차로${lane.validForRoute ? " · 경로 유효" : ""}`;
      laneList.appendChild(slot);
    }
    const missing = document.getElementById("lane-data-missing");
    missing.hidden = lanes.length > 0;
    missing.textContent = lanes.length > 0 ? "" : "차로별 안내 정보 없음";
    panel.hidden = false;
  }

  function indicationArrow(indication) {
    return ({ "uturn": "↶", "sharp right": "↱", "right": "↗", "slight right": "↗", "straight": "↑", "slight left": "↖", "left": "↖", "sharp left": "↰" })[indication] || "·";
  }

  // Kakao has no public arrow visibility/size option. Its current WebView renderer pairs
  // private _ar_N_* arrow nodes with _at_N_* compass labels; keep only the route-heading sector.
  function inspectNativeArrowLayer() {
    const applyDirectionPolicy = () => {
      const arrows = [...container.querySelectorAll('[id^="_ar_"]')];
      if (pendingConfig && pendingConfig.thumbnail) {
        container.querySelectorAll('[id^="_ar_"], [id^="_at_"]').forEach((element) => element.style.setProperty("display", "none", "important"));
        return;
      }
      const expectedDirection = compassDirection(Number(pendingConfig && pendingConfig.heading) || 0);
      const diagnostics = [];
      for (const arrow of arrows) {
        const index = arrow.id.match(/^_ar_(\d+)_/)?.[1];
        const label = index ? container.querySelector(`[id^="_at_${index}_"]`) : null;
        const labelText = (label && label.innerText || "").trim();
        const keep = labelText === expectedDirection;
        arrow.style.setProperty("display", keep ? "" : "none", "important");
        if (label) label.style.setProperty("display", keep ? "" : "none", "important");
        diagnostics.push(`${arrow.id}:${labelText || "?"}:${keep ? "keep" : "hide"}`);
      }
      if (diagnostics.length) send(`arrow-policy:${expectedDirection}:${diagnostics.join(",")}`);
    };
    applyDirectionPolicy();
    nativeArrowObserver?.disconnect();
    nativeArrowObserver = new MutationObserver(applyDirectionPolicy);
    nativeArrowObserver.observe(container, { childList: true, subtree: true });
    scaleNativeArrowLayer();
    nativeArrowScaleObserver?.disconnect();
    nativeArrowScaleObserver = new MutationObserver(scaleNativeArrowLayer);
    nativeArrowScaleObserver.observe(container, { childList: true, subtree: true, attributes: true, attributeFilter: ["style"] });
  }

  // Kakao has no public size option for its ground arrows. In the debug WebView, its current
  // renderer exposes them as private `_ar_*` DOM nodes with perspective matrix transforms.
  // Append a local scale after Kakao's matrix so the arrow gets smaller without changing the
  // panorama or replacing Kakao's movement hit target. This selector is SDK-version fragile.
  function scaleNativeArrowLayer() {
    const arrows = [...container.querySelectorAll('[id^="_ar_"]')];
    for (const arrow of arrows) {
      if (!(pendingConfig && pendingConfig.thumbnail)) {
        if (CSS.supports("scale", "0.65")) {
          if (arrow.style.scale !== "0.65") arrow.style.scale = "0.65";
        } else if (arrow.style.zoom !== "0.65") {
          arrow.style.zoom = "0.65";
        }
      }
      if (arrow.style.transformOrigin !== "50% 50%") arrow.style.transformOrigin = "50% 50%";
    }
    if (arrows.length > 0) {
      if (!(pendingConfig && pendingConfig.thumbnail)) status.textContent = "도로 위 화살표가 경로 진행 방향을 나타냅니다.";
      if (arrows.length !== lastScaledArrowCount) {
        lastScaledArrowCount = arrows.length;
        send(`native-arrow-scaled:${arrows.length}`);
      }
    }
  }

  function compassDirection(degrees) {
    const directions = ["북", "북동", "동", "남동", "남", "남서", "서", "북서"];
    return directions[Math.round((((degrees % 360) + 360) % 360) / 45) % 8];
  }

  function renderJunctionMarks(config) {
    if (junctionOverlayAttempted) return;
    junctionOverlayAttempted = true;
    const record = config && config.junctionRecord;
    const eligible = config && config.developerFixture === true && config.routeVerified === true &&
      config.photoMatchVerified === true && config.overlayAllowed === true && record &&
      record.manuallyVerified === true && record.roadViewCoverageVerified === true &&
      Array.isArray(record.keepPath) && record.keepPath.length === 3 &&
      Array.isArray(record.trapPath) && record.trapPath.length === 3;
    if (!eligible) {
      send("overlay-suppressed:junction-demo-gate");
      return;
    }
    const allPoints = [record.caution, ...record.keepPath, ...record.trapPath];
    if (allPoints.some((point) => !point || !Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) ||
        point.latitude < -90 || point.latitude > 90 || point.longitude < -180 || point.longitude > 180)) {
      send("overlay-suppressed:invalid-junction-coordinate");
      return;
    }
    const heading = Number(config.heading);
    if (!Number.isFinite(heading)) {
      send("overlay-suppressed:missing-heading");
      return;
    }
    const addMark = (point, kind, glyph, bearing) => {
      const content = document.createElement("span");
      content.className = `firstlook-junction-mark ${kind}`;
      content.textContent = glyph;
      content.setAttribute("aria-hidden", "true");
      if (bearing !== null) content.style.transform = `rotate(${bearing - heading - 90}deg)`;
      const overlay = new window.kakao.maps.CustomOverlay({
        position: new window.kakao.maps.LatLng(point.latitude, point.longitude),
        content,
        xAnchor: 0.5,
        yAnchor: 0.5,
        clickable: false,
      });
      overlay.setAltitude(0);
      overlay.setRange(300);
      overlay.setMap(roadview);
      routeOverlays.push(overlay);
    };
    const pathBearing = (path, index) => {
      const start = path[index];
      const end = path[Math.min(index + 1, path.length - 1)];
      if (index >= path.length - 1) {
        const previous = path[index - 1];
        if (!previous) return Number(record.panDegrees);
        return directionBearing(previous, start);
      }
      return directionBearing(start, end);
    };
    const directionBearing = (start, end) => {
      const lat1 = start.latitude * Math.PI / 180;
      const lat2 = end.latitude * Math.PI / 180;
      const dLon = (end.longitude - start.longitude) * Math.PI / 180;
      const y = Math.sin(dLon) * Math.cos(lat2);
      const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
      return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    };
    addMark(record.caution, "caution", "!", null);
    record.keepPath.forEach((point, index) => addMark(point, "keep", "➤", pathBearing(record.keepPath, index)));
    record.trapPath.forEach((point, index) => addMark(point, "trap", "➤", pathBearing(record.trapPath, index)));
    junctionOverlayRendered = true;
    document.getElementById("legend").hidden = false;
    document.getElementById("attribution").textContent = "Kakao Maps · OSM © OpenStreetMap contributors · ODbL 1.0 · 개발 데이터";
    status.textContent = "OSM 교차로 경로 매칭 완료 · 표식 7개 · Kakao 기본 이동 화살표는 유지합니다.";
    send("junction-overlay-ready:7");
  }

  function renderRouteChevrons(config) {
    if (overlayAttempted) return;
    const points = config && config.routePoints;
    const eligible = config && config.nativeArrowLayerConfirmed === true && config.routeVerified === true && config.photoMatchVerified === true &&
      config.overlayAllowed === true && Array.isArray(points) && points.length >= 2 && points.length <= 50;
    if (!eligible) {
      overlayAttempted = true;
      const reason = !config || config.nativeArrowLayerConfirmed !== true
        ? "native-arrow-layer-not-confirmed"
        : "unverified-route-photo-match-or-provider-permission";
      send(`overlay-suppressed:${reason}`);
      return;
    }
    for (const point of points) {
      if (!point || !Number.isFinite(point.latitude) || !Number.isFinite(point.longitude) ||
          point.latitude < -90 || point.latitude > 90 || point.longitude < -180 || point.longitude > 180) {
        overlayAttempted = true;
        send("overlay-suppressed:invalid-route-coordinate");
        return;
      }
    }
    for (let index = 0; index < points.length; index += 1) {
      const point = points[index];
      const content = document.createElement("span");
      content.className = "firstlook-route-chevron";
      content.textContent = "➤";
      content.setAttribute("aria-hidden", "true");
      const bearing = Number(point.bearingDegrees);
      const cameraHeading = Number(config.heading);
      if (!Number.isFinite(bearing) || !Number.isFinite(cameraHeading)) {
        overlayAttempted = true;
        send("overlay-suppressed:missing-heading");
        return;
      }
      content.style.transform = `rotate(${bearing - cameraHeading - 90}deg)`;
      const overlay = new window.kakao.maps.CustomOverlay({
        position: new window.kakao.maps.LatLng(point.latitude, point.longitude),
        content,
        xAnchor: 0.5,
        yAnchor: 0.5,
        clickable: false,
      });
      overlay.setMap(roadview);
      routeOverlays.push(overlay);
    }
    overlayAttempted = true;
    send(`route-overlay-ready:${routeOverlays.length}`);
  }

  window.addEventListener("message", (event) => {
    if (pendingConfig || typeof event.data !== "string") return;
    let config;
    try { config = JSON.parse(event.data); } catch (_) { return; }
    if (config.type !== "initialize" || !Number.isFinite(config.latitude) || !Number.isFinite(config.longitude)) return;
    if (config.latitude < -90 || config.latitude > 90 || config.longitude < -180 || config.longitude > 180) return;
    if (config.panoId !== undefined && (!Number.isSafeInteger(config.panoId) || config.panoId <= 0)) return;
    pendingConfig = config;
    send("config-received");
    initializeRoadview(config);
  });

  window.addEventListener("resize", () => roadview && roadview.relayout());
  window.addEventListener("pagehide", () => {
    nativeArrowObserver?.disconnect();
    nativeArrowScaleObserver?.disconnect();
    routeOverlays.forEach((overlay) => overlay.setMap(null));
    routeOverlays.length = 0;
    junctionOverlayAttempted = false;
  });
  send("ready");
})();
