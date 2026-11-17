"""Preview images of the native PureTV Home, drawn with the launcher's own assets and layout numbers.

    python tools/build_native_assets.py      (once)
    python tools/preview_native.py           -> assets/preview/*.png (1920 x 1080)

The numbers here mirror launcher/src/tv/pure/home (MainActivity, SceneView, Glass, Tile, Row, Settings,
Setup, Menus), so the pictures show what the TV draws. They are renders, not screenshots of a TV.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "launcher" / "assets"
OUT = ROOT / "assets" / "preview"
S = 2                      # drawn at 2x, then scaled down for smooth edges
W, H = 1920 * S, 1080 * S
SCENES = {"ossau": (154, 400), "flatirons": (150, 352), "luna": (246, 400), "kathleen": (174, 400), "titicaca": (246, 400), "louise": (150, 400)}
EMOJI = "C:/Windows/Fonts/seguiemj.ttf"


def font(name, size):
    return ImageFont.truetype(str(A / "fonts" / f"{name}.ttf"), int(size * S))


def emoji_font(size):
    try:
        return ImageFont.truetype(EMOJI, int(size * S))
    except OSError:
        return None


def p(v):
    return int(round(v * S))


class Scene:
    def __init__(self, sid):
        self.id = sid
        self.top, self.size = SCENES[sid]
        self.photo = Image.open(A / "wall" / f"{sid}.jpg").convert("RGB").resize((W, H), Image.LANCZOS)
        self.fg = Image.open(A / "wall" / f"{sid}-fg.webp").convert("RGBA").resize((W, H), Image.LANCZOS)
        self.glass = Image.open(A / "wall" / f"{sid}-glass.jpg").convert("RGB").resize((W, H), Image.BICUBIC)


def mask_round(size, r):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, fill=255)
    return m


def shadow(canvas, box, r, blur, alpha, dy=0):
    x0, y0, x1, y1 = box
    pad = blur * 3
    sh = Image.new("RGBA", (x1 - x0 + 2 * pad, y1 - y0 + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((pad, pad, pad + x1 - x0, pad + y1 - y0), r, fill=(0, 0, 0, alpha))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(sh, (x0 - pad, y0 - pad + dy))


def gradient(size, c0, c1, diagonal=False):
    """A two-colour RGBA gradient, top to bottom (or top-left to bottom-right)."""
    w, h = size
    g = Image.new("RGBA", size)
    px = g.load()
    for y in range(h):
        for x in range(0, w, 4 if diagonal else w):
            t = ((x / max(w, 1)) * .4 + y / max(h, 1)) / 1.4 if diagonal else y / max(h - 1, 1)
            col = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(4))
            if diagonal:
                for xx in range(x, min(x + 4, w)):
                    px[xx, y] = col
            else:
                for xx in range(w):
                    px[xx, y] = col
    return g


def glass(canvas, scene, box, r, style="liquid", dim=0.0):
    """Glass.java: the wallpaper's pre-blurred slice, a tint, light across the top and a bright edge."""
    x0, y0, x1, y1 = box
    size = (x1 - x0, y1 - y0)
    m = mask_round(size, r)
    sl = scene.glass.crop(box).convert("RGBA")
    if dim:
        sl = ImageEnhance.Brightness(sl).enhance(1 - dim)
    tint = {"liquid": (24, 24, 32, 56), "frosted": (70, 72, 84, 97), "dark": (14, 14, 18, 204)}[style]
    sl.alpha_composite(Image.new("RGBA", size, tint))
    if style != "dark":
        sl.alpha_composite(gradient(size, (255, 255, 255, 56 if style == "liquid" else 87), (255, 255, 255, 15 if style == "liquid" else 46), diagonal=True))
    edge = gradient(size, (255, 255, 255, 140), (255, 255, 255, 36))
    em = Image.new("L", size, 0)
    ImageDraw.Draw(em).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), r, outline=255, width=max(2, p(1.5)))
    sl.alpha_composite(Image.composite(edge, Image.new("RGBA", size, (0, 0, 0, 0)), em))
    canvas.paste(sl, (x0, y0), m)


