"""Тягне вільні дані для карти: OpenStreetMap (Overpass, ODbL) і текстури ambientCG (CC0)."""
import json, os, sys, time, urllib.request, urllib.parse, zipfile, io

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "BerezaRP-mapgen/1.0 (github.com/mrkifir)"}

def get(url, data=None, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, data=data, headers=UA)
            with urllib.request.urlopen(req, timeout=180) as r:
                return r.read()
        except Exception as e:
            print("retry", url, e); time.sleep(10 * (i + 1))
    raise SystemExit("failed " + url)

# центр Херсона (вул. Суворова / площа Свободи), ~800 x 800 м
S, W, N, E = 46.6318, 32.6085, 46.6390, 32.6190
q = f"""
[out:json][timeout:120];
(
  way["building"]({S},{W},{N},{E});
  relation["building"]({S},{W},{N},{E});
  way["highway"]({S},{W},{N},{E});
  way["landuse"]({S},{W},{N},{E});
  way["leisure"]({S},{W},{N},{E});
  way["natural"]({S},{W},{N},{E});
  way["amenity"]({S},{W},{N},{E});
  way["waterway"]({S},{W},{N},{E});
  node["amenity"]({S},{W},{N},{E});
  node["shop"]({S},{W},{N},{E});
  node["natural"="tree"]({S},{W},{N},{E});
);
out body; >; out skel qt;
"""
data = get("https://overpass-api.de/api/interpreter", urllib.parse.urlencode({"data": q}).encode())
open(os.path.join(OUT, "kherson_osm.json"), "wb").write(data)
print("osm bytes", len(data))

# текстури ambientCG (CC0), 1K JPG
queries = {
    "bricks": "Bricks", "plaster": "Plaster", "concrete": "Concrete", "asphalt": "Asphalt",
    "paving": "PavingStones", "facade": "Facade", "roof": "RoofingTiles", "grass": "Grass",
    "metal": "MetalPlates", "tiles": "Tiles", "ground": "Ground",
}
tex = os.path.join(OUT, "tex"); os.makedirs(tex, exist_ok=True)
meta = {}
for key, cat in queries.items():
    api = "https://ambientcg.com/api/v2/full_json?" + urllib.parse.urlencode({"category": cat, "type": "Material", "limit": 6, "sort": "Popular", "include": "downloadData"})
    j = json.loads(get(api))
    got = 0
    for a in j.get("foundAssets", []):
        dl = None
        for f in a.get("downloadFolders", {}).get("default", {}).get("downloadFiletypeCategories", {}).get("zip", {}).get("downloads", []):
            if f.get("attribute") == "1K-JPG":
                dl = f.get("downloadLink") or f.get("fullDownloadPath")
        if not dl:
            continue
        z = zipfile.ZipFile(io.BytesIO(get(dl)))
        for n in z.namelist():
            if n.endswith("_Color.jpg"):
                fn = f"{key}_{got}.jpg"
                open(os.path.join(tex, fn), "wb").write(z.read(n))
                meta[fn] = a["assetId"]
                got += 1
        if got >= 3:
            break
    print(key, got)
json.dump(meta, open(os.path.join(tex, "sources.json"), "w"), indent=1)
