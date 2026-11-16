"""Turn a landscape photo into a PureTV scene: the photo, the scenery cut out in front of the clock,
and a soft glass copy for the clock's fill.

    python -I tools/make_scene.py app/ui/demo/wallpapers/ossau.jpg [more.jpg ...]

For each photo it writes <name>-fg.png (the land below the skyline, sky transparent), <name>-glass.jpg
(blurred and brightened), and prints where the skyline crosses the middle of the screen, which is
where the clock's digits are tucked behind the scenery. The skyline is found per column: walking down
from the top, the sky ends where the picture first gets busy (strong, lasting edges) and stays busy.
"""
import json
import os
import sys
from pathlib import Path

from PIL import Image, ImageFilter, ImageOps, ImageChops


BRIGHT_STEP = int(os.environ.get("PURETV_SKY_STEP", "34"))   # raise for sunsets with a strong glow


def skyline(img):
    """The sky grown down from the top edge: a pixel joins while its colour stays close to its neighbour
    above (smooth sky, soft clouds) and to the sky's own colours, and the land stops it."""
    small = img.resize((384, 216), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.8))
    w, h = small.size
    px = small.load()
    top = [px[x, y] for y in range(0, 8) for x in range(0, w, 3)]
    ref = [sum(c[i] for c in top) / len(top) for i in range(3)]
    lum = lambda c: .3 * c[0] + .59 * c[1] + .11 * c[2]
    ref_l = lum(ref)
    d = lambda a, b: ((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2) ** .5
    sky = [[False] * w for _ in range(h)]
    stack = [(x, 0) for x in range(w)]
    for x in range(w):
        sky[0][x] = True
    while stack:
        x, y = stack.pop()
        c = px[x, y]
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not sky[ny][nx]:
                n = px[nx, ny]
                # smooth step from its neighbour, still sky-coloured or cloud-bright, not much darker than the sky
                step_ok = d(n, c) < (BRIGHT_STEP if lum(n) > ref_l - 8 and lum(c) > ref_l - 8 else 16)   # clouds and haze: soft, bright steps
                if step_ok and (d(n, ref) < 110 or lum(n) > ref_l + 25) and lum(n) > ref_l * .55:
                    sky[ny][nx] = True
                    stack.append((nx, ny))
    line = []
    for x in range(w):
        y = 0
        if os.environ.get("PURETV_SKY_MODE") == "dark":   # sunsets: the land is a dark silhouette under a bright sky
            while y < h - 3 and not all(lum(px[x, y + j]) < 70 for j in range(3)):
                y += 1
        else:
            while y < h and sky[y][x]:
                y += 1
        line.append(y)
    k = 7   # a wide rolling median drops thin specks where a cloud edge stopped the sky early
    smooth = [sorted(line[max(0, x - k): x + k + 1])[len(line[max(0, x - k): x + k + 1]) // 2] for x in range(w)]
    return [v * img.height / h for v in smooth], w


def make(path):
    path = Path(path)
    img = Image.open(path).convert("RGB")
    W, H = img.size
    line, w = skyline(img)
    mask_small = Image.new("L", (w, 216), 0)
    m = mask_small.load()
    for x in range(w):
        y0 = int(line[x] * 216 / H)
        for y in range(y0, 216):
            m[x, y] = 255
    mask = mask_small.resize((W, H), Image.BILINEAR).filter(ImageFilter.GaussianBlur(2.5))
    # tighten the soft edge a little so no light rim shows over the digits
    mask = mask.point(lambda v: 0 if v < 60 else 255 if v > 200 else int((v - 60) * 255 / 140))
    fg = img.copy()
    fg.putalpha(mask)
    fg.save(path.with_name(path.stem + "-fg.png"), optimize=True)
    glass = img.filter(ImageFilter.GaussianBlur(16))
    glass = ImageChops.add(glass, Image.new("RGB", glass.size, (0, 0, 0)))
    from PIL import ImageEnhance
    glass = ImageEnhance.Color(ImageEnhance.Brightness(glass).enhance(1.3)).enhance(1.25)
    glass.resize((960, 540), Image.LANCZOS).save(path.with_name(path.stem + "-glass.jpg"), quality=80)
    mid = sorted(line[int(w * .36): int(w * .64)])
    horizon = int(mid[len(mid) // 4])     # the higher parts of the middle: where peaks reach into the digits
    return {"scene": path.stem, "horizon": horizon, "low": int(mid[-1])}


if __name__ == "__main__":
    print(json.dumps([make(p) for p in sys.argv[1:]], indent=1))
