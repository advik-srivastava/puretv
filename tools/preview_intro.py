"""A preview of how Home arrives after the TV starts (MainActivity.arrive), with the same timings and curves.

    python tools/build_native_assets.py      (once)
    python tools/preview_intro.py            -> assets/preview/intro.gif and intro-1..4.png
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import preview_native as pn  # noqa: E402

OUT = ROOT / "assets" / "preview"
W, H, FPS, LENGTH = 960, 540, 30, 1.6
K = W / 1920


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


EASE, LIFT = bezier(.2, .8, .2, 1), bezier(.25, 1.35, .4, 1)


def prog(ms, delay, dur, curve):
    return curve(max(0.0, min(1.0, (ms - delay) / dur)))


def layers():
    sc = pn.Scene("ossau")
    scene = pn.scene_layer(sc).convert("RGB").resize((1920, 1080), Image.LANCZOS)           # zoom source
    date = Image.new("RGBA", (pn.W, pn.H), (0, 0, 0, 0)); pn.date_block(date, sc)
    date = date.resize((W, H), Image.LANCZOS)
    cols, gap, tw, th, band_h, band_top, _ = pn.geometry()
    band = Image.new("RGBA", (pn.W, pn.H), (0, 0, 0, 0))
    pn.glass(band, sc, (pn.p(32), pn.p(band_top), pn.p(32 + 1856), pn.p(band_top + band_h)), pn.p(46))
    band = band.resize((W, H), Image.LANCZOS)
    row = [(a, None, pn.NAMES[a]) for a in pn.APPS] + [(None, "settings", "Settings")]
    tiles = []
    for i, (pk, sy, lab) in enumerate(row):
        x, y = 64 + i * (tw + gap), band_top + 26
        pad = 60
        canvas = Image.new("RGBA", (pn.p(tw + 2 * pad), pn.p(th + 2 * pad + 60)), (0, 0, 0, 0))
        pn.tile(canvas, pn.p(pad), pn.p(pad), pn.p(tw), pn.p(th), pk, sy, lab, focused=(i == 0))
        img = canvas.resize((int(canvas.width * K / pn.S), int(canvas.height * K / pn.S)), Image.LANCZOS)
        cx, cy = (x + tw / 2) * K, (y + th / 2) * K          # the tile's centre: it scales about this point
        tiles.append((img, cx, cy, (pad + tw / 2) * K, (pad + th / 2) * K, i == 0))
    return scene, date, band, tiles


def frame(ms, scene, date, band, tiles):
    s = 1.06 - .06 * prog(ms, 0, 1300, EASE)
    zw, zh = 1920 / s, 1080 / s
    crop = scene.crop((int((1920 - zw) / 2), int((1080 - zh) / 2), int((1920 + zw) / 2), int((1080 + zh) / 2)))
    img = crop.resize((W, H), Image.BILINEAR).convert("RGBA")
    def over(layer, a):
        if a <= 0: return
        l2 = layer.copy(); l2.putalpha(l2.getchannel("A").point(lambda v: int(v * a)))
        img.alpha_composite(l2)
    over(date, prog(ms, 350, 600, EASE))
    over(band, prog(ms, 420, 500, EASE))
    for i, (t, cx, cy, ox, oy, focused) in enumerate(tiles):
        p = prog(ms, 500 + i * 55, 520, LIFT)
        a = max(0.0, min(1.0, prog(ms, 500 + i * 55, 520, EASE)))
        if a <= 0: continue
        sc = (.86 + ((1.0 if not focused else 1.0) - .86) * p)   # the image already includes the focused lift
        tw, th = max(1, int(t.width * sc)), max(1, int(t.height * sc))
        ti = t.resize((tw, th), Image.BILINEAR)
        ti.putalpha(ti.getchannel("A").point(lambda v: int(v * a)))
        img.alpha_composite(ti, (int(cx - ox * sc), int(cy - oy * sc)))
    curtain = 1 - prog(ms, 0, 700, EASE)
    if curtain > 0:
        img.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(255 * curtain))))
    return img.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    scene, date, band, tiles = layers()
    n = int(LENGTH * FPS)
    frames = [frame(i * 1000 / FPS, scene, date, band, tiles) for i in range(n + 1)]
    frames[0].save(OUT / "intro.gif", save_all=True, append_images=frames[1:] + [frames[-1]] * 24, duration=int(1000 / FPS), loop=0, optimize=True)
    for k, ms in enumerate((250, 600, 800, 1600), 1):
        frame(ms, scene, date, band, tiles).save(OUT / f"intro-{k}.png")
    print(OUT / "intro.gif")


if __name__ == "__main__":
    main()
