"""A preview of "Clearing", PureTV's start-up (IntroView.java), with the same timings, curves and layers.

    python tools/build_native_assets.py      (once)
    python tools/preview_intro.py            -> assets/preview/intro.gif and intro-1..4.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import preview_native as pn  # noqa: E402

OUT = ROOT / "assets" / "preview"
W, H = 960, 540
LENGTH = 1.7
FPS = 30


def bezier(p1x, p1y, p2x, p2y):
    """CSS/Android cubic-bezier easing, solved numerically."""
    def f(x):
        lo, hi = 0.0, 1.0
        for _ in range(30):
            m = (lo + hi) / 2
            bx = 3 * (1 - m) ** 2 * m * p1x + 3 * (1 - m) * m * m * p2x + m ** 3
            lo, hi = (m, hi) if bx < x else (lo, m)
        m = (lo + hi) / 2
        return 3 * (1 - m) ** 2 * m * p1y + 3 * (1 - m) * m * m * p2y + m ** 3
    return f


EASE, INOUT = bezier(.22, 0, 0, 1), bezier(.45, 0, .25, 1)
UI_EASE = bezier(.2, .8, .2, 1)


def span(t, a, b):
    return max(0.0, min(1.0, (t - a) / (b - a)))


def layers(sid):
    s = pn.Scene(sid)
    bare = pn.scene_layer(s).convert("RGB").resize((W, H), Image.LANCZOS)          # photo, glass clock, scenery
    home = pn.home(s, focus=1).convert("RGB").resize((W, H), Image.LANCZOS)        # + date and the apps
    frost = Image.open(ROOT / "launcher" / "assets" / "wall" / f"{sid}-glass.jpg").convert("RGB").resize((W, H), Image.BICUBIC)
    return bare, home, frost


def frame(ms, bare, home, frost, font):
    t = ms / (LENGTH * 1000)
    wake, open_, clear = EASE(span(t, 0, .18)), INOUT(span(t, .15, .44)), INOUT(span(t, .44, 1))
    word_in, word_out = EASE(span(t, .26, .44)), EASE(span(t, .46, .66))
    # underneath: Home assembling (the date and apps rise in from the moment clearing starts)
    since = max(0.0, ms - .44 * LENGTH * 1000)
    rise = UI_EASE(span(since, 260, 260 + 820))
    under = Image.blend(bare, home, rise)
    if rise < 1:   # the apps travel up 70 design px (35 here) as they appear
        dy = int(35 * (1 - rise))
        shifted = Image.blend(bare, home, rise)
        under = bare.copy()
        band = shifted.crop((0, 300, W, H))
        under.paste(band, (0, 300 + dy))
        top = Image.blend(bare, home, UI_EASE(span(since, 120, 820))).crop((0, 0, W, 300))
        under.paste(top, (0, 0))
    img = under.copy()
    if clear < 1:
        img = Image.blend(frost, under, clear)
    cy, half = H / 2, (H / 2) * open_
    d = ImageDraw.Draw(img, "RGBA")
    if open_ < 1:
        d.rectangle((0, 0, W, cy - half), fill=(0, 0, 0))
        d.rectangle((0, cy + half, W, H), fill=(0, 0, 0))
        if half < 1:
            d.rectangle((0, cy - 1, W, cy + 1), fill=(0, 0, 0))
        lw, a = W * (.12 + .88 * wake), (1 - open_ * .85)
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        g = ImageDraw.Draw(glow)
        for y in ([cy - half] + ([cy + half] if half > 1 else [])):
            for i, al in enumerate((1, .28, .1)):
                th = 1.5 * .5 + i * 2
                g.rounded_rectangle(((W - lw) / 2, y - th / 2, (W + lw) / 2, y + th / 2), th, fill=(255, 255, 255, int(255 * al * a * min(1, wake * 1.4))))
        img = img.convert("RGBA")
        img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(.6)))
        img = img.convert("RGB")
    wa = word_in * (1 - word_out)
    if wa > 0:
        layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(layer).text((W / 2, cy + 9 - 5 * word_out), "PureTV", font=font, fill=(255, 255, 255, int(235 * wa)), anchor="ms")
        img = img.convert("RGBA")
        img.alpha_composite(layer)
        img = img.convert("RGB")
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    bare, home, frost = layers("ossau")
    font = ImageFont.truetype(str(ROOT / "launcher" / "assets" / "fonts" / "inter_semibold.ttf"), 27)
    n = int(LENGTH * FPS)
    frames = [frame(i * 1000 / FPS, bare, home, frost, font) for i in range(n + 1)]
    hold = [frames[-1]] * 20
    frames[0].save(OUT / "intro.gif", save_all=True, append_images=frames[1:] + hold, duration=int(1000 / FPS), loop=0, optimize=True)
    for k, ms in enumerate((300, 700, 1050, 1700), 1):
        frame(ms, bare, home, frost, font).save(OUT / f"intro-{k}.png")
    print(OUT / "intro.gif")


if __name__ == "__main__":
    main()
