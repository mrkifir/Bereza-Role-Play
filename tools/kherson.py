#!/usr/bin/env python3
"""Bereza RP: місто Херсон (центр) з OpenStreetMap -> моделі GTA SA (DFF + COL + TXD) для open.mp.

Дані: (c) OpenStreetMap contributors, ODbL. Текстури: ambientCG.com (CC0).
Місто ріжеться на квадрати 100 x 100 м - кожен квадрат один об'єкт (мало об'єктів = гра не гальмує).
"""
import json, math, os, sys, zlib
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gtaLib import dff, col
from gen import write_txd   # запис TXD з мипмапами

SRC = sys.argv[1]           # папка з kherson_osm.json і tex/
OUT = sys.argv[2]           # папка для моделей
PWN = sys.argv[3]           # куди записати pawn-дані

# де місто стоїть у світі SA-MP: над морем на схід від Лос-Сантоса
WORLD_X, WORLD_Y, WORLD_Z = 4300.0, -1700.0, 8.0
TILE = 100.0
MODEL_BASE_ID = -1100
RW_SA = 0x36003
COL_SAMP = 39056127
FLOOR = 3.0

# ------------------------------------------------------------------ дані OSM
osm = json.load(open(os.path.join(SRC, "kherson_osm.json")))
nodes = {e["id"]: (e["lat"], e["lon"]) for e in osm["elements"] if e["type"] == "node" and "lat" in e}
ways = [e for e in osm["elements"] if e["type"] == "way" and "nodes" in e]
lat0 = sum(v[0] for v in nodes.values()) / len(nodes)
lon0 = sum(v[1] for v in nodes.values()) / len(nodes)
KX = 111320.0 * math.cos(math.radians(lat0))
KY = 110540.0
S_, W_, N_, E_ = 46.6318, 32.6085, 46.6390, 32.6190


def proj(nid):
    la, lo = nodes[nid]
    return ((lo - lon0) * KX, (la - lat0) * KY)


XMIN, YMIN = (W_ - lon0) * KX, (S_ - lat0) * KY
XMAX, YMAX = (E_ - lon0) * KX, (N_ - lat0) * KY
NTX = int(math.ceil((XMAX - XMIN) / TILE))
NTY = int(math.ceil((YMAX - YMIN) / TILE))


