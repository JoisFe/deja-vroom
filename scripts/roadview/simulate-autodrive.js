// Runs web/roadview/roadview.js against a fake DOM and a fake Kakao SDK to check the auto-drive flow
// without a Kakao key or a device:  node scripts/roadview/simulate-autodrive.js
// The fake SDK fires "init" on every panorama change, the worst case the page has to survive.
const fs = require("fs");
const path = require("path");
const vm = require("vm");
let src = fs.readFileSync(process.argv[2] || path.join(__dirname, "../../web/roadview/roadview.js"), "utf8");
src = src.replace("AUTO_DRIVE_START_DELAY_MS = 1800", "AUTO_DRIVE_START_DELAY_MS = 30").replace("AUTO_DRIVE_STEP_MS = 1200", "AUTO_DRIVE_STEP_MS = 20")
  .replace("AUTO_DRIVE_ARRIVAL_HOLD_MS = 2800", "AUTO_DRIVE_ARRIVAL_HOLD_MS = 160");

const messages = [], panoCalls = [], viewpoints = [];
let fakeRoadview = null;
const stamps = [];
function el(id) {
  const listeners = {};
  return {
    id, hidden: true, textContent: "", style: { setProperty() {}, display: "" },
    classList: { add() {}, remove() {}, contains() { return false; } },
    addEventListener(type, fn) { (listeners[type] ||= []).push(fn); },
    dispatch(type) { (listeners[type] || []).forEach((fn) => fn({})); },
    querySelectorAll() { return []; }, querySelector() { return null; },
    appendChild() {}, replaceChildren() {}, setAttribute() {},
  };
}
const elements = {};
const document = {
  getElementById(id) { return (elements[id] ||= el(id)); },
  querySelector(selector) { return selector.includes("kakao-js-key") ? { content: "abcdef0123456789abcdef0123456789" } : null; },
  createElement(tag) { const e = el(tag); if (tag === "script") setTimeout(() => e.onload && e.onload(), 5); return e; },
  head: { appendChild() {} },
  body: { classList: { add() {}, remove() {}, contains() { return false; } }, appendChild() {} },
};
const kakao = { maps: {
  load(cb) { setTimeout(cb, 5); },
  LatLng: function (lat, lng) { this.lat = lat; this.lng = lng; this.getLat = () => lat; this.getLng = () => lng; },
  RoadviewClient: function () { this.getNearestPanoId = (pos, radius, cb) => setTimeout(() => cb(Math.round(pos.lat * 1e5)), 5); },
  // Worst case: "init" fires again on every panorama change.
  Roadview: function () {
    fakeRoadview = this;
    this.getPosition = () => this._position;
    this.moveTo = (lat, lng) => { this._position = new kakao.maps.LatLng(lat, lng); (this._moved || []).forEach((fn) => fn()); };
    this.setPanoId = (id, position) => {
      panoCalls.push(id);
      this._position = position;
      setTimeout(() => { (this._moved || []).forEach((fn) => fn()); (this._init || []).forEach((fn) => fn()); }, 8);
    };
    this.setViewpoint = (v) => viewpoints.push(Math.round(v.pan));
    this.relayout = () => {};
  },
  CustomOverlay: function () { this.setRange = () => {}; this.setMap = () => {}; },
  event: { addListener(target, type, fn) {
    if (type === "init") (target._init ||= []).push(fn);
    if (type === "position_changed") (target._moved ||= []).push(fn);
  } },
} };
const windowListeners = {};
const context = {
  document, kakao, console, setTimeout, clearTimeout, URLSearchParams,
  MutationObserver: class { observe() {} disconnect() {} },
  DejaVroomBridge: { postMessage: (m) => { messages.push(m); stamps.push([m, Date.now()]); } },
  location: { search: "" },
  addEventListener(type, fn) { windowListeners[type] = fn; },
};
context.window = context;
vm.runInNewContext(src, context);