def scene_layer(scene, time="7:42", darken=0.0):
    """SceneView.java: photo, glass digits made of the blurred wallpaper with a rim and soft shadow, then the scenery."""
    img = scene.photo.convert("RGBA")
    f = font("antonio_light", scene.size)
    tm = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(tm)
    base = p(scene.top) + int(scene.size * S * .9175)
    d.text((W // 2, base), time, font=f, fill=255, anchor="ms")
    # shadow under the rim fill
    sh = Image.new("RGBA", (W, H), (0, 26, 45, 0))
    sh.putalpha(tm.point(lambda v: int(v * .5)))
    sh = sh.filter(ImageFilter.GaussianBlur(p(13)))
    img.alpha_composite(sh, (0, p(4)))
    fill = ImageEnhance.Color(ImageEnhance.Brightness(scene.glass).enhance(1.3)).enhance(1.15).convert("RGBA")
    fill.putalpha(tm.point(lambda v: int(v * .92)))
    img.alpha_composite(fill)
    white = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    white.putalpha(tm.point(lambda v: int(v * .5)))
    img.alpha_composite(white)
    stroke = Image.new("L", (W, H), 0)
    ImageDraw.Draw(stroke).text((W // 2, base), time, font=f, fill=255, anchor="ms", stroke_width=max(2, p(1.25)), stroke_fill=255)
    rim = ImageChops.subtract(stroke, tm.filter(ImageFilter.MinFilter(3)))
    edge = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    edge.putalpha(rim.point(lambda v: int(v * .9)))
    img.alpha_composite(edge)
    img.alpha_composite(scene.fg)
    if darken:
        img.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(darken * 150))))
    return img


def date_block(img, scene, greet="Good evening, Advik", line="Tuesday 17 November   ☁︎ 24° Cloudy"):
    d = ImageDraw.Draw(img)
    g, t = font("inter_semibold", 22), font("inter_semibold", 34)
    bottom = p(scene.top + scene.size * .0425 - 22)
    h = p(22 + 8 + 34)
    top = max(bottom - h, p(64))
    for txt, f, y, a in ((greet, g, top, 217), (line.replace("☁︎", "").replace("  ", " "), t, top + p(30), 255)):
        sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((W // 2, y + p(1)), txt, font=f, fill=(0, 0, 0, 102), anchor="mt")
        img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(p(5))))
        d.text((W // 2, y), txt, font=f, fill=(255, 255, 255, a), anchor="mt")


ART = {p_.stem: p_ for p_ in (A / "art").glob("*.jpg")}


def tile_face(pkg, sys_, w, h):
    face = Image.new("RGBA", (w, h))
    if pkg and pkg in ART:
        art = Image.open(ART[pkg]).convert("RGBA").resize((w, h), Image.LANCZOS)
        face = art
    elif pkg:   # an app without PureTV artwork: its own banner (stand-in colour here)
        face = gradient((w, h), (60, 64, 76, 255), (30, 32, 40, 255))
        ImageDraw.Draw(face).text((w // 2, h // 2), pkg, font=font("inter_heavy", 30), fill="white", anchor="mm")
    else:
        top, bot = {"settings": ((228, 230, 234, 255), (169, 173, 181, 255)), "parents": ((255, 211, 106, 255), (240, 160, 32, 255))}.get(sys_, ((154, 160, 171, 255), (95, 101, 113, 255)))
        face = gradient((w, h), top, bot)
        if sys_ == "settings":
            gear(face, w // 2, h // 2, int(h * .5 * .62))
        else:
            icon(face, {"parents": "lock"}.get(sys_, sys_), w // 2 - h // 4, h // 2 - h // 4, h // 2)
    return face


def tile(canvas, x, y, w, h, pkg=None, sys_=None, label="", focused=False, kids=False):
    """Tile.java: a 5:3 face with rounded corners; focused tiles lift (x1.12), get a soft shadow, a gloss and the name."""
    face = tile_face(pkg, sys_, w, h)
    r = p(20)
    if kids:
        d = ImageDraw.Draw(face)
        bw, bh = p(70), p(28)
        bx, by = w - bw - p(10), h - bh - p(9)
        d.rounded_rectangle((bx, by, bx + bw, by + bh), bh // 2, fill=(255, 214, 10))
        d.text((bx + bw // 2, by + bh // 2), "Kids", font=font("inter_semibold", 15), fill=(26, 20, 0), anchor="mm")
    if focused:
        face.alpha_composite(gradient((w, h), (255, 255, 255, 51), (255, 255, 255, 0)).crop((0, 0, w, h)))
        k = 1.12
        fw, fh = int(w * k), int(h * k)
        face = face.resize((fw, fh), Image.LANCZOS)
        fx, fy = x - (fw - w) // 2, y - (fh - h) // 2
        shadow(canvas, (fx, fy, fx + fw, fy + fh), int(r * k), p(18), 140, dy=p(14))
        canvas.paste(face, (fx, fy), mask_round((fw, fh), int(r * k)))
        sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(sh).text((x + w // 2, y + h + p(45)), label, font=font("inter_semibold", 22), fill=(0, 0, 0, 128), anchor="ms")
        canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(p(3))))
        ImageDraw.Draw(canvas).text((x + w // 2, y + h + p(44)), label, font=font("inter_semibold", 22), fill="white", anchor="ms")
    else:
        shadow(canvas, (x, y, x + w, y + h), r, p(4), 70, dy=p(2))
        canvas.paste(face, (x, y), mask_round((w, h), r))


def gear(img, cx, cy, r):
    import math
    d = ImageDraw.Draw(img)
    pts = []
    n = 36
    for i in range(n):
        a0 = 2 * math.pi * i / n
        for a, rr in ((a0, r), (a0 + math.pi / n * .6, r), (a0 + math.pi / n, r * .9), (a0 + math.pi / n * 1.6, r * .9)):
            pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=(80, 84, 92))
    d.ellipse((cx - r * .745, cy - r * .745, cx + r * .745, cy + r * .745), fill=(214, 217, 222))
    w = max(2, int(r * .093))
    d.ellipse((cx - r * .58, cy - r * .58, cx + r * .58, cy + r * .58), outline=(74, 78, 86), width=w)
    for a in (-90, 30, 150):
        t = math.radians(a)
        d.line((cx, cy, cx + math.cos(t) * r * .56, cy + math.sin(t) * r * .56), fill=(74, 78, 86), width=int(r * .115))
    d.ellipse((cx - r * .18, cy - r * .18, cx + r * .18, cy + r * .18), fill=(74, 78, 86))


def icon(img, name, x, y, s, color=(255, 255, 255)):
    """A few of Icons.java's line icons."""
    d = ImageDraw.Draw(img)
    u = s / 24
    w = max(2, int(s * .085))
    P = lambda a, b: (x + a * u, y + b * u)
    if name == "home":
        for ox, oy in ((4, 4), (13, 4), (4, 13), (13, 13)):
            d.rounded_rectangle((*P(ox, oy), *P(ox + 7, oy + 7)), int(2 * u), fill=color)
    elif name == "appearance":
        d.ellipse((*P(3.5, 3.5), *P(20.5, 20.5)), outline=color, width=w)
        d.pieslice((*P(3.5, 3.5), *P(20.5, 20.5)), -90, 90, fill=color)
    elif name == "wallpaper":
        d.rounded_rectangle((*P(3, 5), *P(21, 19)), int(2.5 * u), outline=color, width=w)
        d.line((P(5, 17), P(10, 11), P(13.5, 15), P(15.5, 13), P(19, 17)), fill=color, width=w, joint="curve")
        d.ellipse((*P(14.4, 7.4), *P(17.6, 10.6)), fill=color)
    elif name == "weather":
        d.ellipse((*P(4.5, 9.5), *P(13.5, 18.5)), fill=color)
        d.ellipse((*P(9, 6), *P(20, 17)), fill=color)
        d.rounded_rectangle((*P(4.5, 13), *P(20, 18.5)), int(2.75 * u), fill=color)
    elif name in ("display", "inputs", "googletv"):
        d.rounded_rectangle((*P(3, 5), *P(21, 16)), int(2 * u), outline=color, width=w)
        d.line((P(8, 20), P(16, 20)), fill=color, width=w)
    elif name == "updates":
        d.line((P(12, 4), P(12, 15)), fill=color, width=w)
        d.line((P(7, 10.5), P(12, 15.5), P(17, 10.5)), fill=color, width=w, joint="curve")
        d.line((P(5, 20), P(19, 20)), fill=color, width=w)
    elif name == "about":
        d.ellipse((*P(2.5, 2.5), *P(21.5, 21.5)), outline=color, width=w)
        d.line((P(12, 11), P(12, 17)), fill=color, width=w)
        d.ellipse((*P(10.8, 6.4), *P(13.2, 8.8)), fill=color)
    elif name == "lock":
        d.rounded_rectangle((*P(5, 10), *P(19, 21)), int(1.5 * u), fill=color)
        d.arc((*P(7.5, 3), *P(16.5, 15)), 180, 360, fill=color, width=w)
    elif name == "search":
        d.ellipse((*P(4, 4), *P(17, 17)), outline=color, width=w)
        d.line((P(15.3, 15.3), P(20, 20)), fill=color, width=w)


def row(canvas, x, y, w, title, sub=None, value=None, focused=False, check=False, chev=False, emoji=None, emoji_bg=None, ic=None, ic_bg=None, center=False):
    """Row.java: glass-light rows that turn white when focused (and grow a touch)."""
    h = p(76 if not sub else 92)
    if focused:
        k = 1.02
        dx, dy = int(w * (k - 1) / 2), int(h * (k - 1) / 2)
        shadow(canvas, (x - dx, y - dy, x + w + dx, y + h + dy), p(22), p(10), 90, dy=p(6))
        layer = Image.new("RGBA", (w + 2 * dx, h + 2 * dy), (255, 255, 255, 242))
        canvas.paste(layer, (x - dx, y - dy), mask_round(layer.size, p(22)))
    else:
        layer = Image.new("RGBA", (w, h), (255, 255, 255, 23))
        canvas.alpha_composite(Image.composite(layer, Image.new("RGBA", (w, h), (0, 0, 0, 0)), mask_round((w, h), p(22))), (x, y))
    d = ImageDraw.Draw(canvas)
    tx = x + p(28)
    if emoji:
        cx, cy = tx + p(22), y + h // 2
        d.ellipse((cx - p(22), cy - p(22), cx + p(22), cy + p(22)), fill=emoji_bg)
        ef = emoji_font(24)
        if ef:
            d.text((cx, cy + p(1)), emoji, font=ef, embedded_color=True, anchor="mm")
        tx += p(64)
    if ic:
        bx, by = tx, y + h // 2 - p(22)
        sq = gradient((p(44), p(44)), ic_bg[0], ic_bg[1])
        canvas.paste(sq, (bx, by), mask_round((p(44), p(44)), p(11)))
        icon(canvas, ic, bx + p(9), by + p(9), p(26))
        tx += p(64)
    col = (28, 28, 30) if focused else (255, 255, 255)
    col2 = (28, 28, 30, 179) if focused else (255, 255, 255, 179)
    tf, sf, vf = font("inter_semibold", 28), font("inter_regular", 19), font("inter_regular", 26)
    if center:
        d.text((x + w // 2, y + (p(22) if sub else h // 2)), title, font=tf, fill=col, anchor="mt" if sub else "mm")
        if sub:
            d.text((x + w // 2, y + p(58)), sub, font=sf, fill=col2, anchor="mt")
        return h
    if sub:
        d.text((tx, y + p(18)), title, font=tf, fill=col, anchor="lt")
        d.text((tx, y + p(56)), sub, font=sf, fill=col2, anchor="lt")
    else:
        d.text((tx, y + h // 2), title, font=tf, fill=col, anchor="lm")
    rx = x + w - p(28)
    if check or chev:
        mark = (10, 132, 255) if check and not focused else col
        if check:
            d.line(((rx - p(24), y + h // 2), (rx - p(16), y + h // 2 + p(8)), (rx, y + h // 2 - p(10))), fill=mark, width=p(3), joint="curve")
        else:
            d.line(((rx - p(10), y + h // 2 - p(12)), (rx, y + h // 2), (rx - p(10), y + h // 2 + p(12))), fill=col2, width=p(3), joint="curve")
        rx -= p(44)
    if value:
        d.text((rx, y + h // 2), value, font=vf, fill=col2, anchor="rm")
    return h


APPS = ["com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.apple.atve.androidtv.appletv"]
NAMES = {"com.google.android.youtube.tv": "YouTube", "com.netflix.ninja": "Netflix", "com.amazon.amazonvideo.livingroom": "Prime Video",
         "com.disney.disneyplus": "Disney+", "com.apple.atve.androidtv.appletv": "Apple TV", "com.spotify.tv.android": "Spotify",
         "com.wbd.stream": "Max", "com.cbs.ca": "Paramount+", "com.crunchyroll.webapp": "Crunchyroll", "tv.twitch.android.app": "Twitch", "dazn": "DAZN"}


def geometry(dock=5):
    cols = dock + 1
    gap, tw = 36, (1792 - 36 * (cols - 1)) / cols
    th = tw * .6
    band_h = th + 52
    band_top = 1080 - 64 - band_h
    return cols, gap, tw, th, band_h, band_top, band_top + band_h + 58


def home(scene, focus=1, deep=False, kids=False):
    cols, gap, tw, th, band_h, band_top, grid_top = geometry()
    shift = -(grid_top - 330) if deep else 0
    img = scene_layer(scene, darken=1.0 if deep else 0)
    if not deep:
        date_block(img, scene)
    dy = p(shift)
    glass(img, scene, (p(32), p(band_top) + dy, p(32 + 1856), p(band_top + band_h) + dy), p(46))
    row1 = ([("com.google.android.youtube.tv", None, "YouTube Kids"), ("com.netflix.ninja", None, "Netflix"), ("com.disney.disneyplus", None, "Disney+"),
             ("com.amazon.amazonvideo.livingroom", None, "Prime Video"), (None, "parents", "Parents")] if kids else
            [(a, None, NAMES[a]) for a in APPS] + [(None, "settings", "Settings")])
    tiles = []
    for i, (pk, sy, lab) in enumerate(row1):
        tiles.append((p(64 + i * (tw + gap)), p(band_top + 26) + dy, pk, sy, lab, kids and pk is not None))
    if not kids:
        rest = ["com.spotify.tv.android", "com.wbd.stream", "com.cbs.ca", "com.crunchyroll.webapp", "tv.twitch.android.app", "dazn"]
        items = [(a, None, NAMES[a]) for a in rest] + [(None, "search", "Search"), (None, "inputs", "Inputs")]
        for i, (pk, sy, lab) in enumerate(items):
            r, c = divmod(i, cols)
            tiles.append((p(64 + c * (tw + gap)), p(grid_top + r * (th + 74)) + dy, pk, sy, lab, False))
    order = [t for i, t in enumerate(tiles) if i != focus] + [tiles[focus]]
    for i, (x, y, pk, sy, lab, kb) in enumerate(order):
        if y > H + p(50) or y + p(th) < -p(50):
            continue
        tile(img, x, y, p(tw), p(th), pk, sy, lab, focused=(i == len(order) - 1), kids=kb)
    return img


def settings(scene):
    img = scene_layer(scene, darken=.55)
    img.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, 77)))
    glass(img, scene, (p(48), p(48), p(48 + 560), p(1080 - 48)), p(44), dim=.35)
    x, y, w = p(72), p(78), p(512)
    y += row(img, x, y, w, "Advik's PureTV", sub="Profiles and Parent PIN", emoji="🦊", emoji_bg=(255, 159, 10)) + p(24)
    side = [("Home Screen", "home", ((90, 168, 255, 255), (28, 100, 214, 255))), ("Appearance", "appearance", ((127, 216, 255, 255), (42, 164, 230, 255))),
            ("Wallpaper", "wallpaper", ((95, 224, 200, 255), (26, 165, 142, 255))), ("Weather", "weather", ((111, 195, 255, 255), (42, 134, 230, 255))), None,
            ("TV Settings", "display", ((125, 134, 255, 255), (75, 79, 224, 255))), ("Google TV", "googletv", ((142, 152, 171, 255), (79, 88, 105, 255))),
            ("Software Updates", "updates", ((154, 160, 171, 255), (95, 101, 113, 255))), ("About", "about", ((154, 160, 171, 255), (95, 101, 113, 255)))]
    for s in side:
        if s is None:
            y += p(18)
            continue
        y += row(img, x, y, w, s[0], ic=s[1], ic_bg=s[2], focused=s[0] == "Home Screen") + p(6)
    d = ImageDraw.Draw(img)
    px, py, pw = p(680), p(60), p(1160)
    d.text((px + p(14), py), "Home Screen", font=font("inter_heavy", 52), fill="white", anchor="lt")
    py += p(86)

    def head(t):
        nonlocal py
        d.text((px + p(14), py + p(26)), t, font=font("inter_semibold", 20), fill=(255, 255, 255, 128), anchor="lt")
        py += p(60)
    head("Apps")
    for t, v in (("Apps in the Top Row", "5"), ("Move or Hide Apps", "Hold OK on an app"), ("Hidden Apps", "None")):
        py += row(img, px, py, pw, t, value=v) + p(10)
    head("Home Button")
    py += row(img, px, py, pw, "Home Button Opens PureTV", sub="PureTV is this TV's only Home screen", value="Yes") + p(10)
    head("Setup")
    row(img, px, py, pw, "Run Setup Again")
    return img


def setup(scene):
    bg = ImageEnhance.Brightness(scene.glass).enhance(.36).convert("RGBA")
    d = ImageDraw.Draw(bg)
    d.text((W // 2, p(330)), "Welcome", font=font("inter_heavy", 150), fill="white", anchor="mt")
    d.text((W // 2, p(540)), "Welcome to PureTV for your TV.", font=font("inter_regular", 26), fill=(255, 255, 255, 204), anchor="mt")
    row(bg, W // 2 - p(310), p(604), p(620), "Get Started", sub="Set up your Home screen", focused=True, center=True)
    f20 = font("inter_regular", 20)
    left, right = "Made with ", " by Crazified Studios"
    wl, wr, wh = d.textlength(left, font=f20), d.textlength(right, font=f20), p(24)
    x = W // 2 - (wl + wh + wr) / 2
    d.text((x, p(800)), left, font=f20, fill=(255, 255, 255, 153), anchor="lt")
    ef = emoji_font(17)
    if ef:
        d.text((x + wl + wh / 2, p(800) + p(11)), "❤️", font=ef, embedded_color=True, anchor="mm")
    d.text((x + wl + wh, p(800)), right, font=f20, fill=(255, 255, 255, 153), anchor="lt")
    return bg


def wallpapers(scene):
    bg = ImageEnhance.Brightness(scene.glass).enhance(.36).convert("RGBA")
    d = ImageDraw.Draw(bg)
    d.text((W // 2, p(300)), "Choose a Wallpaper", font=font("inter_heavy", 64), fill="white", anchor="mt")
    d.text((W // 2, p(392)), "Your clock sits behind the scenery. Wallpapers change every 20 seconds.", font=font("inter_regular", 26), fill=(255, 255, 255, 204), anchor="mt")
    ids = list(SCENES)
    tw, th, gap = 256, 144, 26
    x0 = (1920 - (len(ids) * tw + (len(ids) - 1) * gap)) / 2
    for i, sid in enumerate(ids):
        t = Image.open(A / "wall" / f"{sid}-t.jpg").convert("RGBA").resize((p(tw), p(th)), Image.LANCZOS)
        x, y = p(x0 + i * (tw + gap)), p(480)
        focused = sid == scene.id
        if focused:
            k = 1.12
            t = t.resize((int(t.width * k), int(t.height * k)), Image.LANCZOS)
            x -= (t.width - p(tw)) // 2
            y -= (t.height - p(th)) // 2
            shadow(bg, (x, y, x + t.width, y + t.height), p(26), p(16), 150, dy=p(10))
        bg.paste(t, (x, y), mask_round(t.size, p(24)))
        names = {"ossau": "Ossau", "flatirons": "Flatirons", "luna": "Valle de la Luna", "kathleen": "Kathleen Lake", "titicaca": "Titicaca Sunrise", "louise": "Lake Louise"}
        d.text((p(x0 + i * (tw + gap) + tw / 2), p(480 + th + 34)), names[sid], font=font("inter_semibold", 20), fill=(255, 255, 255, 255 if focused else 204), anchor="mt")
    for i in range(4):
        c = (255, 255, 255, 255) if i == 2 else (255, 255, 255, 89)
        r = p(5 if i == 2 else 4)
        cx = W // 2 + p((i - 1.5) * 22)
        d.ellipse((cx - r, p(1020) - r, cx + r, p(1020) + r), fill=c)
    return bg


def app_menu(scene):
    img = home(scene, focus=1)
    img.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, 105)))
    cw = p(560)
    rows_ = [("Move Left", None), ("Move Right", None), ("Move to All Apps", None), ("Hide from Home", None), ("App Info", "The TV's own page for this app")]
    ch = p(28 + 50 + 26) + sum(p(76 if not s else 92) + p(10) for _, s in rows_)
    x, y = W // 2 - cw // 2, H // 2 - ch // 2
    shadow(img, (x, y, x + cw, y + ch), p(40), p(30), 150, dy=p(16))
    glass(img, scene, (x, y, x + cw, y + ch), p(40))
    d = ImageDraw.Draw(img)
    d.text((W // 2, y + p(28)), "Netflix", font=font("inter_heavy", 32), fill="white", anchor="mt")
    yy = y + p(28 + 50)
    for i, (t, s) in enumerate(rows_):
        yy += p(10)
        yy += row(img, x + p(30), yy, cw - p(60), t, sub=s, focused=i == 2)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    shots = {
        "home": lambda: home(Scene("ossau"), focus=1),
        "home-luna": lambda: home(Scene("luna"), focus=0),
        "all-apps": lambda: home(Scene("kathleen"), focus=7, deep=True),
        "settings": lambda: settings(Scene("louise")),
        "setup": lambda: setup(Scene("ossau")),
        "wallpapers": lambda: wallpapers(Scene("titicaca")),
        "app-menu": lambda: app_menu(Scene("flatirons")),
        "kids": lambda: home(Scene("titicaca"), focus=0, kids=True),
    }
    only = sys.argv[1:]
    for name, make in shots.items():
        if only and name not in only:
            continue
        img = make().convert("RGB").resize((1920, 1080), Image.LANCZOS)
        img.save(OUT / f"{name}.png", optimize=True)
        print(OUT / f"{name}.png")


if __name__ == "__main__":
    main()
