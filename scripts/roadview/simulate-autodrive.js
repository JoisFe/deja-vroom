// Runs web/roadview/roadview.js against a fake DOM and a fake Kakao SDK to check the auto-drive flow
// without a Kakao key or a device:  node scripts/roadview/simulate-autodrive.js
// The fake SDK fires "init" on every panorama change, the worst case the page has to survive.
const fs = require("fs");
const path = require("path");
const vm = require("vm");
let src = fs.readFileSync(process.argv[2] || path.join(__dirname, "../../web/roadview/roadview.js"), "utf8");
const faster = { "AUTO_DRIVE_START_DELAY_MS = 1800": "AUTO_DRIVE_START_DELAY_MS = 30", "AUTO_DRIVE_STEP_MS = 1200": "AUTO_DRIVE_STEP_MS = 40",
  "AUTO_DRIVE_SKIP_MS = 150": "AUTO_DRIVE_SKIP_MS = 5", "AUTO_DRIVE_PHOTO_WAIT_MS = 700": "AUTO_DRIVE_PHOTO_WAIT_MS = 60",
  "CAMERA_TURN_MAX_MS = 900": "CAMERA_TURN_MAX_MS = 150" };
for (const [from, to] of Object.entries(faster)) {
  if (!src.includes(from)) throw new Error(`roadview.js no longer has "${from}"`);
  src = src.replace(from, to);
}

const METERS_PER_DEGREE = 6371000 * Math.PI / 180;
const north = (meters) => 37.5 + meters / METERS_PER_DEGREE;
const east = (meters) => 127.0 + meters / (METERS_PER_DEGREE * Math.cos(37.5 * Math.PI / 180));

