"""Make tvOS-style app icons for the demo: wide (5:3), full bleed, the artwork filling the whole tile.

    python -I tools/make_tv_icons.py

A tvOS icon is a 400x240 picture with no border. The logos we have are square, so each one is
widened by carrying its own left and right edge colours out to the sides (row by row, so gradients
like Disney+'s continue smoothly), then drawn at 2x for sharpness. Writes app/ui/demo/icons-tv/.
On a TV the launcher uses each app's own 16:9 banner, which Android TV apps already ship.
"""
from pathlib import Path
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parents[1] / "app" / "ui" / "demo"
SRC, OUT = HERE / "logos", HERE / "icons-tv"
W, H = 800, 480                      # 2x of tvOS's 400x240
SOLID = {"com.google.android.youtube.tv": (255, 255, 255)}   # transparent logo: give it YouTube's white card
SCALE = {"com.google.android.youtube.tv": 0.8, "com.netflix.ninja": 1.05, "com.apple.atve.androidtv.appletv": 1.0}


def main():
    OUT.mkdir(exist_ok=True)
    for src in sorted(SRC.glob("*.png")):
        logo = Image.open(src).convert("RGBA")
        side = int(H * SCALE.get(src.stem, 1.0))
        logo = logo.resize((side, side), Image.LANCZOS)
        canvas = Image.new("RGBA", (W, H), SOLID.get(src.stem, (0, 0, 0)) + (255,))
        x0, y0 = (W - side) // 2, (H - side) // 2
        if src.stem not in SOLID:
            # carry each row's edge colour outwards; average a few columns so anti-aliasing does not streak
            left = logo.crop((2, 0, 10, side)).resize((1, side), Image.BOX).resize((x0 + 4, side))
            right = logo.crop((side - 10, 0, side - 2, side)).resize((1, side), Image.BOX).resize((W - x0 - side + 4, side))
            band = Image.new("RGBA", (W, side))
            band.paste(left, (0, 0)); band.paste(right, (x0 + side - 4, 0))
            canvas.paste(band, (0, y0))
            if y0 > 0:   # logo shorter than the tile: carry the top and bottom rows too
                top = canvas.crop((0, y0, W, y0 + 1)).resize((W, y0)); canvas.paste(top, (0, 0))
                bot = canvas.crop((0, y0 + side - 1, W, y0 + side)).resize((W, H - y0 - side)); canvas.paste(bot, (0, y0 + side))
            canvas = canvas.filter(ImageFilter.GaussianBlur(1.2))
        canvas.alpha_composite(logo, (x0, y0))
        canvas.convert("RGB").save(OUT / (src.stem + ".jpg"), quality=90)
        print("tv icon", src.stem)


if __name__ == "__main__":
    main()