def tile_of(x, y):
    i = int((x - XMIN) // TILE); j = int((y - YMIN) // TILE)
    return (min(max(i, 0), NTX - 1), min(max(j, 0), NTY - 1))


def tile_center(i, j):
    return (XMIN + (i + 0.5) * TILE, YMIN + (j + 0.5) * TILE)


def inside(x, y, pad=0.0):
    return XMIN - pad <= x <= XMAX + pad and YMIN - pad <= y <= YMAX + pad


# ------------------------------------------------------------------ сітка моделі
class TMesh:
    def __init__(self, name, ox, oy):
        self.name, self.ox, self.oy = name, ox, oy
        self.mats, self.v, self.n, self.uv, self.c, self.tri = [], [], [], [], [], []
        self.cv, self.cf = [], []           # колізія: вершини, грані

    def m(self, tex):
        if tex not in self.mats:
            self.mats.append(tex)
        return self.mats.index(tex)

    def poly(self, pts, tex, uvs, normal, shade=1.0, collide=False):
        """pts - світові XY->локальні; трикутники віялом (опуклі) або ear-clip вже зроблено."""
        mi = self.m(tex)
        base = len(self.v)
        cc = max(0, min(255, int(200 * shade)))
        for p, t in zip(pts, uvs):
            self.v.append((p[0] - self.ox, p[1] - self.oy, p[2])); self.n.append(normal); self.uv.append(t); self.c.append((cc, cc, cc, 255))
        for k in range(1, len(pts) - 1):
            self.tri.append((base, base + k, base + k + 1, mi))
        if collide:
            cb = len(self.cv)
            for p in pts:
                self.cv.append((p[0] - self.ox, p[1] - self.oy, p[2]))
            for k in range(1, len(pts) - 1):
                self.cf.append((cb, cb + k, cb + k + 1))

    def tris(self, pts3, triangles, tex, uvfun, normal, shade=1.0, collide=False):
        mi = self.m(tex)
        base = len(self.v)
        cc = max(0, min(255, int(200 * shade)))
        for p in pts3:
            self.v.append((p[0] - self.ox, p[1] - self.oy, p[2])); self.n.append(normal); self.uv.append(uvfun(p)); self.c.append((cc, cc, cc, 255))
        for (a, b, c) in triangles:
            self.tri.append((base + a, base + b, base + c, mi))
        if collide:
            cb = len(self.cv)
            for p in pts3:
                self.cv.append((p[0] - self.ox, p[1] - self.oy, p[2]))
            for (a, b, c) in triangles:
                self.cf.append((cb + a, cb + b, cb + c))

    def empty(self):
        return not self.tri

    def bounds(self, pts):
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]; zs = [p[2] for p in pts]
        mn = (min(xs), min(ys), min(zs)); mx = (max(xs), max(ys), max(zs))
        c = tuple((a + b) / 2 for a, b in zip(mn, mx))
        r = math.sqrt(sum((b - a) ** 2 for a, b in zip(mn, mx))) / 2
        return mn, mx, c, r

    def write(self, path):
        mn, mx, c, r = self.bounds(self.v)
        g = dff.Geometry()
        g.vertices = [dff.Vector(*p) for p in self.v]
        g.normals = [dff.Vector(*p) for p in self.n]
        g.uv_layers = [[dff.TexCoords(u, 1.0 - v) for (u, v) in self.uv]]
        g.prelit_colors = [dff.RGBA(*cc) for cc in self.c]
        g.triangles = [dff.Triangle(b, a, m, cc) for (a, b, cc, m) in self.tri]
        g.bounding_sphere = dff.Sphere(c[0], c[1], c[2], r)
        g.surface_properties = (0, 0, 0)
        g.export_flags.update({"export_normals": True, "write_mesh_plg": True, "light": True, "modulate_color": True})
        for t in self.mats:
            mt = dff.Material()
            mt.color = dff.RGBA(255, 255, 255, 255)
            mt.surface_properties = dff.GeomSurfPro(1.0, 1.0, 1.0)
            tx = dff.Texture(); tx.filters = 2; tx.uv_addressing = 0b00010001; tx.name = t; tx.mask = ""
            mt.textures.append(tx)
            g.materials.append(mt)
        cl = dff.Clump()
        fr = dff.Frame(); fr.creation_flags = 0; fr.parent = -1
        fr.position = dff.Vector(0, 0, 0); fr.rotation_matrix = dff.Matrix((1, 0, 0), (0, 1, 0), (0, 0, 1))
        fr.name = self.name; fr.user_data = None
        cl.frame_list.append(fr); cl.geometry_list.append(g)
        at = dff.Atomic(); at.frame = 0; at.geometry = 0; at.flags = 0x4; at.extensions = {}
        cl.atomic_list.append(at)
        cl.collisions = [dff.ExtensionColl(COL_SAMP, self.col_bytes())]
        d = dff.dff(); d.clumps.append(cl); d.write_file(path, RW_SA)

    def col_bytes(self):
        pts = self.cv or self.v
        mn, mx, c, r = self.bounds(pts)
        col.Sections.init_sections(3)
        m = col.ColModel()
        m.version = 3; m.model_name = self.name[:21]; m.model_id = 0
        m.bounds = col.TBounds(mn, mx, c, r)
        m.mesh_verts = [col.TVertex(*p) for p in self.cv]
        # 4 = асфальт/бетон (звук кроків), освітлення 255
        m.mesh_faces = [col.TFace(a, b, cc, 4, 255) for (a, b, cc) in self.cf]
        cc_ = col.coll(); cc_.models = [m]
        return cc_.write_memory()


