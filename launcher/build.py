"""Build launcher/dist/purehome.apk without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.

    python launcher/build.py

Needs a JDK 17 and the Android SDK (platform 34, build-tools 34.x). Looks in ~/.puretv/toolchain
first (where PureTV's setup puts them), then JAVA_HOME / ANDROID_HOME.
"""
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOLS = Path.home() / ".puretv" / "toolchain"
BUILD, DIST = HERE / "build", HERE / "dist"
MIN_SDK, TARGET_SDK = "23", "34"
# A release must carry a higher version than the one on the TV, or Android refuses the update:
#   PURETV_VERSION=1.1 python launcher/build.py   ->  versionName 1.1, versionCode 10100
VERSION_NAME = os.environ.get("PURETV_VERSION", "1.0")
_v = [int(x) for x in (VERSION_NAME.split(".") + ["0", "0"])[:3]]
VERSION_CODE = str(_v[0] * 10000 + _v[1] * 100 + _v[2])


def find_jdk():
    for root in [*sorted((TOOLS / "jdk").glob("*")), Path(os.environ.get("JAVA_HOME", "") or "nope")]:
        if (root / "bin" / ("javac.exe" if os.name == "nt" else "javac")).exists():
            return root
    sys.exit("No JDK found. Install a JDK 17 or set JAVA_HOME.")


def find_sdk():
    for root in [TOOLS / "sdk", Path(os.environ.get("ANDROID_HOME", "") or "nope"),
                 Path(os.environ.get("LOCALAPPDATA", "") or "nope") / "Android" / "Sdk"]:
        tools = sorted((root / "build-tools").glob("3[4-9].*"))
        jar = root / "platforms" / "android-34" / "android.jar"
        if tools and jar.exists():
            return tools[-1], jar
    sys.exit("No Android SDK with platform 34 and build-tools 34+ found.")


def run(*cmd, env=None):
    cmd = [str(c) for c in cmd]
    r = subprocess.run(cmd, env=env, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"FAILED: {' '.join(cmd[:3])} ...\n{r.stdout}\n{r.stderr}")
    return r.stdout


def main():
    jdk = find_jdk()
    bt, jar = find_sdk()
    exe = ".exe" if os.name == "nt" else ""
    bat = ".bat" if os.name == "nt" else ""
    env = dict(os.environ, JAVA_HOME=str(jdk), PATH=str(jdk / "bin") + os.pathsep + os.environ["PATH"])
    shutil.rmtree(BUILD, ignore_errors=True)
    for d in (BUILD / "gen", BUILD / "classes", BUILD / "dex", DIST):
        d.mkdir(parents=True, exist_ok=True)

    run(bt / f"aapt2{exe}", "compile", "--dir", HERE / "res", "-o", BUILD / "res.zip")
    run(bt / f"aapt2{exe}", "link", "-o", BUILD / "base.apk", "-I", jar, "--manifest", HERE / "AndroidManifest.xml",
        "--java", BUILD / "gen", "--min-sdk-version", MIN_SDK, "--target-sdk-version", TARGET_SDK,
        "--version-code", VERSION_CODE, "--version-name", VERSION_NAME, BUILD / "res.zip")
    sources = [*HERE.glob("src/**/*.java"), *BUILD.glob("gen/**/*.java")]
    run(jdk / "bin" / f"javac{exe}", "-source", "8", "-target", "8", "-Xlint:-options", "-bootclasspath", jar,
        "-d", BUILD / "classes", *sources)
    run(bt / f"d8{bat}", "--release", "--lib", jar, "--min-api", MIN_SDK, "--output", BUILD / "dex",
        *BUILD.glob("classes/**/*.class"), env=env)
    shutil.copy(BUILD / "base.apk", BUILD / "unsigned.apk")
    with zipfile.ZipFile(BUILD / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as z:
        z.write(BUILD / "dex" / "classes.dex", "classes.dex")
    run(bt / f"zipalign{exe}", "-f", "-p", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")

    # One signing key per computer, kept outside the project: an update must carry the same signature.
    ks = Path.home() / ".puretv" / "puretv.keystore"
    pw = os.environ.get("PURETV_KEY_PASS", "puretv")   # set your own before the first build; the key never leaves this computer
    if not ks.exists():
        ks.parent.mkdir(parents=True, exist_ok=True)
        run(jdk / "bin" / f"keytool{exe}", "-genkeypair", "-keystore", ks, "-storepass", pw, "-keypass", pw,
            "-alias", "puretv", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=PureTV")
    out = DIST / "purehome.apk"
    run(bt / f"apksigner{bat}", "sign", "--ks", ks, "--ks-pass", "pass:" + pw, "--key-pass", "pass:" + pw,
        "--out", out, BUILD / "aligned.apk", env=env)
    run(bt / f"apksigner{bat}", "verify", out, env=env)
    for extra in DIST.glob("*.idsig"):
        extra.unlink()
    print(f"built {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