const lead = 150;
const drive = Array.from({ length: 9 }, (_, i) => ({
  latitude: 37.5 + i * 0.0002, longitude: 127.0, headingDegrees: i < 7 ? 0 : 90, remainingMeters: lead - i * 25,
}));
const config = {
  type: "initialize", latitude: 37.5014, longitude: 127.0, heading: 0, routeId: "r", routeSource: "OSRM/OSM",
  candidates: [{ latitude: 37.5014, longitude: 127.0, viewLatitude: 37.5, viewLongitude: 127.0, leadMeters: lead, headingDegrees: 0,
    name: "테스트 갈림길", score: 0.5, displayIndex: 0, evidence: ["이유"], routeLanes: [], drive }],
};
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const progress = () => messages.filter((m) => m.startsWith("drive-progress:")).map((m) => Number(m.split(":")[1]));
const assert = (ok, what) => { if (!ok) { console.log("FAIL:", what); process.exitCode = 1; } else console.log("ok:", what); };

(async () => {
  await wait(40);
  assert(messages.includes("ready") && messages.includes("sdk-ready"), "page reaches ready and sdk-ready");
  windowListeners.message({ data: JSON.stringify(config) });
  await wait(900);
  const button = elements.autodrive;
  assert(messages.includes("shown") && messages.includes("route-candidate-selected:0"), "panorama shown for the candidate");
  assert(messages.filter((m) => m === "drive-finished").length === 1, "drive finishes exactly once (init re-fire does not restart it)");
  // First the real distance from where the first panorama stands, then one report per frame of the drive.
  assert(Math.abs(progress()[0] - lead) <= 10, "the first panorama reports its own distance to the junction: " + progress()[0]);
  assert(JSON.stringify(progress().slice(1)) === JSON.stringify(drive.slice(1).map((p) => p.remainingMeters)), "progress counts down through every frame: " + progress().slice(1));
  assert(panoCalls.length === drive.length, "one panorama per frame (first by the page, the rest by the drive): " + panoCalls.length);
  assert(viewpoints[viewpoints.length - 1] === 90, "camera ends facing the way the route leaves the junction");
  assert(button.hidden === false && button.textContent === "분기점까지 다시 보기", "replay button is offered: " + button.textContent);
  // The drive lingers on the junction frame and nowhere else.
  const frames = stamps.filter(([m]) => m.startsWith("drive-progress:")).slice(1);
  // Time spent on a frame before the next one; NaN (a failed check, not a crash) when either is missing.
  const gapAfter = (value) => {
    const i = frames.findIndex(([m]) => m === `drive-progress:${value}`);
    return i >= 0 && frames[i + 1] ? frames[i + 1][1] - frames[i][1] : NaN;
  };
  assert(gapAfter(0) >= 140 && gapAfter(25) < 100 && gapAfter(-25) < 100, `the drive holds on the junction frame: ${gapAfter(25)}ms, ${gapAfter(0)}ms, ${gapAfter(-25)}ms`);

  messages.length = 0; panoCalls.length = 0;
  button.dispatch("click");
  await wait(20);
  assert(button.textContent === "멈춤", "button shows 멈춤 while playing");
  await wait(700);
  assert(progress()[0] === lead && progress().length === drive.length, "replay starts again from the viewpoint: " + progress());

  messages.length = 0;
  button.dispatch("click");
  await wait(60);
  elements.roadview.dispatch("pointerdown");
  await wait(30); // let a panorama that was already loading settle
  const hops = panoCalls.length;
  await wait(300);
  assert(panoCalls.length === hops && !messages.includes("drive-finished"), "touching the panorama stops the drive");
  assert(button.textContent === "분기점까지 다시 보기", "button offers replay after a stop");

  // The driver steps along the road by hand: the banner distance follows the panorama's own position.
  messages.length = 0;
  fakeRoadview.moveTo(37.5005, 127.0); // about 100 m before the junction at 37.5014
  assert(Math.abs(progress()[0] - 100) <= 3, "a manual step reports the distance left: " + progress());
  messages.length = 0;
  fakeRoadview.moveTo(37.5016, 127.0); // about 22 m past it
  assert(progress()[0] < 0 && progress()[0] > -30, "past the junction the distance is reported as passed: " + progress());
})();
