#!/usr/bin/env python3
"""Bereza RP: генератор моделей міста (DFF + вбудована COL + TXD) для GTA SA / SA-MP.

Будівлі складаються з коробок і площин з текстурами, намальованими кодом (PIL).
Запис файлів - через gtaLib з DragonFF (GPL-3.0).
"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gtaLib import dff, col, txd
from gtaLib.txd import ImageEncoder
from gtaLib.dff import NativePlatformType

RW_SA = 0x36003
COL_SAMP = 39056127          # тип розширення колізії, який розуміє SA-MP / open.mp
FONT = os.path.join(os.path.dirname(__file__), "..", "..", "cef", "assets", "bereza", "fonts", "Oswald.ttf")


# ===================================================================== геометрія
class Mesh:
    def __init__(self, name):
        self.name = name
        self.mats = []          # імена текстур
        self.v, self.n, self.uv, self.c, self.tri = [], [], [], [], []
        self.boxes = []         # колізія: (min, max, surface)

    def mat(self, tex):
        if tex not in self.mats:
            self.mats.append(tex)
        return self.mats.index(tex)

    def quad(self, p, tex, uvs, normal, shade=1.0):
        """p - 4 точки проти годинникової стрілки, якщо дивитись з боку нормалі."""
        m = self.mat(tex)
        base = len(self.v)
        c = max(0, min(255, int(225 * shade)))
        for i in range(4):
            self.v.append(p[i]); self.n.append(normal); self.uv.append(uvs[i]); self.c.append((c, c, c, 255))
        # двосторонні не робимо - GTA малює одну сторону
        self.tri.append((base, base + 1, base + 2, m))
        self.tri.append((base, base + 2, base + 3, m))

    def box(self, x0, y0, z0, x1, y1, z1, tex, scale=(3.0, 3.0), top=None, bottom=False, sides=None, collide=True, uv0=(0.0, 0.0)):
        """коробка; UV у метрах / scale (1 повтор текстури = scale метрів)."""
        su, sv = scale
        ou, ov = uv0
        t = tex
        S = sides or {}
        # +Y (перед / «північ»)
        self.quad([(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)], S.get("py", t),
                  [(ou, ov), ((x1 - x0) / su + ou, ov), ((x1 - x0) / su + ou, (z1 - z0) / sv + ov), (ou, (z1 - z0) / sv + ov)], (0, 1, 0), 0.92)
        # -Y
        self.quad([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], S.get("ny", t),
                  [(ou, ov), ((x1 - x0) / su + ou, ov), ((x1 - x0) / su + ou, (z1 - z0) / sv + ov), (ou, (z1 - z0) / sv + ov)], (0, -1, 0), 1.0)
        # +X
        self.quad([(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], S.get("px", t),
                  [(ou, ov), ((y1 - y0) / su + ou, ov), ((y1 - y0) / su + ou, (z1 - z0) / sv + ov), (ou, (z1 - z0) / sv + ov)], (1, 0, 0), 0.85)
        # -X
        self.quad([(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)], S.get("nx", t),
                  [(ou, ov), ((y1 - y0) / su + ou, ov), ((y1 - y0) / su + ou, (z1 - z0) / sv + ov), (ou, (z1 - z0) / sv + ov)], (-1, 0, 0), 0.8)
        if top is not False:
            tt = top or t
            self.quad([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], tt,
                      [(0, 0), ((x1 - x0) / su, 0), ((x1 - x0) / su, (y1 - y0) / su), (0, (y1 - y0) / su)], (0, 0, 1), 1.05)
        if bottom:
            self.quad([(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)], t,
                      [(0, 0), (1, 0), (1, 1), (0, 1)], (0, 0, -1), 0.6)
        if collide:
            self.boxes.append(((x0, y0, z0), (x1, y1, z1)))

    def plane_y(self, x0, x1, z0, z1, y, tex, facing=1, uv=((0, 0), (1, 0), (1, 1), (0, 1))):
        """вертикальна площина (вивіска, двері) перед стіною."""
        if facing > 0:
            p = [(x1, y, z0), (x0, y, z0), (x0, y, z1), (x1, y, z1)]
        else:
            p = [(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)]
        self.quad(p, tex, list(uv), (0, facing, 0), 1.0)

    def bounds(self):
        xs = [p[0] for p in self.v]; ys = [p[1] for p in self.v]; zs = [p[2] for p in self.v]
        mn = (min(xs), min(ys), min(zs)); mx = (max(xs), max(ys), max(zs))
        c = tuple((a + b) / 2 for a, b in zip(mn, mx))
        r = math.sqrt(sum((b - a) ** 2 for a, b in zip(mn, mx))) / 2
        return mn, mx, c, r

    # ------------------------------------------------------------ запис DFF
    def write(self, path):
        mn, mx, c, r = self.bounds()
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
            m = dff.Material()
            m.color = dff.RGBA(255, 255, 255, 255)
            m.surface_properties = dff.GeomSurfPro(1.0, 1.0, 1.0)
            tx = dff.Texture()
            tx.filters = 2
            tx.uv_addressing = 0b00010001
            tx.name = t
            tx.mask = ""
            m.textures.append(tx)
            g.materials.append(m)

        cl = dff.Clump()
        fr = dff.Frame()
        fr.creation_flags = 0
        fr.parent = -1
        fr.position = dff.Vector(0, 0, 0)
        fr.rotation_matrix = dff.Matrix((1, 0, 0), (0, 1, 0), (0, 0, 1))
        fr.name = self.name
        fr.user_data = None
        cl.frame_list.append(fr)
        cl.geometry_list.append(g)
        at = dff.Atomic()
        at.frame = 0
        at.geometry = 0
        at.flags = 0x4
        at.extensions = {}
        cl.atomic_list.append(at)
        cl.collisions = [dff.ExtensionColl(COL_SAMP, self.col_bytes())]

        d = dff.dff()
        d.clumps.append(cl)
        d.write_file(path, RW_SA)

    def col_bytes(self):
        mn, mx, c, r = self.bounds()
        col.Sections.init_sections(3)
        m = col.ColModel()
        m.version = 3
        m.model_name = self.name[:21]
        m.model_id = 0
        m.bounds = col.TBounds(mn, mx, c, r)
        surf = col.TSurface(0, 0, 0, 255)   # 0 = бетон
        m.boxes = [col.TBox(a, b, surf) for (a, b) in self.boxes]
        cc = col.coll()
        cc.models = [m]
        return cc.write_memory()


# ===================================================================== текстури
def tex_native(name, img, alpha=False):
    img = img.convert("RGBA")
    levels = []
    w, h = img.size
    cur = img
    while True:
        levels.append(ImageEncoder.rgba_to_bgra8888(bytearray(cur.tobytes())))
        if w <= 4 or h <= 4:
            break
        w //= 2; h //= 2
        cur = img.resize((w, h), Image.LANCZOS)
    t = txd.TextureNative()
    t.platform_id = NativePlatformType.D3D9
    t.filter_mode = 0x06
    t.uv_addressing = 0b00010001
    t.name = name
    t.mask = ""
    t.raster_format_flags = (txd.RasterFormat.RASTER_8888 << 8) | 0x8000
    t.d3d_format = txd.D3DFormat.D3D_8888
    t.width, t.height = img.size
    t.depth = 32
    t.num_levels = len(levels)
    t.raster_type = 4
    t.platform_properties = type("PP", (), {"alpha": alpha, "cube_texture": False, "auto_mipmaps": False, "compressed": False})()
    t.palette = b""
    t.pixels = levels
    return t


def write_txd(path, textures):
    t = txd.txd()
    t.device_id = txd.DeviceType.DEVICE_D3D9
    t.native_textures = [tex_native(n, im, a) for (n, im, a) in textures]
    t.write_file(path, RW_SA)


def noise(img, amount=10, seed=1):
    import random
    rnd = random.Random(seed)
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            d = rnd.randint(-amount, amount)
            r, g, b = px[x, y][:3]
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), 255)
    return img


def t_panel():
    """клітинка панельного фасаду: 1 вікно на 3 x 2.8 м."""
    im = Image.new("RGBA", (128, 128), (196, 190, 178, 255))
    noise(im, 8, 11)
    d = ImageDraw.Draw(im)
    d.line([(0, 0), (127, 0)], fill=(150, 145, 136), width=2)      # шов між панелями
    d.line([(0, 0), (0, 127)], fill=(160, 155, 146), width=2)
    # вікно
    d.rectangle([34, 34, 94, 102], fill=(235, 235, 230))           # рама
    d.rectangle([39, 39, 89, 97], fill=(58, 74, 92))               # скло
    d.line([(64, 39), (64, 97)], fill=(235, 235, 230), width=3)
    d.line([(39, 60), (64, 60)], fill=(235, 235, 230), width=3)
    d.polygon([(42, 95), (60, 42), (66, 42), (48, 95)], fill=(80, 98, 118))   # відблиск
    d.rectangle([30, 102, 98, 106], fill=(140, 136, 128))           # підвіконня
    return im


def t_panel_blank():
    im = Image.new("RGBA", (128, 128), (190, 184, 172, 255))
    noise(im, 8, 12)
    d = ImageDraw.Draw(im)
    d.line([(0, 0), (127, 0)], fill=(150, 145, 136), width=2)
    d.line([(0, 0), (0, 127)], fill=(160, 155, 146), width=2)
    return im


def t_roof():
    im = Image.new("RGBA", (128, 128), (64, 64, 66, 255))
    noise(im, 14, 3)
    return im


def t_concrete():
    im = Image.new("RGBA", (128, 128), (150, 148, 142, 255))
    noise(im, 12, 4)
    return im


def t_door():
    im = Image.new("RGBA", (64, 128), (92, 64, 44, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([4, 4, 59, 123], outline=(60, 40, 28), width=3)
    d.rectangle([12, 14, 51, 60], fill=(70, 86, 100))
    d.rectangle([44, 70, 50, 74], fill=(200, 200, 200))
    return im


def t_balcony():
    im = Image.new("RGBA", (128, 64), (176, 172, 162, 255))
    noise(im, 6, 5)
    d = ImageDraw.Draw(im)
    for x in range(0, 128, 8):
        d.line([(x, 0), (x, 63)], fill=(150, 146, 138), width=1)
    d.rectangle([0, 0, 127, 5], fill=(120, 116, 110))
    return im


def sign(text, w=512, h=128, bg=(29, 78, 216), fg=(255, 214, 10)):
    im = Image.new("RGBA", (w, h), bg + (255,))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(FONT, int(h * 0.62))
    f.set_variation_by_axes([600])
    bb = d.textbbox((0, 0), text, font=f)
    d.text(((w - (bb[2] - bb[0])) / 2 - bb[0], (h - (bb[3] - bb[1])) / 2 - bb[1]), text, font=f, fill=fg)
    d.rectangle([0, 0, w - 1, h - 1], outline=(255, 255, 255), width=3)
    return im


def t_kiosk():
    im = Image.new("RGBA", (128, 128), (232, 232, 228, 255))
    noise(im, 5, 6)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 127, 10], fill=(29, 78, 216))
    d.rectangle([0, 118, 127, 127], fill=(90, 90, 90))
    return im


def t_glass():
    im = Image.new("RGBA", (64, 64), (120, 150, 170, 150))
    d = ImageDraw.Draw(im)
    d.polygon([(6, 60), (40, 4), (48, 4), (14, 60)], fill=(170, 195, 210, 150))
    return im


def t_metal():
    im = Image.new("RGBA", (64, 64), (70, 110, 80, 255))
    noise(im, 6, 7)
    return im


def t_shopwin():
    im = Image.new("RGBA", (128, 128), (60, 76, 92, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 127, 127], outline=(220, 220, 220), width=6)
    d.line([(64, 0), (64, 127)], fill=(220, 220, 220), width=4)
    d.polygon([(10, 120), (50, 8), (60, 8), (20, 120)], fill=(88, 108, 126))
    return im


# ===================================================================== моделі
def khrushchyovka(name="bz_khrush"):
    """панельна п'ятиповерхівка: 4 під'їзди, 48 x 12 м, 5 поверхів."""
    m = Mesh(name)
    L, D, FL, N = 48.0, 12.0, 2.8, 5
    H = FL * N
    x0, x1, y0, y1 = -L / 2, L / 2, -D / 2, D / 2
    # цоколь
    m.box(x0 - 0.2, y0 - 0.2, -1.0, x1 + 0.2, y1 + 0.2, 0.6, "bz_concrete", (2, 2), top=False)
    # стіни з вікнами (UV: 1 повтор = 3 м по горизонталі і 2.8 м по вертикалі)
    m.box(x0, y0, 0.6, x1, y1, 0.6 + H, "bz_panel", (3.0, FL), top="bz_roof",
          sides={"px": "bz_panel_b", "nx": "bz_panel_b"})
    # парапет
    m.box(x0, y0, 0.6 + H, x1, y1, 0.6 + H + 0.5, "bz_concrete", (2, 2), top="bz_roof")
    # під'їзди (фасад -Y) + козирки
    for i in range(4):
        cx = x0 + 6.0 + i * 12.0
        m.plane_y(cx - 0.9, cx + 0.9, 0.6, 2.8, y0 - 0.02, "bz_door", facing=-1)
        m.box(cx - 1.6, y0 - 1.6, 2.9, cx + 1.6, y0, 3.15, "bz_concrete", (2, 2))
        m.box(cx - 1.4, y0 - 1.5, 0.0, cx + 1.4, y0, 0.6, "bz_concrete", (2, 2))   # ґанок
    # балкони на задньому фасаді (+Y), поверхи 2..5
    for f in range(1, N):
        z = 0.6 + f * FL
        for i in range(16):
            if (i + f) % 3 == 0:
                continue
            bx = x0 + 1.5 + i * 3.0
            m.box(bx - 1.2, y1, z - 0.05, bx + 1.2, y1 + 1.1, z + 0.12, "bz_concrete", (2, 2))
            m.box(bx - 1.2, y1 + 1.0, z + 0.12, bx + 1.2, y1 + 1.1, z + 1.1, "bz_balcony", (2.4, 1.0), top="bz_concrete", collide=False)
    return m


def kiosk(name="bz_kiosk", text="ПРОДУКТИ"):
    m = Mesh(name)
    w, d, h = 4.0, 3.0, 2.8
    m.box(-w / 2, -d / 2, 0, w / 2, d / 2, h, "bz_kiosk", (4.0, 2.8), top="bz_roof")
    m.box(-w / 2 - 0.3, -d / 2 - 0.6, h, w / 2 + 0.3, d / 2 + 0.2, h + 0.15, "bz_metal", (2, 2))   # дах-козирок
    m.plane_y(-w / 2 + 0.2, w / 2 - 0.2, 0.9, 2.3, -d / 2 - 0.02, "bz_shopwin", facing=-1)
    m.plane_y(-w / 2 - 0.3, w / 2 + 0.3, h + 0.15, h + 0.85, -d / 2 - 0.62, "bz_sign_" + name[3:], facing=-1)
    m.boxes.append(((-w / 2 - 0.3, -d / 2 - 0.62, h + 0.15), (w / 2 + 0.3, -d / 2 - 0.58, h + 0.85)))
    return m


def busstop(name="bz_stop"):
    m = Mesh(name)
    w, d, h = 5.0, 1.8, 2.6
    for x in (-w / 2 + 0.1, w / 2 - 0.2):
        for y in (-d / 2 + 0.1, d / 2 - 0.2):
            m.box(x, y, 0, x + 0.1, y + 0.1, h, "bz_metal", (1, 1), top=False)
    m.box(-w / 2, -d / 2, h, w / 2, d / 2, h + 0.12, "bz_metal", (2, 2))
    m.box(-w / 2 + 0.1, d / 2 - 0.12, 0.3, w / 2 - 0.1, d / 2 - 0.08, h - 0.1, "bz_glass", (2.0, 2.0), top=False)
    m.box(-w / 2 + 0.4, d / 2 - 0.7, 0.45, w / 2 - 0.4, d / 2 - 0.2, 0.55, "bz_metal", (2, 2))     # лавка
    m.plane_y(-1.2, 1.2, h + 0.12, h + 0.62, -d / 2 - 0.01, "bz_sign_stop", facing=-1)
    return m


def build(out):
    os.makedirs(out, exist_ok=True)
    models = [khrushchyovka(), kiosk("bz_kiosk", "ПРОДУКТИ"), busstop()]
    texs = [
        ("bz_panel", t_panel(), False), ("bz_panel_b", t_panel_blank(), False), ("bz_roof", t_roof(), False),
        ("bz_concrete", t_concrete(), False), ("bz_door", t_door(), False), ("bz_balcony", t_balcony(), False),
        ("bz_kiosk", t_kiosk(), False), ("bz_metal", t_metal(), False), ("bz_glass", t_glass(), True),
        ("bz_shopwin", t_shopwin(), False),
        ("bz_sign_kiosk", sign("ПРОДУКТИ"), False),
        ("bz_sign_stop", sign("ЗУПИНКА", 512, 128, (255, 255, 255), (20, 20, 20)), False),
    ]
    byname = {n: (n, im, a) for (n, im, a) in texs}
    for m in models:
        m.write(os.path.join(out, m.name + ".dff"))
        write_txd(os.path.join(out, m.name + ".txd"), [byname[t] for t in m.mats])
        print(m.name, "verts", len(m.v), "tris", len(m.tri), "boxes", len(m.boxes), "mats", m.mats)
    # прев'ю текстур
    sheet = Image.new("RGBA", (128 * 6, 128 * 2), (0, 0, 0, 255))
    for i, (n, im, a) in enumerate(texs):
        sheet.paste(im.resize((128, 128)), ((i % 6) * 128, (i // 6) * 128))
    sheet.save(os.path.join(out, "_textures.png"))


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "out")