const messages = [], panoCalls = [], lookups = [], viewpoints = [];
let fakeRoadview = null;
let noPhotoNear = null; // a latitude the fake SDK has no photo for
function el(id) {
  const listeners = {};
  return {
    id, hidden: true, textContent: "", style: { setProperty() {}, getPropertyValue() { return ""; }, display: "" },
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
  RoadviewClient: function () {
    this.getNearestPanoId = (pos, radius, cb) => {
      lookups.push(radius);
      setTimeout(() => cb(noPhotoNear !== null && Math.abs(pos.lat - noPhotoNear) < 1e-7 ? null : Math.round(pos.lat * 1e7)), 5);
    };
  },
  // Worst case: "init" fires again on every panorama change, and the photo resets the camera.
  Roadview: function () {
    fakeRoadview = this;
    this._viewpoint = { pan: 0 };
    this.getPosition = () => this._position;
    this.getViewpoint = () => this._viewpoint;
    this.moveTo = (lat, lng) => { this._position = new kakao.maps.LatLng(lat, lng); (this._moved || []).forEach((fn) => fn()); };
    this.setPanoId = (id, position) => {
      panoCalls.push(id);
      setTimeout(() => {
        this._position = position;
        this._viewpoint = { pan: 222 };
        (this._moved || []).forEach((fn) => fn());
        (this._init || []).forEach((fn) => fn());
      }, 8);
    };
    this.setViewpoint = (v) => { this._viewpoint = v; viewpoints.push(Math.round(v.pan)); };
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
  requestAnimationFrame: (fn) => setTimeout(() => fn(Date.now()), 4), cancelAnimationFrame: clearTimeout,
  performance: { now: () => Date.now() },
  MutationObserver: class { observe() {} disconnect() {} },
  DejaVroomBridge: { postMessage: (m) => messages.push(m) },
  location: { search: "" },
  addEventListener(type, fn) { windowListeners[type] = fn; },
};
context.window = context;
vm.runInNewContext(src, context);

// 150 m due north to the junction in 25 m hops; the route leaves the junction to the east.
const lead = 150;
const drive = Array.from({ length: 7 }, (_, i) => ({
  latitude: north(i * 25), longitude: 127.0, headingDegrees: i < 6 ? 0 : 90, remainingMeters: lead - i * 25,
}));
const config = {
  type: "initialize", latitude: north(lead), longitude: 127.0, heading: 0, routeId: "r", routeSource: "OSRM/OSM",
  candidates: [{ latitude: north(lead), longitude: 127.0, viewLatitude: north(0), viewLongitude: 127.0, leadMeters: lead, headingDegrees: 0,
    name: "테스트 갈림길", score: 0.5, displayIndex: 0, evidence: ["이유"], routeLanes: [], drive }],
};
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const progress = () => messages.filter((m) => m.startsWith("drive-progress:")).map((m) => Number(m.split(":")[1]));
const assert = (ok, what) => { if (!ok) { console.log("FAIL:", what); process.exitCode = 1; } else console.log("ok:", what); };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

(async () => {
  await wait(40);
  assert(messages.includes("ready") && messages.includes("sdk-ready"), "page reaches ready and sdk-ready");
  windowListeners.message({ data: JSON.stringify(config) });
  await wait(900);
  const button = elements.autodrive;
  assert(messages.filter((m) => m === "shown").length === 1 && messages.includes("route-candidate-selected:0"), "the page sets itself up once, however often init fires");
  assert(messages.filter((m) => m === "drive-finished").length === 1, "drive finishes exactly once (init re-fire does not restart it)");
  assert(same(progress(), drive.map((p) => p.remainingMeters)), "each photo reports where it stands, down to the junction: " + progress());
  assert(Math.min(...progress()) === 0, "the drive ends on the junction and goes no further");
  assert(panoCalls.length === drive.length, "one panorama per frame (first by the page, the rest by the drive): " + panoCalls.length);
  assert(lookups.slice(-6).every((radius) => radius === 12), "drive frames only take photos within 12 m: " + lookups.slice(-6));
  assert(viewpoints[viewpoints.length - 1] === 90, "camera ends facing the way the route leaves the junction");
  assert(viewpoints.some((pan) => pan > 5 && pan < 85), "the camera turns to it gradually, not in one snap");
  assert(!viewpoints.includes(222), "a photo that resets the camera never shows through: " + [...new Set(viewpoints)]);
  assert(button.hidden === false && button.textContent === "분기점까지 다시 보기", "replay button is offered: " + button.textContent);

  // A stretch with no photo of this road is passed over; the banner is never told about it.
  messages.length = 0; panoCalls.length = 0;
  noPhotoNear = drive[3].latitude;
  button.dispatch("click");
  await wait(20);
  assert(button.textContent === "멈춤", "button shows 멈춤 while playing");
  await wait(800);
  assert(same(progress(), [150, 125, 100, 50, 25, 0]), "a frame without a photo is left out and the drive goes on: " + progress());
  assert(panoCalls.length === drive.length - 1 && messages.includes("drive-finished"), "no panorama is shown for the missing frame");
  noPhotoNear = null;

  messages.length = 0;
  button.dispatch("click");
  await wait(100);
  elements.roadview.dispatch("pointerdown");
  await wait(30); // let a panorama that was already loading settle
  const hops = panoCalls.length;
  await wait(300);
  assert(panoCalls.length === hops && !messages.includes("drive-finished"), "touching the panorama stops the drive");
  assert(button.textContent === "분기점까지 다시 보기", "button offers replay after a stop");

  // The driver steps along the road by hand: the banner follows the photo's own position.
  const stepTo = (lat, lng) => { messages.length = 0; fakeRoadview.moveTo(lat, lng); return progress(); };
  assert(same(stepTo(north(40), 127.0), [110]), "a manual step reports the distance left along the drive");
  assert(same(stepTo(north(60), east(9)), [90]), "a photo in another lane counts by how far along it is, not how far across");
  assert(same(stepTo(north(lead + 22), 127.0), [-22]), "past the junction the distance is reported as passed");
  assert(same(stepTo(north(-30), 127.0), [180]), "before the start of the drive the extra distance is added on");
  assert(same(stepTo(north(-30), 127.0), []), "the same place is not reported twice");
  const far = stepTo(north(75), east(200));
  assert(far.length === 1 && Math.abs(far[0] - Math.hypot(75, 200)) <= 2, "far from the drive only the straight distance is known: " + far);
})();
