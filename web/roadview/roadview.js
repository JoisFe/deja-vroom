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
  let arrowTidyScheduled = false;
  let lastArrowPolicy = "";
  let lastScaledArrowCount = 0;
  let overlayAttempted = false;
  let junctionOverlayAttempted = false;
  let junctionOverlayRendered = false;
  let candidateOverlayRendered = false;
  const routeOverlays = [];

  // Auto drive: plays the approach like a slow drive, from the viewpoint up to the junction, and stops there.
  const AUTO_DRIVE_START_DELAY_MS = 1800;
  const AUTO_DRIVE_STEP_MS = 1200;
  // A frame with no photo of this road is passed over at once instead of being waited on.
  const AUTO_DRIVE_SKIP_MS = 150;
  const AUTO_DRIVE_LOOKUP_TIMEOUT_MS = 2500;
  // A photo further than this from where the car should be belongs to some other road (the opposite
  // carriageway, a path under a bridge). The drive leaves the frame out rather than show it.
  const AUTO_DRIVE_RADIUS_METERS = 12;
  // If the new photo does not report in by then, the camera turns anyway.
  const AUTO_DRIVE_PHOTO_WAIT_MS = 700;
  const AUTO_DRIVE_MAX_POSES = 40;
  // A camera turn takes this long at most; a wide turn at a junction still ends well inside one frame.
  const CAMERA_TURN_MAX_MS = 900;
  // Further than this from the drive, the photo is on a road the drive knows nothing about.
  const OFF_DRIVE_METERS = 60;
  const driveButton = document.getElementById("autodrive");
  let autoDrive = null;
  let shownOnce = false;
  let lastReportedMeters = null;
  let cameraTurn = 0;
  let afterPhoto = null;

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

  window.addEventListener("securitypolicyviolation", (event) => {
    send(`csp-blocked:${event.violatedDirective}:${event.blockedURI}`);
    console.error(`roadview csp-blocked ${event.violatedDirective} ${event.blockedURI}`);
  });

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
    // kakao.maps.load fetches more scripts from t1.daumcdn.net; if any is blocked it never calls back.
    const mapsLoadTimer = window.setTimeout(() => fail("sdk-load", "Kakao 지도 모듈을 불러오지 못했습니다. 네트워크·보안 정책(CSP)을 확인하세요."), 10000);
    window.kakao.maps.load(() => {
      window.clearTimeout(mapsLoadTimer);
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
        // Every new photo, however it was reached, tells the app where it stands and lets a waiting
        // camera turn begin.
        window.kakao.maps.event.addListener(roadview, "position_changed", () => {
          reportRemainingDistance();
          const turn = afterPhoto;
          afterPhoto = null;
          if (turn) turn();
        });
        window.kakao.maps.event.addListener(roadview, "init", () => {
          window.clearTimeout(timer);
          // Kakao fires "init" again for each new panorama. Only the first one sets the page up; a later
          // one means the photo is up, so a waiting camera turn begins, or the camera is put back on the route.
          if (shownOnce) {
            const turn = afterPhoto;
            afterPhoto = null;
            if (turn) turn();
            else if (!cameraTurn) setPan(Number(config.heading) || 0);
            return;
          }
          shownOnce = true;
          roadview.relayout();
          setPan(Number(config.heading) || 0);
          inspectNativeArrowLayer();
          renderSelectedCandidate(config);
          status.textContent = config.selectedCandidate ? "! 표시가 분기점입니다" : "Kakao Roadview";
          if (config.thumbnail) status.style.display = "none";
          send("shown");
          reportRemainingDistance();
          startAutoDrive(config, false);
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
    // The panorama starts at the approach viewpoint (before the junction) when the app sends one;
    // candidate.latitude/longitude stay the junction itself, where the marker goes.
    const hasViewpoint = Number.isFinite(candidate.viewLatitude) && Number.isFinite(candidate.viewLongitude);
    const position = new window.kakao.maps.LatLng(
      hasViewpoint ? candidate.viewLatitude : candidate.latitude,
      hasViewpoint ? candidate.viewLongitude : candidate.longitude
    );
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

  function drivePoses(config) {
    const candidate = config && config.selectedCandidate;
    const poses = candidate && Array.isArray(candidate.drive) ? candidate.drive : [];
    return poses.filter((pose) => pose && Number.isFinite(pose.latitude) && Number.isFinite(pose.longitude) &&
      pose.latitude >= -90 && pose.latitude <= 90 && pose.longitude >= -180 && pose.longitude <= 180).slice(0, AUTO_DRIVE_MAX_POSES);
  }

  // The first run starts on the pose the panorama already shows; a replay goes back to the first pose.
  function startAutoDrive(config, fromStart) {
    const poses = drivePoses(config);
    if (!roadview || !driveButton || !config || config.thumbnail || poses.length < 2) return;
    stopAutoDrive();
    autoDrive = {
      config, poses, index: fromStart ? -1 : 0, panoId: fromStart ? null : (config.panoId || null), playing: true, timer: 0,
      // Where the camera was last turned to; a replay starts from wherever the driver left it.
      heading: fromStart ? null : Number(config.heading) || 0,
    };
    driveButton.hidden = false;
    updateDriveButton();
    autoDrive.timer = window.setTimeout(stepAutoDrive, fromStart ? 0 : AUTO_DRIVE_START_DELAY_MS);
  }

  function stopAutoDrive() {
    if (!autoDrive) return;
    window.clearTimeout(autoDrive.timer);
    autoDrive.playing = false;
    afterPhoto = null;
    stopCameraTurn();
    updateDriveButton();
  }

  function updateDriveButton() {
    if (driveButton) driveButton.textContent = autoDrive && autoDrive.playing ? "멈춤" : "분기점까지 다시 보기";
  }

  // One frame of the drive: find the photo nearest to where the car is on the route, show it, then turn
  // the camera the way the route goes. The app is told nothing here; it hears from the photo itself.
  function stepAutoDrive() {
    const drive = autoDrive;
    if (!drive || !drive.playing) return;
    drive.index += 1;
    if (drive.index >= drive.poses.length) {
      drive.playing = false;
      updateDriveButton();
      send("drive-finished");
      return;
    }
    const pose = drive.poses[drive.index];
    const active = () => autoDrive === drive && drive.playing;
    const next = (delay) => {
      if (active()) drive.timer = window.setTimeout(stepAutoDrive, delay);
    };
    let answered = false;
    // A lookup that never answers must not freeze the drive; the frame is passed over instead.
    const guard = window.setTimeout(() => {
      if (answered) return;
      answered = true;
      next(AUTO_DRIVE_SKIP_MS);
    }, AUTO_DRIVE_LOOKUP_TIMEOUT_MS);
    try {
      const position = new window.kakao.maps.LatLng(pose.latitude, pose.longitude);
      new window.kakao.maps.RoadviewClient().getNearestPanoId(position, AUTO_DRIVE_RADIUS_METERS, (panoId) => {
        if (answered) return;
        answered = true;
        window.clearTimeout(guard);
        if (!active()) return;
        if (!panoId) {
          next(AUTO_DRIVE_SKIP_MS);
          return;
        }
        try {
          const heading = Number(pose.headingDegrees) || 0;
          const before = drive.heading;
          drive.heading = heading;
          // Remembered so that an "init" for a later panorama faces the same way.
          drive.config.heading = heading;
          // Whatever the new photo did to the camera, the turn starts from where the drive was looking.
          const turn = () => {
            if (active()) turnCamera(heading, before);
          };
          if (panoId === drive.panoId) {
            turn();
          } else {
            drive.panoId = panoId;
            // The camera turns once the new photo is up; turning first would swing the old one round.
            afterPhoto = turn;
            window.setTimeout(() => {
              if (afterPhoto !== turn) return;
              afterPhoto = null;
              turn();
            }, AUTO_DRIVE_PHOTO_WAIT_MS);
            roadview.setPanoId(panoId, position);
          }
        } catch (failure) {
          console.error(`roadview auto-drive failed ${failure && failure.message || failure}`);
          stopAutoDrive();
          return;
        }
        next(AUTO_DRIVE_STEP_MS);
      });
    } catch (failure) {
      window.clearTimeout(guard);
      console.error(`roadview auto-drive failed ${failure && failure.message || failure}`);
      stopAutoDrive();
    }
  }

  function setPan(pan) {
    try {
      roadview.setViewpoint({ pan: ((pan % 360) + 360) % 360, tilt: 0, zoom: 0 });
    } catch (_) { /* the next frame faces the route again */ }
  }

  function stopCameraTurn() {
    window.cancelAnimationFrame(cameraTurn);
    cameraTurn = 0;
  }

  // Turns the camera from [from] to [pan] the short way round. A snap reads as a jump cut, so anything
  // more than a nudge is eased over a moment, longer for a wider turn. Without [from] it just faces [pan].
  function turnCamera(pan, from) {
    stopCameraTurn();
    const delta = Number.isFinite(from) ? ((pan - from + 540) % 360) - 180 : 0;
    if (Math.abs(delta) < 2) {
      setPan(pan);
      return;
    }
    setPan(from);
    const duration = Math.min(CAMERA_TURN_MAX_MS, 250 + Math.abs(delta) * 6);
    const started = performance.now();
    const frame = (now) => {
      const progress = Math.min(1, Math.max(0, (now - started) / duration));
      setPan(from + delta * progress * progress * (3 - 2 * progress));
      cameraTurn = progress < 1 ? window.requestAnimationFrame(frame) : 0;
    };
    cameraTurn = window.requestAnimationFrame(frame);
  }

  function flatDistanceMeters(a, b) {
    const meanLatitude = (a.latitude + b.latitude) / 2 * Math.PI / 180;
    const dx = (b.longitude - a.longitude) * Math.PI / 180 * 6371000 * Math.cos(meanLatitude);
    const dy = (b.latitude - a.latitude) * Math.PI / 180 * 6371000;
    return Math.hypot(dx, dy);
  }

  // How far the junction still is from [here], measured along the drive; negative once past it.
  // Beyond either end of the drive the distance to that end is added on. Away from the drive altogether
  // (another road the driver stepped onto) only the straight distance is known.
  function remainingFrom(here, poses, junction) {
    const straight = flatDistanceMeters(here, junction);
    if (poses.length < 2) return straight;
    let nearest = null;
    for (let i = 0; i + 1 < poses.length; i += 1) {
      const from = poses[i];
      const to = poses[i + 1];
      const span = flatDistanceMeters(from, to);
      const toFrom = flatDistanceMeters(from, here);
      const toTo = flatDistanceMeters(to, here);
      const along = span <= 0 ? 0 : Math.min(span, Math.max(0, (toFrom * toFrom - toTo * toTo + span * span) / (2 * span)));
      const share = span <= 0 ? 0 : along / span;
      const foot = {
        latitude: from.latitude + (to.latitude - from.latitude) * share,
        longitude: from.longitude + (to.longitude - from.longitude) * share,
      };
      const off = flatDistanceMeters(foot, here);
      if (!nearest || off < nearest.off) {
        nearest = {
          off,
          remaining: Number(from.remainingMeters) + (Number(to.remainingMeters) - Number(from.remainingMeters)) * share,
          beforeStart: i === 0 && share <= 0,
          pastEnd: i + 2 === poses.length && share >= 1,
        };
      }
    }
    if (!Number.isFinite(nearest.remaining)) return straight;
    if (nearest.pastEnd) return nearest.remaining - nearest.off;
    if (nearest.beforeStart) return nearest.remaining + nearest.off;
    return nearest.off > OFF_DRIVE_METERS ? straight : nearest.remaining;
  }

  // Tells the app how far the junction is from where the photo on screen actually stands. The banner
  // follows the picture: it changes when the picture does, and never to a place the picture is not.
  function reportRemainingDistance() {
    const config = pendingConfig;
    const candidate = config && config.selectedCandidate;
    if (!roadview || !candidate || config.thumbnail) return;
    try {
      const position = roadview.getPosition();
      if (!position || typeof position.getLat !== "function") return;
      const here = { latitude: position.getLat(), longitude: position.getLng() };
      if (!Number.isFinite(here.latitude) || !Number.isFinite(here.longitude)) return;
      const remaining = Math.round(remainingFrom(here, drivePoses(config), candidate));
      if (remaining === lastReportedMeters || remaining < -500 || remaining > 1000) return;
      lastReportedMeters = remaining;
      send(`drive-progress:${remaining}`);
    } catch (failure) {
      console.error(`roadview distance report failed ${failure && failure.message || failure}`);
    }
  }

  // Touching the panorama hands control back to the driver.
  container.addEventListener("pointerdown", () => {
    if (autoDrive && autoDrive.playing) stopAutoDrive();
    stopCameraTurn();
  }, { passive: true });
  if (driveButton) {
    driveButton.addEventListener("click", () => {
      if (autoDrive && autoDrive.playing) stopAutoDrive();
      else if (pendingConfig) startAutoDrive(pendingConfig, true);
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
    // Keep the junction marker visible from the approach viewpoint, which can be a few hundred meters back.
    const leadMeters = Math.max(0, Math.round(Number(candidate.leadMeters) || 0));
    overlay.setRange(Math.max(120, leadMeters + 100));
    overlay.setMap(roadview);
    routeOverlays.push(overlay);
    // The app shows the maneuver, the distance and the sign in its own banner over this page.
    document.getElementById("attribution").textContent = "Kakao Maps · © OpenStreetMap contributors";
    send(`route-candidate-selected:${config.selectedCandidateIndex}`);
  }

  function renderLaneGuidance(candidate) {
    const panel = document.getElementById("segment-context");
    const lanes = Array.isArray(candidate.routeLanes) ? candidate.routeLanes : [];
    // The panel exists for the lane diagram; without lane data there is nothing a driver needs from it.
    if (lanes.length === 0) {
      panel.hidden = true;
      return;
    }
    const validLanes = lanes.filter((lane) => lane.validForRoute === true);
    document.getElementById("lane-title").textContent = "차로 안내";
    document.getElementById("lane-subtitle").textContent = validLanes.length ? "초록색 차로로 가면 됩니다." : "";
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
    document.getElementById("lane-data-missing").hidden = true;
    panel.hidden = false;
  }

  function indicationArrow(indication) {
    return ({ "uturn": "↶", "sharp right": "↱", "right": "↗", "slight right": "↗", "straight": "↑", "slight left": "↖", "left": "↖", "sharp left": "↰" })[indication] || "·";
  }

  // Kakao has no public arrow visibility/size option. Its current WebView renderer pairs
  // private _ar_N_* arrow nodes with _at_N_* compass labels; keep only the route-heading sector.
  // Kakao rebuilds those nodes constantly while a panorama loads, so the tidy-up is done once per
  // frame at most and touches a node only when it has to.
  function inspectNativeArrowLayer() {
    tidyNativeArrows();
    nativeArrowObserver?.disconnect();
    nativeArrowObserver = new MutationObserver(scheduleArrowTidy);
    nativeArrowObserver.observe(container, { childList: true, subtree: true, attributes: true, attributeFilter: ["style"] });
  }

  function scheduleArrowTidy() {
    if (arrowTidyScheduled) return;
    arrowTidyScheduled = true;
    window.requestAnimationFrame(() => {
      arrowTidyScheduled = false;
      tidyNativeArrows();
    });
  }

  function setStyle(element, property, value) {
    if (element.style.getPropertyValue(property) !== value) element.style.setProperty(property, value, value ? "important" : "");
  }

  function tidyNativeArrows() {
    const thumbnail = Boolean(pendingConfig && pendingConfig.thumbnail);
    if (thumbnail) {
      container.querySelectorAll('[id^="_ar_"], [id^="_at_"]').forEach((element) => setStyle(element, "display", "none"));
      return;
    }
    const arrows = [...container.querySelectorAll('[id^="_ar_"]')];
    const expectedDirection = compassDirection(Number(pendingConfig && pendingConfig.heading) || 0);
    const diagnostics = [];
    for (const arrow of arrows) {
      const index = arrow.id.match(/^_ar_(\d+)_/)?.[1];
      const label = index ? container.querySelector(`[id^="_at_${index}_"]`) : null;
      const labelText = (label && label.innerText || "").trim();
      const keep = labelText === expectedDirection;
      setStyle(arrow, "display", keep ? "" : "none");
      if (label) setStyle(label, "display", keep ? "" : "none");
      // Kakao has no size option either. A local scale after its own perspective matrix makes the
      // arrow smaller without moving it or replacing Kakao's hit target. This is SDK-version fragile.
      if (CSS.supports("scale", "0.65")) {
        if (arrow.style.scale !== "0.65") arrow.style.scale = "0.65";
      } else if (arrow.style.zoom !== "0.65") {
        arrow.style.zoom = "0.65";
      }
      if (arrow.style.transformOrigin !== "50% 50%") arrow.style.transformOrigin = "50% 50%";
      diagnostics.push(`${arrow.id}:${labelText || "?"}:${keep ? "keep" : "hide"}`);
    }
    if (arrows.length === 0) return;
    const hint = "도로 위 화살표가 경로 진행 방향을 나타냅니다.";
    if (status.textContent !== hint) status.textContent = hint;
    const policy = `${expectedDirection}:${diagnostics.join(",")}`;
    if (policy !== lastArrowPolicy) {
      lastArrowPolicy = policy;
      send(`arrow-policy:${policy}`);
    }
    if (arrows.length !== lastScaledArrowCount) {
      lastScaledArrowCount = arrows.length;
      send(`native-arrow-scaled:${arrows.length}`);
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
    stopAutoDrive();
    routeOverlays.forEach((overlay) => overlay.setMap(null));
    routeOverlays.length = 0;
    junctionOverlayAttempted = false;
  });
  send("ready");
})();