# ------------------------------------------------------------------ геометрія
def area(poly):
    s = 0.0
    for i in range(len(poly)):
        x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % len(poly)]
        s += x1 * y2 - x2 * y1
    return s / 2


def earclip(poly):
    """тріангуляція простого полігона (проти годинникової)."""
    idx = list(range(len(poly)))
    out = []

    def is_convex(a, b, c):
        return (poly[b][0] - poly[a][0]) * (poly[c][1] - poly[a][1]) - (poly[b][1] - poly[a][1]) * (poly[c][0] - poly[a][0]) > 1e-9

    def in_tri(p, a, b, c):
        def s(p1, p2, p3):
            return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
        d1, d2, d3 = s(p, a, b), s(p, b, c), s(p, c, a)
        neg = d1 < 0 or d2 < 0 or d3 < 0; pos = d1 > 0 or d2 > 0 or d3 > 0
        return not (neg and pos)

    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        n = len(idx)
        for k in range(n):
            a, b, c = idx[(k - 1) % n], idx[k], idx[(k + 1) % n]
            if not is_convex(a, b, c):
                continue
            ok = True
            for o in idx:
                if o in (a, b, c):
                    continue
                if in_tri(poly[o], poly[a], poly[b], poly[c]):
                    ok = False; break
            if ok:
                out.append((a, b, c)); idx.pop(k); break
        else:
            break
    if len(idx) == 3:
        out.append(tuple(idx))
    return out


def clean(poly):
    out = []
    for p in poly:
        if not out or (abs(p[0] - out[-1][0]) > 0.05 or abs(p[1] - out[-1][1]) > 0.05):
            out.append(p)
    if len(out) > 2 and abs(out[0][0] - out[-1][0]) < 0.05 and abs(out[0][1] - out[-1][1]) < 0.05:
        out.pop()
    return out


tiles = {}


def T(i, j):
    if (i, j) not in tiles:
        cx, cy = tile_center(i, j)
        tiles[(i, j)] = TMesh("kh_%d_%d" % (i, j), cx, cy)
    return tiles[(i, j)]


# ---- земля (трава) кожного квадрата + схили до моря по краю
for i in range(NTX):
    for j in range(NTY):
        m = T(i, j)
        x0 = XMIN + i * TILE; y0 = YMIN + j * TILE; x1 = min(x0 + TILE, XMAX); y1 = min(y0 + TILE, YMAX)
        q = [(x0, y0, 0.0), (x1, y0, 0.0), (x1, y1, 0.0), (x0, y1, 0.0)]
        m.poly(q, "kh_grass", [(p[0] / 8, p[1] / 8) for p in q], (0, 0, 1), 1.0, collide=True)
        # набережна-стіна до моря
        if j == 0:
            m.poly([(x1, y0, 0.0), (x0, y0, 0.0), (x0, y0, -14), (x1, y0, -14)], "kh_wall", [(0, 0), ((x1 - x0) / 4, 0), ((x1 - x0) / 4, 3.5), (0, 3.5)], (0, -1, 0), 0.9, True)
        if j == NTY - 1:
            m.poly([(x0, y1, 0.0), (x1, y1, 0.0), (x1, y1, -14), (x0, y1, -14)], "kh_wall", [(0, 0), ((x1 - x0) / 4, 0), ((x1 - x0) / 4, 3.5), (0, 3.5)], (0, 1, 0), 0.9, True)
        if i == 0:
            m.poly([(x0, y0, 0.0), (x0, y1, 0.0), (x0, y1, -14), (x0, y0, -14)], "kh_wall", [(0, 0), ((y1 - y0) / 4, 0), ((y1 - y0) / 4, 3.5), (0, 3.5)], (-1, 0, 0), 0.9, True)
        if i == NTX - 1:
            m.poly([(x1, y1, 0.0), (x1, y0, 0.0), (x1, y0, -14), (x1, y1, -14)], "kh_wall", [(0, 0), ((y1 - y0) / 4, 0), ((y1 - y0) / 4, 3.5), (0, 3.5)], (1, 0, 0), 0.9, True)

