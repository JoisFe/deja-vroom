// Runs web/roadview/places.js against a fake Kakao SDK to check what the page tells the app,
// without a Kakao key or a device:  node scripts/roadview/simulate-places.js
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const src = fs.readFileSync(path.join(__dirname, "../../web/roadview/places.js"), "utf8");

const assert = (ok, what) => { if (!ok) { console.log("FAIL:", what); process.exitCode = 1; } else console.log("ok:", what); };
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** One page with a fake SDK. [answers] says what the fake Kakao returns for a query. */
function page({ key = "abcdef0123456789abcdef0123456789", services = true, answers = {} } = {}) {
  const messages = [];
  const status = { OK: "OK", ZERO_RESULT: "ZERO_RESULT", ERROR: "ERROR" };
  const answer = (kind, query, done) => setTimeout(() => {
    const entry = (answers[query] || {})[kind];
    if (entry === "error") done([], status.ERROR);
    else if (!entry || entry.length === 0) done([], status.ZERO_RESULT);
    else done(entry, status.OK);
  }, 5);
  const kakao = { maps: {
    load(cb) { setTimeout(cb, 5); },
    services: services ? {
      Status: status,
      Places: function () { this.keywordSearch = (query, done) => answer("places", query, done); },
      Geocoder: function () { this.addressSearch = (query, done) => answer("addresses", query, done); },
    } : undefined,
  } };
  const listeners = {};
  const context = {
    console, setTimeout, clearTimeout, kakao,
    document: {
      querySelector: () => ({ content: key }),
      createElement: () => { const script = {}; setTimeout(() => script.onload && script.onload(), 5); return script; },
      head: { appendChild() {} },
    },
    DejaVroomBridge: { postMessage: (m) => messages.push(m) },
    addEventListener(type, fn) { listeners[type] = fn; },
  };
  context.window = context;
  vm.runInNewContext(src, context);
  const ask = (request) => listeners.message && listeners.message({ data: typeof request === "string" ? request : JSON.stringify(request) });
  const answersFor = (id) => messages.filter((m) => m.startsWith("{")).map((m) => JSON.parse(m)).filter((m) => m.id === id);
  return { messages, ask, answersFor };
}

(async () => {
  const kakaoAnswers = {
    "강남역": { places: [
      { place_name: "강남역 2호선", road_address_name: "서울 강남구 강남대로 지하 396", address_name: "서울 강남구 역삼동 858", x: "127.02762", y: "37.49794" },
      { place_name: "강남역 신분당선", road_address_name: "", address_name: "서울 강남구 역삼동 804", x: "127.0284", y: "37.4969" },
      { place_name: "강남역 2호선", road_address_name: "서울 강남구 강남대로 지하 396", address_name: "서울 강남구 역삼동 858", x: "127.02762", y: "37.49794" },
      { place_name: "좌표 없는 곳", road_address_name: "어딘가", x: "", y: "abc" },
    ] },
    "수원시 영통구": {
      addresses: [{ address_name: "경기 수원시 영통구", x: "127.0465", y: "37.2596", road_address: null }],
      places: [{ place_name: "영통구청", road_address_name: "경기 수원시 영통구 효원로 407", x: "127.0469", y: "37.2595" }],
    },
    "주소만 됨": { addresses: [{ address_name: "서울 중구 세종대로 110", x: "126.9784", y: "37.5666", road_address: { address_name: "서울 중구 세종대로 110" } }], places: "error" },
    "둘 다 오류": { addresses: "error", places: "error" },
  };

  const ready = page({ answers: kakaoAnswers });
  await wait(40);
  assert(ready.messages.length === 1 && ready.messages[0] === "ready", "the page says ready once the Kakao services are up");

  ready.ask({ type: "search", id: 1, query: "  강남역 " });
  ready.ask({ type: "search", id: 2, query: "수원시 영통구" });
  ready.ask({ type: "search", id: 3, query: "없는 장소" });
  ready.ask({ type: "search", id: 4, query: "주소만 됨" });
  ready.ask({ type: "search", id: 5, query: "둘 다 오류" });
  ready.ask({ type: "search", id: 6, query: "   " });
  ready.ask("not json");
  ready.ask({ type: "other", id: 7, query: "강남역" });
  ready.ask({ type: "search", id: "8", query: "강남역" });
  await wait(60);

  const station = ready.answersFor(1)[0];
  assert(station.type === "places" && station.items.length === 2, "a place name gives its places, without repeats or entries that have no position: " + station.items.map((i) => i.name));
  assert(station.items[0].name === "강남역 2호선" && station.items[0].address === "서울 강남구 강남대로 지하 396" &&
    station.items[0].latitude === 37.49794 && station.items[0].longitude === 127.02762, "each place carries a name, an address and numbers for its position");
  assert(station.items[1].address === "서울 강남구 역삼동 804", "a place without a road address falls back to its lot address");

  const district = ready.answersFor(2)[0];
  assert(district.items.map((i) => i.name).join() === "경기 수원시 영통구,영통구청", "a typed district comes first, then the places named after it");
  assert(ready.answersFor(3)[0].type === "places" && ready.answersFor(3)[0].items.length === 0, "nothing found is an empty answer, not an error");
  assert(ready.answersFor(4)[0].items.length === 1 && ready.answersFor(4)[0].items[0].address === "", "one lookup failing still gives what the other found");
  assert(ready.answersFor(5)[0].type === "places-error", "both lookups failing is reported as an error");
  assert(ready.answersFor(6)[0].type === "places-error", "an empty question is refused");
  assert(ready.answersFor(7).length === 0 && ready.messages.filter((m) => m.includes('"id":"8"')).length === 0, "anything that is not a question is ignored");

  const noKey = page({ key: "__KAKAO_JS_KEY__" });
  await wait(30);
  assert(noKey.messages.join() === "fail:config", "without a key the page says so and loads nothing");

  const noServices = page({ services: false });
  await wait(40);
  assert(noServices.messages.join() === "fail:sdk-load", "an SDK without the services library is a load failure");
  noServices.ask({ type: "search", id: 1, query: "강남역" });
  await wait(20);
  assert(noServices.answersFor(1)[0].type === "places-error", "a page that is not ready answers a question with an error");
})();
