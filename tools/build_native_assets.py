"""Assets for the native PureTV Home (launcher/assets): wallpapers sized for TVs, app artwork, fonts, sounds.

    python tools/build_native_assets.py      then   python launcher/build.py

Everything is sized so the launcher stays small in memory:
  wall/ID.jpg        1366 x 768 photo            (decoded as RGB_565: 2 MB)
  wall/ID-fg.webp    1366 x 768 cut-out scenery  (ARGB: 4 MB, only for the wallpaper on screen and the next)
  wall/ID-glass.jpg   683 x 384 pre-blurred copy (the Liquid Glass: drawn, never blurred live)
  wall/ID-t.jpg       320 x 180 thumbnail
  art/PKG.jpg         TV artwork for well-known apps (others use the app's own banner)
  fonts/*.ttf         Antonio Light (clock), Inter (text), both under the SIL Open Font License
  sfx/*.wav           the soft UI sounds, rendered once here so playing them costs no CPU on the TV
"""
import math
import shutil
import struct
import sys
import wave
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
UI, OUT = ROOT / "app" / "ui" / "demo", ROOT / "launcher" / "assets"
SCENES = ["ossau", "flatirons", "luna", "kathleen", "titicaca", "louise"]
W, H = 1366, 768
RATE = 44100


def fit(img, w, h):
    k = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * k), round(img.height * k)), Image.LANCZOS)
    x, y = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def wallpapers():
    out = OUT / "wall"
    out.mkdir(parents=True)
    for s in SCENES:
        photo = Image.open(UI / "wallpapers" / f"{s}.jpg").convert("RGB")
        fg = Image.open(UI / "wallpapers" / f"{s}-fg.png").convert("RGBA")
        fit(photo, W, H).save(out / f"{s}.jpg", quality=88, optimize=True, progressive=True)
        fit(fg, W, H).save(out / f"{s}-fg.webp", quality=90, alpha_quality=100, method=6)
        # the glass: a strongly blurred, slightly brightened copy; panels show the slice behind them
        g = fit(photo, W // 2, H // 2).filter(ImageFilter.GaussianBlur(14))
        g.save(out / f"{s}-glass.jpg", quality=86)
        fit(photo, 320, 180).save(out / f"{s}-t.jpg", quality=84)
    shutil.copy(UI / "wallpapers" / "CREDITS.md", out / "CREDITS.md")


def art():
    out = OUT / "art"
    out.mkdir(parents=True)
    for f in (UI / "icons-tv").glob("*.jpg"):
        Image.open(f).convert("RGB").resize((400, 240), Image.LANCZOS).save(out / f.name, quality=88)


def fonts():
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
    out = OUT / "fonts"
    out.mkdir(parents=True)
    f = TTFont(UI / "fonts" / "antonio-latin-300.woff2")
    f.flavor = None
    f.save(out / "antonio_light.ttf")
    for w, n in [(400, "inter_regular"), (600, "inter_semibold"), (800, "inter_heavy")]:
        i = instancer.instantiateVariableFont(TTFont(UI / "fonts" / "inter-latin-wght-normal.woff2"), {"wght": w})
        i.flavor = None
        i.save(out / f"{n}.ttf")


def tone(buf, t0, f0, f1, vol, dur, swell):
    """A pure sine with a slow swell (no click) and a smooth exponential fade, added into buf."""
    n0, n = int(t0 * RATE), int(dur * 1.6 * RATE)
    phase = 0.0
    for i in range(n):
        t = i / RATE
        f = f0 * (f1 / f0) ** min(1.0, t / dur) if f1 else f0
        phase += 2 * math.pi * f / RATE
        env = (t / swell) if t < swell else math.exp(-(t - swell) / (dur / 4))
        if n0 + i < len(buf):
            buf[n0 + i] += vol * env * math.sin(phase)


def room(buf, mix=.1):
    """A faint, dark room: a few soft early reflections."""
    out = list(buf)
    for delay, g in [(.011, .5), (.019, .38), (.029, .27), (.041, .18), (.057, .1)]:
        d = int(delay * RATE)
        prev = 0.0
        for i in range(d, len(buf)):
            prev += (buf[i - d] - prev) * .25   # darker: each reflection loses its highs
            out[i] += mix * g * prev
    return out


def sounds():
    out = OUT / "sfx"
    out.mkdir(parents=True)
    master = 2.4 * .9
    defs = {
        "nav": [(0, 1046.5, 1000, .022, .07, .006), (0, 523.25, 0, .012, .06, .006)],
        "ok": [(0, 783.99, 0, .032, .22, .01), (.028, 1174.66, 0, .016, .2, .012), (0, 392, 0, .012, .14, .012)],
        "back": [(0, 698.46, 587.33, .03, .17, .01), (0, 349.23, 293.66, .01, .12, .012)],
        "edge": [(0, 233.08, 196, .04, .09, .008)],
    }
    for name, parts in defs.items():
        length = max(t0 + dur * 1.6 for t0, _, _, _, dur, _ in parts) + .08
        buf = [0.0] * int(length * RATE)
        for p in parts:
            tone(buf, *p)
        buf = room(buf)
        with wave.open(str(out / f"{name}.wav"), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", max(-32767, min(32767, int(v * master * 32767)))) for v in buf))


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    wallpapers()
    art()
    fonts()
    sounds()
    size = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file())
    print(f"built {OUT} ({size // 1024} KB)")


if __name__ == "__main__":
    sys.exit(main())
