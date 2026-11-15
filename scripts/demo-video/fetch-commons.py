"""Download Wikimedia Commons files (cached by name) and record their license in <dir>/credits.tsv.

Usage: python3 -I fetch-commons.py <clips-dir> <file name>...
Stops if a file is not CC0 or public domain: the video must not need attribution or other permissions.
"""
import json, os, sys, time, urllib.parse, urllib.request

out, names = sys.argv[1], sys.argv[2:]
ua = {"User-Agent": "deja-vroom-demo/0.1 (contest demo video)"}
credits_path = os.path.join(out, "credits.tsv")
credits = {}
if os.path.exists(credits_path):
    for line in open(credits_path, encoding="utf-8"):
        name, license, page = line.rstrip("\n").split("\t")
        credits[name] = (license, page)
for name in names:
    path = os.path.join(out, name.replace(" ", "_") + ".media")
    if os.path.exists(path) and name in credits:
        continue
    params = {"action": "query", "format": "json", "titles": "File:" + name, "prop": "imageinfo",
              "iiprop": "url|extmetadata", "iiextmetadatafilter": "LicenseShortName|Artist"}
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    page = next(iter(json.load(urllib.request.urlopen(urllib.request.Request(url, headers=ua), timeout=30))["query"]["pages"].values()))
    info = page["imageinfo"][0]; meta = info["extmetadata"]
    license = meta["LicenseShortName"]["value"]
    if license not in ("CC0", "Public domain"):
        sys.exit(f"FAIL: {name} is {license}; clips and sounds must be CC0 or public domain")
    credits[name] = (license, info["descriptionurl"])
    if not os.path.exists(path):
        print(f"download {name}")
        with urllib.request.urlopen(urllib.request.Request(info["url"], headers=ua), timeout=300) as r, open(path + ".part", "wb") as f:
            f.write(r.read())
        os.replace(path + ".part", path)
    time.sleep(2)
with open(credits_path, "w", encoding="utf-8") as f:
    for name, (license, page) in credits.items():
        f.write(f"{name}\t{license}\t{page}\n")
