// Place search for the app, answered by Kakao. The app loads this page in a WebView that is never shown,
// posts {type:"search", id, query} and gets {type:"places", id, items} back over the same bridge the
// road view page uses. The Kakao JavaScript key lives here, where it only works on the registered domain.
(() => {
  // The key is injected into <meta name="kakao-js-key"> at deploy time (GitHub Actions) or by scripts/roadview/serve.py.
  const appKey = ((document.querySelector('meta[name="kakao-js-key"]') || {}).content || "").trim();
  const MAX_PLACES = 8;
  const MAX_ADDRESSES = 3;
  const MAX_QUERY_LENGTH = 80;
  const SDK_TIMEOUT_MS = 10000;
  let places = null;
  let geocoder = null;

  const send = (message) => {
    if (window.DejaVroomBridge && typeof window.DejaVroomBridge.postMessage === "function") {
      window.DejaVroomBridge.postMessage(message);
    }
  };

  if (!appKey || appKey.includes("__KAKAO_")) {
    send("fail:config");
    return;
  }

  const sdk = document.createElement("script");
  sdk.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&libraries=services&autoload=false`;
  sdk.async = true;
  const sdkTimer = window.setTimeout(() => send("fail:sdk-load"), SDK_TIMEOUT_MS);
  sdk.onerror = () => {
    window.clearTimeout(sdkTimer);
    send("fail:sdk-load");
  };
  sdk.onload = () => {
    if (!window.kakao || !window.kakao.maps) {
      window.clearTimeout(sdkTimer);
      send("fail:sdk-load");
      return;
    }
    window.kakao.maps.load(() => {
      window.clearTimeout(sdkTimer);
      const services = window.kakao.maps.services;
      if (!services) {
        send("fail:sdk-load");
        return;
      }
      places = new services.Places();
      geocoder = new services.Geocoder();
      send("ready");
    });
  };
  document.head.appendChild(sdk);

  // Each lookup settles with a list: nothing found is an empty list, only a service error rejects.
  const lookup = (run) => new Promise((resolve, reject) => {
    const status = window.kakao.maps.services.Status;
    run((result, outcome) => {
      if (outcome === status.OK) resolve(Array.isArray(result) ? result : []);
      else if (outcome === status.ZERO_RESULT) resolve([]);
      else reject(new Error("kakao-search"));
    });
  });

  // parseFloat, not Number: an empty coordinate must come out as no number, not as zero.
  const item = (name, address, latitude, longitude) => ({ name, address, latitude: Number.parseFloat(latitude), longitude: Number.parseFloat(longitude) });

  // A typed name can be a place ("강남역") or an address or district ("수원시 영통구"). Both are asked;
  // an address that matches comes first because it is what was typed, then the places by relevance.
  function search(query) {
    const byName = lookup((done) => places.keywordSearch(query, done, { size: MAX_PLACES }));
    const byAddress = lookup((done) => geocoder.addressSearch(query, done, { size: MAX_ADDRESSES }));
    return Promise.allSettled([byAddress, byName]).then(([addresses, named]) => {
      if (addresses.status === "rejected" && named.status === "rejected") throw new Error("kakao-search");
      const found = [
        ...(addresses.status === "fulfilled" ? addresses.value : []).map((entry) => item(
          entry.address_name,
          entry.road_address && entry.road_address.address_name !== entry.address_name ? entry.road_address.address_name : "",
          entry.y, entry.x,
        )),
        ...(named.status === "fulfilled" ? named.value : []).map((entry) => item(
          entry.place_name, entry.road_address_name || entry.address_name || "", entry.y, entry.x,
        )),
      ];
      const seen = new Set();
      return found.filter((entry) => {
        const usable = typeof entry.name === "string" && entry.name.trim() !== "" &&
          Number.isFinite(entry.latitude) && Number.isFinite(entry.longitude);
        const key = `${entry.name}|${entry.address}`;
        if (!usable || seen.has(key)) return false;
        seen.add(key);
        return true;
      }).slice(0, MAX_PLACES);
    });
  }

  window.addEventListener("message", (event) => {
    if (typeof event.data !== "string") return;
    let request;
    try { request = JSON.parse(event.data); } catch (_) { return; }
    if (!request || request.type !== "search" || !Number.isSafeInteger(request.id) || typeof request.query !== "string") return;
    const query = request.query.trim().slice(0, MAX_QUERY_LENGTH);
    if (!places || query === "") {
      send(JSON.stringify({ type: "places-error", id: request.id }));
      return;
    }
    search(query).then(
      (items) => send(JSON.stringify({ type: "places", id: request.id, items })),
      () => send(JSON.stringify({ type: "places-error", id: request.id })),
    );
  });
})();