# ---- майданчики (паркінги, пішохідні зони) - плитка / асфальт
for w in ways:
    t = w.get("tags", {})
    tex = None
    if t.get("amenity") == "parking":
        tex, z = "kh_asphalt", 0.03
    elif t.get("highway") == "pedestrian" and t.get("area") == "yes":
        tex, z = "kh_paving", 0.03
    elif t.get("landuse") in ("retail", "commercial") or t.get("place") == "square":
        tex, z = "kh_paving", 0.02
    if not tex or w["nodes"][0] != w["nodes"][-1]:
        continue
    poly = clean([proj(n) for n in w["nodes"] if n in nodes])
    if len(poly) < 3:
        continue
    if area(poly) < 0:
        poly.reverse()
    cx = sum(p[0] for p in poly) / len(poly); cy = sum(p[1] for p in poly) / len(poly)
    if not inside(cx, cy):
        continue
    tr = earclip(poly)
    T(*tile_of(cx, cy)).tris([(p[0], p[1], z) for p in poly], tr, tex, lambda p: (p[0] / 6, p[1] / 6), (0, 0, 1), 1.0)

# ---- дороги і тротуари
RW = {"primary": (14, "kh_asphalt", 0.07), "secondary": (12, "kh_asphalt", 0.07), "tertiary": (10, "kh_asphalt", 0.07),
      "residential": (7, "kh_asphalt", 0.065), "unclassified": (7, "kh_asphalt", 0.065), "living_street": (6, "kh_asphalt", 0.065),
      "service": (4.5, "kh_asphalt", 0.06), "pedestrian": (6, "kh_paving", 0.05), "footway": (2.6, "kh_paving", 0.045),
      "path": (2.0, "kh_paving", 0.045), "steps": (2.5, "kh_paving", 0.045), "cycleway": (2.0, "kh_asphalt", 0.045)}
