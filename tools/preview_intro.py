"""An animated preview of PureTV's start-up (IntroView.java), drawn with the same timings and shapes.

    python tools/preview_intro.py     -> assets/preview/intro.gif (960 x 540) and intro-frame.png
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONTS = ROOT / "launcher" / "assets" / "fonts"
OUT = ROOT / "assets" / "preview"
W, H, K = 960, 540, 0.5                       # half of the 1920 x 1080 design grid
SPECTRUM = [(0x7F, 0xD8, 0xFF), (0xA9, 0xA2, 0xFF), (0xF5, 0x9B, 0xD0), (0xFF, 0xD0, 0x8A), (0x9A, 0xF0, 0xC8), (0x7F, 0xD8, 0xFF)]


def soft(x):   # cubic-bezier(.3, 0, .2, 1), close enough for a preview
    return x * x * (3 - 2 * x)


def span(t, a, b):
    return max(0.0, min(1.0, (t - a) / (b - a)))


def sweep(size, cx, cy, turn):
    """A colour wheel around (cx, cy): the same idea as Android's SweepGradient."""
    yy, xx = np.mgrid[0:size[1], 0:size[0]]
    ang = (np.degrees(np.arctan2(yy - cy, xx - cx)) - turn) % 360 / 360 * (len(SPECTRUM) - 1)
    i = np.floor(ang).astype(int)
    f = (ang - i)[..., None]
    c0, c1 = np.array(SPECTRUM)[i], np.array(SPECTRUM)[np.minimum(i + 1, len(SPECTRUM) - 1)]
    return Image.fromarray((c0 * (1 - f) + c1 * f).astype(np.uint8), "RGB")


def ring_mask(size, box, r, width, blur=0):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(box, r, outline=255, width=width)
    return m.filter(ImageFilter.GaussianBlur(blur)) if blur else m


def frame(t, wheel_cache):
    cw, ch, r = 620 * K, 360 * K, 64 * K
    cx, cy = W / 2, H / 2
    inn, txt, out = soft(span(t, 0, .32)), soft(span(t, .18, .5)), soft(span(t, .76, 1))
    s = (.94 + .06 * inn) * (1 + .18 * out)
    bg = Image.new("RGB", (W, H), (0, 0, 0))
    home = wheel_cache["home"]
    img = Image.blend(bg, home, out) if out > 0 else bg
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    box = (cx - cw * s / 2, cy - ch * s / 2, cx + cw * s / 2, cy + ch * s / 2)
    a = inn * (1 - out)
    # the soft spectral light
    breathe = .82 + .18 * math.sin(t * math.pi * 2.2)
    glow = ring_mask((W, H), box, r * s, int(46 * K * s), blur=44 * K)
    glow = glow.point(lambda v: int(min(255, v * 2) * a * breathe))
    layer.paste(wheel_cache["wheel0"], (0, 0), glow)
    card = Image.new("L", (W, H), 0)
    ImageDraw.Draw(card).rounded_rectangle(box, r * s, fill=int(242 * a))
    layer.paste((11, 13, 20), (0, 0), card)
    # the thin edge, colours turning slowly
    edge = ring_mask((W, H), box, r * s, max(1, int(2.5 * K * s + .5))).point(lambda v: int(v * .9 * a))
    layer.paste(sweep((W, H), cx, cy, t * 140), (0, 0), edge)
    # the wordmark settling in
    f = ImageFont.truetype(str(FONTS / "inter_heavy.ttf"), int(112 * K * s))
    tracking = (.12 * (1 - txt) - .01) * 112 * K * s
    word = "PureTV"
    widths = [f.getlength(ch_) for ch_ in word]
    total = sum(widths) + tracking * (len(word) - 1)
    x = cx - total / 2
    d = ImageDraw.Draw(layer)
    y = cy + (38 + 10 * (1 - txt)) * K * s
    for ch_, w_ in zip(word, widths):
        d.text((x, y), ch_, font=f, fill=(255, 255, 255, int(255 * txt * (1 - out))), anchor="ls")
        x += w_ + tracking
    fs = ImageFont.truetype(str(FONTS / "inter_regular.ttf"), int(22 * K))
    d.text((cx, cy + ch / 2 + 110 * K), "Made with love by Crazified Studios", font=fs, fill=(255, 255, 255, int(153 * span(t, .4, .6) * (1 - out))), anchor="ms")
    img = img.convert("RGBA")
    img.alpha_composite(layer)
    return img.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    home = Image.open(ROOT / "assets" / "preview" / "home.png").convert("RGB").resize((W, H), Image.LANCZOS)
    cache = {"home": home, "wheel0": sweep((W, H), W / 2, H / 2, 0)}
    frames = [frame(i / 39, cache) for i in range(40)]
    frames[0].save(OUT / "intro.gif", save_all=True, append_images=frames[1:] + [frames[-1]] * 8, duration=65, loop=0, optimize=True)
    frames[22].save(OUT / "intro-frame.png")
    print(OUT / "intro.gif")


if __name__ == "__main__":
    main()
