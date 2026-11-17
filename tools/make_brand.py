"""PureTV's app icon and TV banner (what the TV's app list shows).

    python tools/make_brand.py      -> launcher/res/drawable-nodpi/icon.png, banner.png
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


if __name__ == "__main__":
    icons()