for w in ways:
    t = w.get("tags", {})
    hw = t.get("highway")
    if hw not in RW or t.get("area") == "yes":
        continue
    width, tex, z = RW[hw]
    pts = [proj(n) for n in w["nodes"] if n in nodes]
    for a, b in zip(pts, pts[1:]):
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        if L < 0.1:
            continue
        steps = max(1, int(L // 20) + 1)
        for s in range(steps):
            p = (a[0] + (b[0] - a[0]) * s / steps, a[1] + (b[1] - a[1]) * s / steps)
            q = (a[0] + (b[0] - a[0]) * (s + 1) / steps, a[1] + (b[1] - a[1]) * (s + 1) / steps)
            mx_, my_ = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
            if not inside(mx_, my_):
                continue
            dx, dy = (q[0] - p[0]) / L * steps, (q[1] - p[1]) / L * steps
            nx, ny = -dy * width / 2, dx * width / 2
            ex, ey = dx * width / 2, dy * width / 2      # подовжуємо, щоб стики не мали щілин
            quad = [(p[0] + nx - ex, p[1] + ny - ey, z), (p[0] - nx - ex, p[1] - ny - ey, z), (q[0] - nx + ex, q[1] - ny + ey, z), (q[0] + nx + ex, q[1] + ny + ey, z)]
            # порядок проти годинникової (дивимось згори)
            if area([(v[0], v[1]) for v in quad]) < 0:
                quad.reverse()
            seg = math.hypot(q[0] - p[0], q[1] - p[1])
            T(*tile_of(mx_, my_)).poly(quad, tex, [(0, 0), (width / 4, 0), (width / 4, (seg + width) / 4), (0, (seg + width) / 4)], (0, 0, 1), 1.0)

# ---- будівлі
FAC = ["kh_fac_a", "kh_fac_b", "kh_fac_c", "kh_fac_d"]
nb = 0
for w in ways:
    t = w.get("tags", {})
    b = t.get("building")
    if not b or b in ("ruins", "roof", "construction") or w["nodes"][0] != w["nodes"][-1]:
        continue
    poly = clean([proj(n) for n in w["nodes"] if n in nodes])
    if len(poly) < 3:
        continue
    if area(poly) < 0:
        poly.reverse()
    A = area(poly)
    if A < 12:
        continue
    cx = sum(p[0] for p in poly) / len(poly); cy = sum(p[1] for p in poly) / len(poly)
    if not inside(cx, cy):
        continue
    try:
        lv = int(float(t.get("building:levels", "0")))
    except ValueError:
        lv = 0
    if lv <= 0:
        if t.get("height"):
            try: lv = max(1, round(float(t["height"].split()[0]) / FLOOR))
            except ValueError: lv = 0
    if lv <= 0:
        lv = {"apartments": 5, "residential": 3, "hospital": 4, "school": 3, "retail": 1, "commercial": 3, "garage": 1, "garages": 1, "shed": 1, "kiosk": 1, "church": 3}.get(b, 0)
        if lv == 0:
            lv = 4 if A > 700 else (3 if A > 300 else 2)
    lv = min(lv, 16)
    H = lv * FLOOR + 0.6
    h = zlib.crc32(str(w["id"]).encode())
    if lv >= 5:
        fac = "kh_fac_panel" if h % 3 else "kh_fac_d"
    elif b in ("garage", "garages", "shed"):
        fac = "kh_wall"
    else:
        fac = FAC[h % len(FAC)]
    m = T(*tile_of(cx, cy))
    # стіни
    run = 0.0
    for k in range(len(poly)):
        p = poly[k]; q = poly[(k + 1) % len(poly)]
        L = math.hypot(q[0] - p[0], q[1] - p[1])
        if L < 0.01:
            continue
        nx, ny = (q[1] - p[1]) / L, -(q[0] - p[0]) / L        # назовні для полігона проти годинникової
        u0, u1 = run / 3.0, (run + L) / 3.0
        run += L
        shade = 0.82 + 0.18 * abs(nx * 0.6 + ny * 0.8)
        m.poly([(p[0], p[1], 0.0), (q[0], q[1], 0.0), (q[0], q[1], H), (p[0], p[1], H)], fac,
               [(u0, 0), (u1, 0), (u1, H / FLOOR), (u0, H / FLOOR)], (nx, ny, 0), shade, collide=True)
    # дах
    tr = earclip(poly)
    roof = "kh_roof_tile" if (lv <= 2 and h % 2 == 0) else "kh_roof"
    m.tris([(p[0], p[1], H) for p in poly], tr, roof, lambda p: (p[0] / 5, p[1] / 5), (0, 0, 1), 1.05, collide=True)
    nb += 1

print("buildings", nb, "tiles", NTX, "x", NTY)

# ------------------------------------------------------------------ текстури
TEX = os.path.join(SRC, "tex")


def load(n, size):
    return Image.open(os.path.join(TEX, n)).convert("RGB").resize((size, size), Image.LANCZOS)


def facade(base, tint=None, size=512, brick=False):
    """клітинка фасаду 3 x 3 м (один поверх, одне вікно)."""
    im = base.copy().resize((size, size), Image.LANCZOS)
    if tint:
        tl = Image.new("RGB", im.size, tint)
        im = Image.blend(im, tl, 0.35)
    d = ImageDraw.Draw(im)
    s = size / 3.0                     # пікселів на метр
    wx0, wx1 = 0.75 * s, 2.25 * s      # вікно 1.5 м
    wy0, wy1 = 0.55 * s, 2.25 * s      # від 0.75 до 2.45 м над підлогою (картинка зверху вниз)
    # укоси
    d.rectangle([wx0 - 6, wy0 - 6, wx1 + 6, wy1 + 10], fill=(int(im.getpixel((5, 5))[0] * 0.8),) * 3)
    d.rectangle([wx0, wy0, wx1, wy1], fill=(236, 236, 232))            # пластикова рама
    g0, g1 = wx0 + 10, wx1 - 10
    gy0, gy1 = wy0 + 10, wy1 - 10
    # скло з градієнтом (відображення неба)
    for y in range(int(gy0), int(gy1)):
        k = (y - gy0) / max(1, gy1 - gy0)
        c = (int(70 + 60 * (1 - k)), int(92 + 60 * (1 - k)), int(112 + 55 * (1 - k)))
        d.line([(g0, y), (g1, y)], fill=c)
    mid = (g0 + g1) / 2
    d.rectangle([mid - 5, gy0, mid + 5, gy1], fill=(236, 236, 232))     # імпост
    d.rectangle([g0, gy0 + (gy1 - gy0) * 0.32 - 4, mid, gy0 + (gy1 - gy0) * 0.32 + 4], fill=(236, 236, 232))
    d.rectangle([wx0 - 10, wy1 + 2, wx1 + 10, wy1 + 14], fill=(205, 205, 200))  # підвіконня (відлив)
    # легкий бруд під вікном
    dirt = Image.new("L", im.size, 0)
    dd = ImageDraw.Draw(dirt)
    dd.rectangle([wx0, wy1 + 14, wx1, wy1 + 70], fill=40)
    dirt = dirt.filter(ImageFilter.GaussianBlur(14))
    im = Image.composite(Image.new("RGB", im.size, (60, 55, 50)), im, dirt)
    # міжповерхова смуга
    d = ImageDraw.Draw(im)
    d.line([(0, size - 2), (size, size - 2)], fill=(int(im.getpixel((5, 5))[0] * 0.75),) * 3, width=3)
    return im


plaster = load("plaster_0.jpg", 512)
plaster2 = load("plaster_2.jpg", 512)
panel = load("concrete_1.jpg", 512)
brick = load("bricks_2.jpg", 512)
texs = [
    ("kh_grass", load("grass_1.jpg", 256), False),
    ("kh_asphalt", load("asphalt_1.jpg", 256), False),
    ("kh_paving", load("paving_1.jpg", 256), False),
    ("kh_wall", load("concrete_2.jpg", 256), False),
    ("kh_roof", load("roof_0.jpg", 256), False),
    ("kh_roof_tile", load("roof_1.jpg", 256), False),
    ("kh_fac_a", facade(plaster, (236, 214, 160)), False),     # кремовий
    ("kh_fac_b", facade(plaster2, (214, 196, 168)), False),    # бежевий
    ("kh_fac_c", facade(plaster, (190, 205, 214)), False),     # блакитний
    ("kh_fac_d", facade(plaster2, (225, 225, 220)), False),      # світло-сірий
    ("kh_fac_panel", facade(panel, (200, 196, 186)), False),
]
os.makedirs(OUT, exist_ok=True)
write_txd(os.path.join(OUT, "kherson.txd"), texs)
prev = Image.new("RGB", (256 * 6, 256 * 2))
for k, (n, im, a) in enumerate(texs):
    prev.paste(im.resize((256, 256)), ((k % 6) * 256, (k // 6) * 256))
prev.save(os.path.join(OUT, "_kherson_tex.png"))

# ------------------------------------------------------------------ запис моделей + pawn
lines = []
mid = 0
total_tris = 0
for (i, j), m in sorted(tiles.items()):
    if m.empty():
        continue
    m.write(os.path.join(OUT, m.name + ".dff"))
    wx, wy = WORLD_X + m.ox, WORLD_Y + m.oy
    lines.append('    {%d, "%s", %.2f, %.2f, %.2f}' % (MODEL_BASE_ID - mid, m.name, wx, wy, WORLD_Z))
    total_tris += len(m.tri)
    mid += 1
# ---- дерева (стандартні моделі дерев GTA як «реквізит»)
trees = []
for e in osm["elements"]:
    if e["type"] == "node" and e.get("tags", {}).get("natural") == "tree" and e["id"] in nodes:
        trees.append(proj(e["id"]))
for w in ways:
    t = w.get("tags", {})
    pts = [proj(n) for n in w["nodes"] if n in nodes]
    if t.get("natural") == "tree_row":
        for a, b in zip(pts, pts[1:]):
            L = math.hypot(b[0] - a[0], b[1] - a[1])
            k = 0.0
            while k < L:
                trees.append((a[0] + (b[0] - a[0]) * k / L, a[1] + (b[1] - a[1]) * k / L)); k += 9.0
    elif t.get("natural") == "wood" or t.get("leisure") == "park":
        if len(pts) > 2:
            xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
            import random
            rnd = random.Random(w["id"])
            poly = pts
            for _ in range(int(abs(area(pts)) / 220)):
                x = rnd.uniform(min(xs), max(xs)); y = rnd.uniform(min(ys), max(ys))
                # точка в полігоні
                c = False
                for k2 in range(len(poly)):
                    x1, y1 = poly[k2]; x2, y2 = poly[(k2 + 1) % len(poly)]
                    if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-12) + x1:
                        c = not c
                if c:
                    trees.append((x, y))
trees = [t_ for t_ in trees if inside(t_[0], t_[1], -3)]
# прорідити: не ближче 6 м одне до одного, максимум 260
kept = []
for t_ in trees:
    if all((t_[0] - k_[0]) ** 2 + (t_[1] - k_[1]) ** 2 > 36 for k_ in kept):
        kept.append(t_)
kept = kept[:260]
TREE_MODELS = [615, 616, 617, 618]
tree_lines = ['    {%d.0, %.2f, %.2f, %.2f}' % (TREE_MODELS[k % 4], WORLD_X + x, WORLD_Y + y, WORLD_Z) for k, (x, y) in enumerate(kept)]
print("trees", len(kept))
# точка появи: вузол найближчої великої дороги до центру
best = None
for w in ways:
    if w.get("tags", {}).get("highway") in ("primary", "secondary", "tertiary", "pedestrian", "residential"):
        for n in w["nodes"]:
            if n in nodes:
                x, y = proj(n)
                d = x * x + y * y
                if best is None or d < best[0]:
                    best = (d, x, y)
SPAWN = (WORLD_X + best[1], WORLD_Y + best[2], WORLD_Z + 1.2)
print("spawn", SPAWN)

with open(PWN, "w", encoding="utf-8") as f:
    f.write("// згенеровано tools/kherson.py - не редагувати вручну\n")
    f.write("// Херсон (центр), дані (c) OpenStreetMap contributors (ODbL), текстури ambientCG (CC0)\n")
    f.write("#define KH_X (%.1f)\n#define KH_Y (%.1f)\n#define KH_Z (%.1f)\n" % (WORLD_X, WORLD_Y, WORLD_Z))
    f.write("#define KH_TILES (%d)\n" % mid)
    f.write("#define KH_SPAWN_X (%.2f)\n#define KH_SPAWN_Y (%.2f)\n#define KH_SPAWN_Z (%.2f)\n" % SPAWN)
    f.write("#if defined _INC_open_mp\n")
    f.write("enum E_KHTILE { khId, khFile[16], Float:khX, Float:khY, Float:khZ }\n")
    f.write("new const gKhTiles[KH_TILES][E_KHTILE] =\n{\n" + ",\n".join(lines) + "\n};\n")
    f.write("#define KH_TREES (%d)\n" % len(tree_lines))
    f.write("new const Float:gKhTrees[KH_TREES][4] =\n{\n" + ",\n".join(tree_lines) + "\n};\n")
    f.write("#endif\n")
print("tiles written", mid, "triangles", total_tris)
