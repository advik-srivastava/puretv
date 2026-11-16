"""Render PureTV's README images with headless Chrome or Edge (start PureTV first: it serves the pages).

    python -I tools/render.py            demo screenshots, then the repo card and the README banner

Writes assets/shots/*.png (1920x1080 frames of the TV demo), assets/social-card.png (1280x640,
for GitHub's Settings > Social preview) and assets/banner.png (the README's top image).
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get("PURETV_URL", "http://127.0.0.1:8765")
SHOTS = {"home": "shot=home&scene=0", "cc": "shot=cc&scene=1", "settings": "shot=settings&scene=0", "setup": "shot=setup&scene=0", "grid": "shot=grid&scene=2"}


def browser():
    for p in [r"C:\Program Files\Google\Chrome\Application\chrome.exe", r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
              shutil.which("google-chrome") or "", shutil.which("chromium") or "", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"]:
        if p and Path(p).exists():
            return p
    sys.exit("Chrome or Edge is needed to render the images.")


def shoot(url, out, w, h, wait=9000):
    with tempfile.TemporaryDirectory() as prof:
        subprocess.run([browser(), "--headless=new", f"--user-data-dir={prof}", "--hide-scrollbars", "--force-device-scale-factor=1",
                        f"--window-size={w},{h}", f"--virtual-time-budget={wait}", "--run-all-compositor-stages-before-draw",
                        f"--screenshot={out}", url], capture_output=True, timeout=120)
    print(out.relative_to(ROOT), "ok" if out.exists() else "MISSING")


def main():
    shots = ROOT / "assets" / "shots"
    shots.mkdir(parents=True, exist_ok=True)
    only = sys.argv[1:]
    for name, q in SHOTS.items():
        if not only or name in only or "shots" in only:
            shoot(f"{BASE}/tv.html?bare&{q}", shots / f"{name}.png", 1920, 1080)
    for name, size in {"social-card": (1280, 640), "banner": (1600, 1330)}.items():
        if not only or name in only:
            shoot((ROOT / "assets" / "src" / f"{name}.html").as_uri(), ROOT / "assets" / f"{name}.png", *size, wait=3000)


if __name__ == "__main__":
    main()
