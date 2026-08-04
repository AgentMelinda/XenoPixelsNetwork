import json
import urllib.parse
import urllib.request

def get(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "XenoPixels-dev"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())

for proj, name in [("XeEYk41R", "ballistix"), ("kzF5itx6", "voltaic")]:
    q = urllib.parse.urlencode({
        "game_versions": json.dumps(["1.20.1"]),
        "loaders": json.dumps(["forge"]),
    })
    vers = get(f"https://api.modrinth.com/v2/project/{proj}/version?{q}")
    print("===", name, "count", len(vers))
    for v in vers[:8]:
        files = [f["filename"] for f in v.get("files", [])[:1]]
        print(v["id"], v["version_number"], files)
