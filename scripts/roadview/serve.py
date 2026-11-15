#!/usr/bin/env python3
"""Serve web/roadview (road view and place search pages) locally the same way GitHub Pages does, with the Kakao JS key injected.

Production is the static site built by .github/workflows/roadview-pages.yml. This server only
replaces the `__KAKAO_JS_KEY__` placeholder with `kakaoJsKey` from local.properties so the
page can be tested at http://localhost:8080/roadview/ (that origin must be registered as a
Kakao Web platform domain). See docs/roadview-setup.md.
"""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
import re

ROOT = Path(__file__).resolve().parents[2]
WEB_ROOT = ROOT / "web"
KEY_PATTERN = re.compile(r"^\s*kakaoJsKey\s*=\s*([A-Za-z0-9]+)\s*$", re.MULTILINE)
PLACEHOLDER = "__KAKAO_JS_KEY__"
# Pages that carry the key placeholder: the road view itself and the place search the app asks out of sight.
KEYED_PAGES = {
    "/roadview": "index.html",
    "/roadview/": "index.html",
    "/roadview/index.html": "index.html",
    "/roadview/places.html": "places.html",
}


def read_js_key() -> str:
    properties = ROOT / "local.properties"
    if not properties.exists():
        return ""
    match = KEY_PATTERN.search(properties.read_text(encoding="utf-8"))
    return match.group(1) if match else ""


class RoadviewHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_ROOT), **kwargs)

    def do_GET(self):
        page = KEYED_PAGES.get(urlsplit(self.path).path)
        if page:
            html = (WEB_ROOT / "roadview" / page).read_text(encoding="utf-8")
            body = html.replace(PLACEHOLDER, read_js_key()).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    if not read_js_key():
        print("kakaoJsKey is missing in local.properties; the page will show a key error. See docs/roadview-setup.md.")
    print("Serving Roadview locally at http://localhost:8080/roadview/")
    try:
        ThreadingHTTPServer(("127.0.0.1", 8080), RoadviewHandler).serve_forever()
    except KeyboardInterrupt:
        pass
