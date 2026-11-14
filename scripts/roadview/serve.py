#!/usr/bin/env python3
"""Serve the local Roadview test page and inject its origin-restricted JS key."""

from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re
import secrets
import json
import math
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit, parse_qs
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
WEB_ROOT = ROOT / "web"
KEY_PATTERN = re.compile(r"^\s*kakaoJsKey\s*=\s*([A-Za-z0-9]+)\s*$", re.MULTILINE)
MAPILLARY_TOKEN_PATTERN = re.compile(r"^\s*mapillaryAccessToken\s*=\s*([^\s#]+)\s*$", re.MULTILINE)


def read_js_key() -> str:
    properties = ROOT / "local.properties"
    if not properties.exists():
        return ""
    match = KEY_PATTERN.search(properties.read_text(encoding="utf-8"))
    return match.group(1) if match else ""


def read_mapillary_token() -> str:
    properties = ROOT / "local.properties"
    if not properties.exists():
        return ""
    match = MAPILLARY_TOKEN_PATTERN.search(properties.read_text(encoding="utf-8"))
    return match.group(1) if match else ""


class RoadviewHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_ROOT), **kwargs)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/api/mapillary-thumbnail":
            self.send_mapillary_thumbnail(parse_qs(urlsplit(self.path).query))
            return
        if path in ("/roadview", "/roadview/"):
            html = (WEB_ROOT / "roadview" / "index.html").read_text(encoding="utf-8")
            html = html.replace("__KAKAO_JS_KEY__", read_js_key())
            nonce = secrets.token_urlsafe(18)
            html = html.replace("__CSP_NONCE__", nonce)
            body = html.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Security-Policy", f"upgrade-insecure-requests; default-src 'self'; script-src 'self' 'nonce-{nonce}' https://dapi.kakao.com https://*.daumcdn.net https://t1.kakaocdn.net; connect-src 'self' https://dapi.kakao.com https://*.daumcdn.net https://t1.kakaocdn.net https://rv.map.kakao.com; img-src 'self' data: https://images.mapillary.com https://*.daumcdn.net https://*.kakaocdn.net; style-src 'self' 'unsafe-inline' https://*.daumcdn.net; font-src 'self' https://*.daumcdn.net; frame-src 'none'; object-src 'none'; base-uri 'none'")
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def send_mapillary_thumbnail(self, query):
        token = read_mapillary_token()
        try:
            latitude = float(query.get("lat", [""])[0])
            longitude = float(query.get("lng", [""])[0])
            heading = float(query.get("heading", ["0"])[0]) % 360
            if not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
                raise ValueError("invalid coordinate")
        except (TypeError, ValueError):
            self.send_json(400, {"status": "invalid-coordinate"})
            return
        if not token:
            self.send_json(200, {"status": "unconfigured"})
            return

        lat_radius = 80 / 111_320
        lon_radius = lat_radius / max(math.cos(math.radians(latitude)), 0.15)
        params = urlencode({
            "fields": "id,thumb_1024_url,computed_geometry,geometry,captured_at,computed_compass_angle,compass_angle,creator{username}",
            "bbox": f"{longitude-lon_radius},{latitude-lat_radius},{longitude+lon_radius},{latitude+lat_radius}",
            "limit": "100",
        })
        request = Request(
            f"https://graph.mapillary.com/images?{params}",
            headers={"Authorization": f"OAuth {token}", "Accept": "application/json", "User-Agent": "FirstLook/0.1"},
        )
        try:
            with urlopen(request, timeout=8) as response:
                data = json.loads(response.read().decode("utf-8")).get("data", [])
        except (HTTPError, URLError, TimeoutError, json.JSONDecodeError):
            self.send_json(200, {"status": "provider-error"})
            return

        candidates = []
        for image in data:
            image_url = image.get("thumb_1024_url")
            geometry = image.get("computed_geometry") or image.get("geometry") or {}
            coordinates = geometry.get("coordinates", [])
            if not image_url or len(coordinates) < 2:
                continue
            image_lon, image_lat = coordinates[:2]
            distance = math.hypot((image_lat-latitude) * 111_320, (image_lon-longitude) * 111_320 * math.cos(math.radians(latitude)))
            if distance > 80:
                continue
            image_heading = image.get("computed_compass_angle", image.get("compass_angle"))
            try:
                direction_delta = 0 if image_heading is None else abs((float(image_heading) - heading + 180) % 360 - 180)
            except (TypeError, ValueError):
                direction_delta = 0
            candidates.append((distance + direction_delta * 0.7, distance, direction_delta, image))
        if not candidates:
            self.send_json(200, {"status": "no-coverage"})
            return
        _, distance, direction_delta, image = min(candidates, key=lambda item: item[0])
        creator = image.get("creator") or {}
        creator_name = creator.get("username") if isinstance(creator, dict) else None
        self.send_json(200, {
            "status": "ok",
            "imageUrl": image["thumb_1024_url"],
            "imageId": image["id"],
            "creator": creator_name or "Mapillary contributor",
            "distanceMeters": round(distance),
            "directionDifferenceDegrees": round(direction_delta),
            "capturedAt": image.get("captured_at"),
        })

    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    print("Serving Roadview locally at http://localhost:8080/roadview/")
    print("Press Ctrl+C to stop.")
    try:
        ThreadingHTTPServer(("127.0.0.1", 8080), RoadviewHandler).serve_forever()
    except KeyboardInterrupt:
        print("\nRoadview server stopped.")
