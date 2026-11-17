"""The PureTV mark (same geometry as launcher Mark.java): app icon and TV banner, and the intro preview.

    python tools/make_brand.py      -> launcher/res/drawable-nodpi/icon.png, banner.png
                                       assets/preview/intro.gif and intro-1..4.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONT = ROOT / "launcher" / "assets" / "fonts" / "inter_semibold.ttf"
SS = 4   # drawn larger, then scaled down for smooth edges


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def mark(size, alpha=255):
    """The mark as an RGBA image of size x size."""
    S = size * SS
    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    grad = Image.new("RGBA", (S, S))
    px = grad.load()
    c0, c1 = (0x6C, 0x8C, 0xFF), (0x3A, 0x2F, 0xBF)
    for y in range(0, S, 2):
        for x in range(0, S, 2):
            col = lerp(c0, c1, (x + y) / (2 * S)) + (255,)
            px[x, y] = col
            if x + 1 < S: px[x + 1, y] = col
            if y + 1 < S: px[x, y + 1] = col
            if x + 1 < S and y + 1 < S: px[x + 1, y + 1] = col
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), int(S * .225), fill=255)
    base.paste(grad, (0, 0), mask)
    gloss = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gloss)
    for y in range(int(S * .55)):
        gd.line((0, y, S, y), fill=(255, 255, 255, int(51 * (1 - y / (S * .55)))))
    base.alpha_composite(Image.composite(gloss, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mask))
    d = ImageDraw.Draw(base)
    w = int(S * .055)   # a TV: the screen and its stand
    d.rounded_rectangle((S * .18, S * .24, S * .82, S * .68), int(S * .07), outline=(255, 255, 255, 255), width=w)
    d.line((S * .40, S * .80, S * .60, S * .80), fill=(255, 255, 255, 255), width=w)
    for x in (.40, .60):
        d.ellipse((S * x - w / 2, S * .80 - w / 2, S * x + w / 2, S * .80 + w / 2), fill=(255, 255, 255, 255))
    w2 = int(S * .045)  # on it, a calm landscape: one ridge and the sun
    pts = [(.28, .59), (.42, .43), (.50, .52), (.60, .39), (.72, .59)]
    d.line([(x * S, y * S) for x, y in pts], fill=(255, 255, 255, 255), width=w2, joint="curve")
    for x, y in (pts[0], pts[-1]):
        d.ellipse((x * S - w2 / 2, y * S - w2 / 2, x * S + w2 / 2, y * S + w2 / 2), fill=(255, 255, 255, 255))
    r = S * .042
    d.ellipse((S * .33 - r, S * .345 - r, S * .33 + r, S * .345 + r), fill=(255, 255, 255, 242))
    img = base.resize((size, size), Image.LANCZOS)
    if alpha < 255:
        a = img.getchannel("A").point(lambda v: v * alpha // 255)
        img.putalpha(a)
    return img


def icons():
    out = ROOT / "launcher" / "res" / "drawable-nodpi"
    out.mkdir(parents=True, exist_ok=True)
    mark(192).save(out / "icon.png")
    banner = Image.new("RGBA", (640, 360), (12, 13, 20, 255))
    m = mark(170)
    f = ImageFont.truetype(str(FONT), 68)
    tw = ImageDraw.Draw(banner).textlength("PureTV", font=f)
    x = int((640 - (170 + 34 + tw)) / 2)
    banner.alpha_composite(m, (x, 95))
    ImageDraw.Draw(banner).text((x + 170 + 34, 180), "PureTV", font=f, fill="white", anchor="lm")
    banner.save(out / "banner.png")
    for old in ("icon.xml", "banner.xml"):
        p = ROOT / "launcher" / "res" / "drawable" / old
        if p.exists():
            p.unlink()


# ---------------------------------------------------------------- the intro preview (IntroView.java timings)

def bezier(p1x, p1y, p2x, p2y):
    def f(x):
        lo, hi = 0.0, 1.0
        for _ in range(30):
            m = (lo + hi) / 2
            bx = 3 * (1 - m) ** 2 * m * p1x + 3 * (1 - m) * m * m * p2x + m ** 3
            lo, hi = (m, hi) if bx < x else (lo, m)
        m = (lo + hi) / 2
        return 3 * (1 - m) ** 2 * m * p1y + 3 * (1 - m) * m * m * p2y + m ** 3
    return f


OUT_E, INOUT, UI_EASE = bezier(.16, 1, .3, 1), bezier(.45, 0, .25, 1), bezier(.2, .8, .2, 1)


def span(t, a, b):
    return max(0.0, min(1.0, (t - a) / (b - a)))


def intro():
    W, H, K, LENGTH, FPS = 960, 540, .5, 1.9, 30
    home = Image.open(ROOT / "assets" / "preview" / "home.png").convert("RGB").resize((W, H), Image.LANCZOS)
    m_full = mark(int(200 * K * 1.1))
    f = ImageFont.truetype(str(FONT), int(64 * K))

    def frame(ms):
        t = ms / (LENGTH * 1000)
        inn, name, light, out = OUT_E(span(t, 0, .42)), OUT_E(span(t, .14, .5)), INOUT(span(t, .36, .66)), INOUT(span(t, .68, 1))
        rise = UI_EASE(span(ms - .68 * LENGTH * 1000, 120, 940))
        img = Image.blend(Image.new("RGB", (W, H)), home, rise * (1 - 0) if out > 0 else 0)
        img = Image.blend(Image.new("RGB", (W, H)), home, out)
        s = (.86 + .14 * inn) * (1 + .04 * out)
        size = int(200 * K * s)
        a = int(255 * min(1, inn * 1.4) * (1 - out))
        cx, cy = W / 2, H / 2 - 46 * K
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        if a > 0:
            m = m_full.resize((size, size), Image.LANCZOS)
            alpha = m.getchannel("A").point(lambda v: v * a // 255)
            m.putalpha(alpha)
            if 0 < light < 1:   # the soft highlight, clipped to the mark
                sh = Image.new("RGBA", (size, size), (0, 0, 0, 0))
                sd = ImageDraw.Draw(sh)
                x = -size + 3 * size * light
                for i in range(-int(size * .35), int(size * .35)):
                    v = int(89 * (1 - abs(i) / (size * .35)) * (1 - out))
                    sd.line((x + i - size * .3, size, x + i + size * .3, 0), fill=(255, 255, 255, v))
                sh.putalpha(Image.fromarray(__import__("numpy").minimum(__import__("numpy").array(sh.getchannel("A")), __import__("numpy").array(m.getchannel("A")))))
                m.alpha_composite(sh)
            layer.alpha_composite(m, (int(cx - size / 2), int(cy - size / 2)))
        ImageDraw.Draw(layer).text((cx, cy + 100 * K * s + 100 * K + 14 * K * (1 - name)), "PureTV", font=f,
                                   fill=(255, 255, 255, int(255 * name * (1 - out))), anchor="ms")
        img = img.convert("RGBA")
        img.alpha_composite(layer)
        return img.convert("RGB")

    n = int(LENGTH * FPS)
    frames = [frame(i * 1000 / FPS) for i in range(n + 1)]
    out = ROOT / "assets" / "preview"
    frames[0].save(out / "intro.gif", save_all=True, append_images=frames[1:] + [frames[-1]] * 20, duration=int(1000 / FPS), loop=0, optimize=True)
    for k, ms in enumerate((350, 900, 1200, 1900), 1):
        frame(ms).save(out / f"intro-{k}.png")
    print(out / "intro.gif")


if __name__ == "__main__":
    icons()
    if "--icons-only" not in sys.argv:
        intro()
