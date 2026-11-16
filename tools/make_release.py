"""Pack a ready-to-run PureTV download: the PC app, the TV preview, and the launcher APK already built.

    PURETV_VERSION=1.1 python launcher/build.py
    python -I tools/make_release.py 1.1

Writes release/PureTV-<version>.zip. Unzip it anywhere and run "Start PureTV.bat" (Windows) or start.sh.
"""
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = ["Start PureTV.bat", "start.sh", "requirements.txt", "README.md", "LICENSE", "NOTICE.md"]
DIRS = ["app", "launcher/dist"]
SKIP = ("__pycache__", "logos-dark", ".part")


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "dev"
    apk = ROOT / "launcher" / "dist" / "purehome.apk"
    if not apk.exists():
        sys.exit("Build the launcher first: PURETV_VERSION=%s python launcher/build.py" % version)
    out = ROOT / "release" / f"PureTV-{version}.zip"
    out.parent.mkdir(exist_ok=True)
    top = f"PureTV-{version}"
    n = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for name in FILES:
            info = zipfile.ZipInfo.from_file(ROOT / name, f"{top}/{name}")
            if name.endswith(".sh"):
                info.external_attr = 0o100755 << 16   # executable on macOS and Linux
            z.writestr(info, (ROOT / name).read_bytes(), zipfile.ZIP_DEFLATED)
            n += 1
        for d in DIRS:
            for f in sorted((ROOT / d).rglob("*")):
                rel = f.relative_to(ROOT).as_posix()
                if f.is_file() and not any(s in rel for s in SKIP) and not (f.name.startswith("tv-v") and f.suffix == ".html"):
                    z.write(f, f"{top}/{rel}")
                    n += 1
    print(f"{out} ({n} files, {out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
