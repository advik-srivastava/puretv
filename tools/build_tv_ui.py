"""Turn the Home screen page (app/ui/tv.html) into the one the launcher ships (launcher/assets/ui).

    python tools/build_tv_ui.py            # then: python launcher/build.py
    python tools/build_tv_ui.py --test     # also writes app/ui/tvtest/ with a pretend TV, for the browser

What changes for the TV:
  * no demo frame: the page is laid out at 1920 x 1080 and the WebView draws it at the panel's size;
  * launcher/ui-src/tv-bridge.js joins the page's script and connects it to the real TV (Bridge.java);
  * launcher/ui-src/tv.css: balanced glass for HD Ready panels, and the update and Remote Helper prompts;
  * wallpapers are re-sized for 1366 x 768 panels, cut-outs become WebP, and setup gets small thumbnails,
    so the launcher uses a fraction of the memory the full-size demo images would need.
"""
import json
import shutil
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
UI, SRC, OUT = ROOT / "app" / "ui", ROOT / "launcher" / "ui-src", ROOT / "launcher" / "assets" / "ui"
W, H = 1366, 768
SCENES = ["ossau", "flatirons", "luna", "kathleen", "titicaca", "louise"]

EDITS = [
    ('<meta name="viewport" content="width=device-width, initial-scale=1">', '<meta name="viewport" content="width=1920, user-scalable=no">'),
    ('<title>PureTV Home — TV demo</title>', '<title>PureTV Home</title>'),
    ('const artHTML = (a) =>', 'let artHTML = (a) =>'),
    ('const autoLite = (navigator.hardwareConcurrency || 8) <= 4 || (navigator.deviceMemory || 8) <= 2;',
     'const autoLite = window.PureTV ? JSON.parse(PureTV.mem()).total < 1200 : (navigator.hardwareConcurrency || 8) <= 4;'),
    ('setInterval(tick, 5000)', 'setInterval(() => tick(), 5000)'),
    ('document.fonts.ready.then(tick);', 'document.fonts.ready.then(() => tick());'),
    ('runSetup(0); wake();', 'if (!window.PureTV) runSetup(0); wake();'),
    ('const P = (id, k = "") => `demo/wallpapers/${id}${k}`;', 'const P = (id, k = "") => `demo/wallpapers/${id}${k === "-fg.png" ? "-fg.webp" : k}`;'),
    ('<img src="${P(s.id, ".jpg")}" alt=""><div class="mini"', '<img src="${P(s.id, "-t.jpg")}" alt=""><div class="mini"'),
    ('${s.fg ? `<img src="${P(s.id, "-fg.png")}" alt="">` : ""}</div><span>', '${s.fg ? `<img src="${P(s.id, "-tfg.webp")}" alt="">` : ""}</div><span>'),
    ('img: P(x.id, ".jpg"), t: x.name', 'img: P(x.id, "-t.jpg"), t: x.name'),
]

MOCK = """<script>/* a pretend TV for testing in a browser: tools/build_tv_ui.py --test */
window.PureTV = (() => {
  const apps = [["com.google.android.youtube.tv","YouTube",1],["com.netflix.ninja","Netflix",1],["com.amazon.amazonvideo.livingroom","Prime Video",1],
    ["in.startv.hotstar","JioHotstar",1],["com.disney.disneyplus","Disney+",1],["com.spotify.tv.android","Spotify",1],["com.apple.atve.androidtv.appletv","Apple TV",1],
    ["com.google.android.youtube.tvmusic","YouTube Music",1],["com.android.vending","Google Play Store",1]];
  let auto = true, take = true, last = 0; window.__calls = [];
  const log = (...a) => { window.__calls.push(a.join(" ")); return true; };
  return {
    version: () => "2.0", device: () => JSON.stringify({ maker: "Acer", model: "AR32QDXGU2841AT", android: "14", w: 1366, h: 768, cores: 4 }),
    apps: () => JSON.stringify(apps.map(([pkg, name, tv]) => ({ pkg, name, tv: !!tv }))), has: (p) => apps.some((a) => a[0] === p),
    launch: (p) => log("launch", p), store: (p) => log("store", p), settings: (p) => log("settings", p), search: () => log("search"),
    inputs: () => JSON.stringify([{ id: "hdmi1", name: "HDMI 1", pass: true }, { id: "hdmi2", name: "HDMI 2", pass: true }, { id: "tuner", name: "Live TV", pass: false }]),
    input: (i) => log("input", i), mem: () => JSON.stringify({ total: 1484, free: 612, low: false }), close: (p) => log("close", p),
    disabled: () => JSON.stringify([{ pkg: "com.apps.mk.wd", name: "Partner ads" }, { pkg: "com.google.android.feedback", name: "Feedback" }]),
    isHome: () => false, helperOn: () => false, openHelper: () => log("helper"), takeHome: () => take, setTakeHome: (v) => { take = v; },
    googleHome: () => log("googleHome"), sleep: () => false, backup: (j) => { localStorage.setItem("mockBackup", j); return true; },
    restoreBackup: () => localStorage.getItem("mockBackup") || "", backupTime: () => 0,
    autoUpdate: () => auto, setAutoUpdate: (v) => { auto = v; }, updateDue: () => Date.now() - last > 1000, canInstall: () => true,
    checkUpdate: (install) => { last = Date.now(); log("checkUpdate", install);
      const say = (o, t) => setTimeout(() => tvUpdate(JSON.stringify(o)), t);
      if (!install) { say({ state: "checking" }, 10); say({ state: "available", version: "2.1", notes: "Smoother wallpapers\\nFaster Settings" }, 600); }
      else { [20, 60, 100].forEach((p, i) => say({ state: "downloading", version: "2.1", pct: String(p) }, 300 * (i + 1))); say({ state: "installing", version: "2.1" }, 1300); } },
  };
})();
</script>"""


def fit(img, w, h):
    k = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * k), round(img.height * k)), Image.LANCZOS)
    x, y = (img.width - w) // 2, (img.height - h) // 2
    return img.crop((x, y, x + w, y + h))


def wallpapers(dest):
    src, out = UI / "demo" / "wallpapers", dest / "demo" / "wallpapers"
    out.mkdir(parents=True, exist_ok=True)
    for s in SCENES:
        photo = Image.open(src / f"{s}.jpg").convert("RGB")
        fg = Image.open(src / f"{s}-fg.png").convert("RGBA")
        fit(photo, W, H).save(out / f"{s}.jpg", quality=88, optimize=True, progressive=True)
        fit(fg, W, H).save(out / f"{s}-fg.webp", quality=90, alpha_quality=100, method=6)
        Image.open(src / f"{s}-glass.jpg").convert("RGB").resize((960, 540), Image.LANCZOS).save(out / f"{s}-glass.jpg", quality=86)
        fit(photo, 400, 225).save(out / f"{s}-t.jpg", quality=84)
        fit(fg, 400, 225).save(out / f"{s}-tfg.webp", quality=88, alpha_quality=100, method=6)
    shutil.copy(src / "CREDITS.md", out / "CREDITS.md")


def build(dest, test=False):
    html = (UI / "tv.html").read_text(encoding="utf-8")
    for a, b in EDITS:
        assert html.count(a) == 1, f"tv.html changed, can't find: {a[:70]}"
        html = html.replace(a, b)
    bridge = (SRC / "tv-bridge.js").read_text(encoding="utf-8")
    end = "</script>\n</body>"
    assert html.count(end) == 1
    html = html.replace(end, "\n" + bridge + end)
    html = html.replace("</head>", "<style>\n" + (SRC / "tv.css").read_text(encoding="utf-8") + "</style>\n</head>", 1)
    if test:
        html = html.replace("<script>", MOCK + "\n<script>", 1)
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True)
    (dest / "tv.html").write_text(html, encoding="utf-8")
    for d in ("fonts", "icons-tv", "logos", "logos-dark"):
        shutil.copytree(UI / "demo" / d, dest / "demo" / d)
    wallpapers(dest)
    size = sum(f.stat().st_size for f in dest.rglob("*") if f.is_file())
    print(f"built {dest} ({size // 1024} KB)")


if __name__ == "__main__":
    build(OUT)
    if "--test" in sys.argv:
        build(UI / "tvtest", test=True)
