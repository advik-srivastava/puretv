"""PureTV core: talk to an Android TV / Google TV over network ADB, back it up, switch bloat off
with `pm disable-user` (nothing is uninstalled), install the PureTV launcher, and undo all of it.

The method and every safety guard here follow cleanyourtv's tools (disable, recovery, restore and
build-and-install), MIT licensed: https://github.com/Gathin23/cleanyourtv. See NOTICE.md.
"""
import base64
import hashlib
import sys
import ipaddress
import socket
import subprocess
from concurrent.futures import ThreadPoolExecutor
import json
import os
import re
import time
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = Path(os.environ.get("PURETV_HOME") or Path.home() / ".puretv")
APK = HERE.parent / "launcher" / "dist" / "purehome.apk"

# Every name from the TV ends up inside a command run by the TV's shell: nothing with shell syntax passes.
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
HOME_RE = re.compile(r"[A-Za-z_][\w.]*/[\w.$]+")
SCALE_RE = re.compile(r"-?\d+(\.\d+)?")
SCALES = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

OUR_PKG = "tv.pure.home"
OUR_HOME = "tv.pure.home/.MainActivity"
SETUP = "com.google.android.tungsten.setupwraith"
STOCK_LAUNCHERS = ("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
                   "com.google.android.leanbacklauncher", "com.mitv.tvhome")
NOT_LAUNCHERS = {SETUP, "com.android.tv.settings", "com.android.settings", "com.android.provision"}
FACTORY_RE = re.compile(r"factorytest|factory_test|factorymode", re.I)

# Core Android and TV middleware: with one of these off the TV may not boot. Never disabled, no override.
CORE_RE = re.compile(
    r"^(android|android\.ext\.(services|shared)|com\.android\.sdksandbox|com\.android\.location\.fused"
    r"|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings"
    r"|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller"
    r"|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*"
    r"|com\.android\.providers\..*|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent))$")
CORE_OK = re.compile(r"^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$")
# PureTV's own extra keep-list: things a living room breaks without (Play services and store, the
# keyboard, the remote's Bluetooth, WebView, networking).
KEEP_RE = re.compile(
    r"^(com\.google\.android\.(gms|gsf|webview|inputmethod\..*|tv\.frameworkpackagestubs)"
    r"|com\.android\.(vending|bluetooth|webview|networkstack.*|wifi.*|inputmethod\..*|keychain|se|nfc"
    r"|certinstaller|externalstorage|inputdevices|server\.telecom|mtp|proxyhandler|vpndialogs)"
    r"|.*\.(keyboard|inputmethod|ime)(\..*)?)$", re.I)

HOME_QUERY = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
HOME_CLAIMS = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"

BATCH = 4          # packages per batch, as the tested lists advise
PAUSE = 6          # seconds between batches so PackageManager can write its state down


class TVError(Exception):
    """A problem worth showing the user in plain words."""


def knowledge():
    try:
        return json.loads((HERE / "knowledge.json").read_text(encoding="utf-8"))
    except OSError:
        return {"lists": 0, "packages": {}}


def split_host(text):
    text = (text or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9.\-]+(:\d{1,5})?", text):
        raise TVError("That does not look like a TV address. Use the IP from the TV's network settings, e.g. 192.168.1.50")
    host, _, port = text.partition(":")
    return host, int(port or 5555)


# ---------------------------------------------------------------- connection

class TV:
    """One connection to a TV. `dev` is an adb_shell device or the demo TV."""

    def __init__(self, host, dev, demo=False):
        self.host, self.dev, self.demo = host, dev, demo

    @classmethod
    def connect(cls, address, timeout=60):
        host, port = split_host(address)
        try:
            from adb_shell.adb_device import AdbDeviceTcp
            from adb_shell.auth.keygen import keygen
            from adb_shell.auth.sign_pythonrsa import PythonRSASigner
        except ImportError as e:
            raise TVError(f"PureTV's packages are not installed ({e}). Start it with 'Start PureTV.bat'.")
        DATA.mkdir(parents=True, exist_ok=True)
        key = DATA / "adbkey"
        if not key.exists():
            keygen(str(key))
        signer = PythonRSASigner((DATA / "adbkey.pub").read_text(), key.read_text())
        dev = AdbDeviceTcp(host, port, default_transport_timeout_s=float(timeout))
        try:
            dev.connect(rsa_keys=[signer], transport_timeout_s=float(timeout), auth_timeout_s=float(timeout))
        except ConnectionRefusedError:
            raise TVError("The TV refused the connection. Turn on USB debugging (and Network/ADB debugging "
                          "if the TV shows it) in Developer options, then try again.")
        except (TimeoutError, OSError) as e:
            raise TVError(f"Could not reach the TV at {host}. Check the IP address and that this PC is on "
                          f"the same Wi-Fi. ({e.__class__.__name__})")
        except Exception as e:  # adb_shell raises its own types for a declined or unanswered prompt
            raise TVError("The TV did not allow this computer. Look at the TV: choose 'Always allow from "
                          f"this computer' and press Allow, then try again. ({e.__class__.__name__})")
        return cls(f"{host}:{port}", dev)

    def sh(self, command, timeout=120.0):
        try:
            return (self.dev.shell(command, read_timeout_s=timeout) or "").replace("\r", "").strip()
        except Exception as e:
            raise TVError(f"The TV stopped answering ({e.__class__.__name__}). Check it is still on, then reconnect.")

    def close(self):
        try:
            self.dev.close()
        except Exception:
            pass

    # ------------------------------------------------------------ queries

    def prop(self, name):
        return self.sh(f"getprop {name}")

    def packages(self, flag=""):
        answer = self.sh(f"pm list packages {flag} --user 0".replace("  ", " "))
        names = {l.split(":", 1)[1].strip() for l in answer.splitlines() if l.startswith("package:")}
        if not names and not flag:
            answer = self.sh("pm list packages")
            names = {l.split(":", 1)[1].strip() for l in answer.splitlines() if l.startswith("package:")}
        return {n for n in names if PACKAGE_RE.fullmatch(n)}

    def home(self):
        answer = self.sh(HOME_QUERY)
        line = answer.splitlines()[-1].strip() if answer else ""
        return line if HOME_RE.fullmatch(line) else None

    def activities(self, query):
        """[(package, class)] answering an intent query."""
        found = []
        for line in self.sh(f"cmd package query-activities --brief {query}").splitlines():
            m = re.fullmatch(r"([A-Za-z0-9_.]+)/([A-Za-z0-9_.$]+)", line.strip())
            if m:
                found.append((m.group(1), m.group(2)))
        return found

    def home_claimants(self):
        return self.activities("-a android.intent.action.MAIN -c android.intent.category.HOME")

    def launchers(self):
        """Packages that are real launchers able to answer HOME right now."""
        return {p for p, c in self.home_claimants()
                if not c.endswith("FallbackHome") and p not in NOT_LAUNCHERS and not FACTORY_RE.search(p)}

    def info(self):
        sdk = re.sub(r"\D", "", self.prop("ro.build.version.sdk"))
        serial = re.sub(r"[^A-Za-z0-9_-]", "", self.prop("ro.serialno")) or re.sub(r"[^A-Za-z0-9]", "_", self.host)
        return {
            "host": self.host, "demo": self.demo, "id": serial,
            "brand": self.prop("ro.product.brand"), "model": self.prop("ro.product.model"),
            "android": self.prop("ro.build.version.release"), "sdk": int(sdk or 0),
            "home": self.home(),
            "adbSecure": self.prop("ro.adb.secure"),
        }

    # ------------------------------------------------------------ guards

    def input_drivers(self, names):
        """(drivers, unreadable, checked): packages under the HDMI/AV/tuner inputs must stay on.

        A package asking for TV_INPUT_HARDWARE is the software layer under the inputs; with it off a
        port notices the cable and stays black. A dump the check cannot read counts as unreadable and
        the package is kept on. `checked` is False when the loop did not run at all.
        """
        names = [n for n in names if PACKAGE_RE.fullmatch(n)]
        if not names:
            return set(), set(), True
        marker = "puretv-" + uuid.uuid4().hex
        loop = ("s=$(getprop ro.build.version.sdk); for p in " + " ".join(names) + "; do "
                "d=$(dumpsys package $p 2>/dev/null); case $d in "
                "*\"requested permissions:\"*) printf '%s\\n' \"$d\" | sed -n -E '/requested permissions:/,/(install|runtime) permissions:|User [0-9]+:/p' | grep -q TV_INPUT_HARDWARE && echo $p;; "
                "*\"grantedPermissions:\"*) printf '%s\\n' \"$d\" | sed -n -E '/grantedPermissions:/,/(install|runtime) permissions:|User [0-9]+:/p' | grep -q TV_INPUT_HARDWARE && echo $p;; "
                "*\"Service Resolver Table:\"*) printf '%s\\n' \"$d\" | grep -q 'android.media.tv.TvInputService' && echo $p;; "
                "*\"Unable to find\"*) :;; "
                "*\"hung system process\"*) echo \"NOCHK $p\";; "
                "*\"Package [$p]\"*) [ \"${s:-0}\" -ge 23 ] 2>/dev/null || echo \"NOCHK $p\";; "
                "*) echo \"NOCHK $p\";; esac; done; true; echo " + marker)
        lines = [l.strip() for l in self.sh(loop, timeout=300.0).splitlines()]
        if marker not in lines:
            return set(), set(names), False
        drivers = {l for l in lines if l in names}
        unreadable = {l[6:].strip() for l in lines if l.startswith("NOCHK ")}
        return drivers, unreadable, True

    def handlers(self):
        """Packages the TV opens its inputs/channels and Settings with."""
        inputs = {p for p, _ in self.activities("-a android.intent.action.VIEW -t vnd.android.cursor.item/channel")}
        settings = {p for p, _ in self.activities("-a android.settings.SETTINGS")}
        return inputs, settings

    def setup_owns_wifi(self):
        """True when disabling the setup wizard would delete the saved Wi-Fi (or we cannot tell)."""
        wifi = self.sh("dumpsys wifi | grep -o 'cname=[^ ]*'")
        return "cname=" not in wifi or "setupwraith" in wifi


# ---------------------------------------------------------------- scan

def scan(tv):
    """Everything the UI needs: device info and every relevant package with a verdict."""
    info = tv.info()
    installed = tv.packages()
    if not installed:
        raise TVError("The TV returned no packages. Reconnect and try again.")
    disabled = tv.packages("-d")
    third = tv.packages("-3")
    tiles = {p for p, _ in tv.activities("-a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER")}
    tiles |= {p for p, _ in tv.activities("-a android.intent.action.MAIN -c android.intent.category.LAUNCHER")}
    claim = {p for p, _ in tv.home_claimants()}
    home_pkg = (info["home"] or "").split("/")[0]
    inputs, settings = tv.handlers()
    know = knowledge()["packages"]

    rows, candidates = [], []
    for name in sorted(installed):
        d, c, note = know.get(name, [0, 0, ""])
        row = {"name": name, "state": "disabled" if name in disabled else "enabled",
               "note": note, "lists": d, "kept": c, "tile": name in tiles, "why": ""}
        if name == OUR_PKG:
            continue
        if CORE_RE.match(name) and not CORE_OK.match(name):
            row.update(cat="protected", why="Core Android: the TV may not boot without it")
        elif KEEP_RE.match(name):
            row.update(cat="protected", why="Needed for the Play Store, keyboard, remote or network")
        elif name in STOCK_LAUNCHERS or name == SETUP or name in claim or name == home_pkg:
            row.update(cat="home", why="Answers the Home button: handled in the Launcher step only")
        elif name in settings:
            row.update(cat="protected", why="The TV opens Settings with it")
        elif name in inputs:
            row.update(cat="protected", why="The TV opens its HDMI/AV inputs with it")
        elif name in tiles and (name in third or d < 5 * max(c, 1)):
            row["cat"] = "app"
        elif d >= 3 and d >= 5 * c:
            row["cat"] = "recommended"
        elif d >= 1:
            row["cat"] = "optional"
        elif name in third:
            row["cat"] = "app"
        elif c >= 1:
            row.update(cat="protected", why="Every tested list keeps it on")
        else:
            continue  # an unknown system package: nobody tested it, so it is not offered
        if row["cat"] in ("recommended", "optional", "app"):
            candidates.append(name)
        rows.append(row)

    drivers, unreadable, checked = tv.input_drivers(candidates)
    for row in rows:
        if row["name"] in drivers:
            row.update(cat="protected", why="Drives the HDMI/AV/tuner inputs: off means 'No Signal'")
        elif row["name"] in unreadable and row["cat"] in ("recommended", "optional"):
            row.update(cat="protected", why="Could not verify it is not an input driver, so it stays on")

    record = load_record(info["id"])
    return {"info": info, "packages": rows, "installed": len(installed), "disabled": len(disabled),
            "lists": knowledge()["lists"], "backup": record_summary(record),
            "launcher": {"installed": OUR_PKG in installed, "isHome": home_pkg == OUR_PKG,
                         "apk": APK.exists(), "stock": stock_launcher(tv, info["home"]),
                         "setup": SETUP in installed and SETUP not in disabled}}


# ---------------------------------------------------------------- backup record

def record_path(tv_id):
    return DATA / "backups" / f"{tv_id}.json"


def load_record(tv_id):
    try:
        return json.loads(record_path(tv_id).read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def record_summary(record):
    if not record:
        return None
    states = record["packages"].values()
    return {"created": record["created"], "home": record.get("home"), "packages": len(record["packages"]),
            "enabled": sum(1 for s in states if s == "enabled"), "path": str(record_path(record["id"]))}


def backup(tv, log=print):
    """Write down what the TV looks like before PureTV changes anything.

    The first record for a TV is the original and is never overwritten: a later run only adds
    packages that were not on the TV then, so a retry can never record a half-cleaned state as
    'how it was'. A full copy of the TV's settings goes next to it for reference.
    """
    info = tv.info()
    installed = tv.packages()
    if not installed:
        raise TVError("The TV returned no packages, so nothing was backed up and nothing will be changed.")
    disabled = tv.packages("-d")
    path = record_path(info["id"])
    record = load_record(info["id"])
    if record:
        added = [n for n in installed if n not in record["packages"]]
        for n in added:
            record["packages"][n] = "disabled" if n in disabled else "enabled"
        if added:
            _write(path, record)
        log(f"Keeping the original backup from {record['created']} (+{len(added)} new apps noted).")
        return record
    home = tv.home()
    if home and (home.startswith("android/") or home.split("/")[0] in NOT_LAUNCHERS or home.split("/")[0] == OUR_PKG):
        home = None   # the chooser, a wizard or ourselves is no launcher to put back
    record = {
        "version": 1, "id": info["id"], "created": time.strftime("%Y-%m-%d %H:%M:%S"),
        "host": tv.host, "model": f"{info['brand']} {info['model']}".strip(), "android": info["android"],
        "home": home,
        "settings": {k: tv.sh(f"settings get global {k}") for k in SCALES},
        "screensaver": tv.sh("settings get secure screensaver_components"),
        "dns": {k: tv.sh(f"settings get global {k}") for k in ("private_dns_mode", "private_dns_specifier")},
        "packages": {n: ("disabled" if n in disabled else "enabled") for n in sorted(installed)},
    }
    _write(path, record)
    dump = "\n".join(f"## settings list {ns}\n{tv.sh(f'settings list {ns}')}\n" for ns in ("global", "secure", "system"))
    path.with_suffix(".settings.txt").write_text(dump, encoding="utf-8")
    log(f"Backed up {len(installed)} apps, the Home screen and settings -> {path}")
    return record


def _write(path, record):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(record, indent=1), encoding="utf-8")
    os.replace(tmp, path)


# ---------------------------------------------------------------- clean

def clean(tv, names, animations=False, log=print, pause=PAUSE):
    """Disable the chosen packages for user 0. Returns {disabled, skipped, failed}."""
    names = list(dict.fromkeys(names))
    bad = [n for n in names if not PACKAGE_RE.fullmatch(n)]
    if bad:
        raise TVError(f"Refusing: not a package name: {', '.join(bad)}")
    skipped = {}

    def skip(group, why):
        for n in group:
            if n in names:
                names.remove(n)
                skipped[n] = why

    skip([n for n in names if (CORE_RE.match(n) and not CORE_OK.match(n)) or KEEP_RE.match(n)],
         "core package, never disabled")
    skip([OUR_PKG], "that is PureTV's own launcher")
    # Home guard: nothing that answers the Home button goes off here. The Launcher step hands Home
    # over in the right order and rolls back if it fails.
    home = tv.home()
    claim = {p for p, _ in tv.home_claimants()} | set(STOCK_LAUNCHERS) | {SETUP}
    if home:
        claim.add(home.split("/")[0])
    skip([n for n in names if n in claim], "answers the Home button (use the Launcher step)")
    inputs, settings = tv.handlers()
    if not settings:
        log("! Could not ask the TV which app opens Settings; after this, open Settings and an HDMI input once.")
    skip([n for n in names if n in settings], "the TV opens Settings with it")
    skip([n for n in names if n in inputs], "the TV opens its inputs with it")
    drivers, unreadable, checked = tv.input_drivers(names)
    if not checked:
        raise TVError("Could not check which apps drive the HDMI inputs, so nothing was changed. Restart the TV and try again.")
    skip(list(drivers), "drives the HDMI/AV/tuner inputs")
    skip(list(unreadable), "could not verify it is not an input driver")
    for n, why in skipped.items():
        log(f"- kept {n}: {why}")

    # The record comes first: no record, nothing disabled.
    backup(tv, log)

    already = tv.packages("-d")
    todo = [n for n in names if n not in already]
    for i in range(0, len(todo), BATCH):
        group = todo[i:i + BATCH]
        for n in group:
            answer = tv.sh(f"pm disable-user --user 0 {n}")
            log(f"off  {n}" if "disabled" in answer else f"!    {n}: {answer[:160] or 'no answer'}")
        if i + BATCH < len(todo):
            time.sleep(pause)
            tv.sh("true")   # still there?
    if animations:
        for k in SCALES:
            tv.sh(f"settings put global {k} 0.5")
        log("Animations set to 0.5x (snappier menus).")

    # Read the TV back instead of trusting silence.
    enabled = tv.packages("-e")
    failed = [n for n in names if n in enabled] if enabled else []
    done = [n for n in names if n not in failed]
    if failed:
        log(f"! The TV kept these on: {', '.join(failed)}")
    remember_disabled(tv, done)
    log(f"Done: {len(done)} switched off, {len(skipped)} kept for safety, {len(failed)} refused by the TV.")
    if tv.info()["adbSecure"] in ("0", ""):
        log("! This TV accepts ADB from any device on your network. Turn USB debugging off when you finish.")
    return {"disabled": done, "skipped": skipped, "failed": failed}


def trim_caches(tv, log=print):
    def free():
        m = re.findall(r"\s(\d+)\s+\d+%\s", tv.sh("df -k /data"))
        return int(m[-1]) if m else None
    before = free()
    tv.sh("pm trim-caches 999999M", timeout=300.0)
    after = free()
    gained = (after - before) // 1024 if before is not None and after is not None else None
    log(f"App caches cleared{'' if gained is None else f': {max(gained, 0)} MB freed'}.")
    return {"freedMb": gained}


# ---------------------------------------------------------------- launcher

def stock_launcher(tv, home=None):
    """The stock launcher to switch off once PureTV owns Home, or None to leave everything alone."""
    pkg = (home if home is not None else tv.home() or "").split("/")[0]
    if pkg in STOCK_LAUNCHERS:
        return pkg
    if pkg == "android":   # the chooser: only when exactly one known stock launcher claims Home
        found = [p for p in STOCK_LAUNCHERS if p in {c for c, _ in tv.home_claimants()}]
        return found[0] if len(found) == 1 else None
    return None


def push_file(tv, local, remote, log=print):
    data = Path(local).read_bytes()
    want = hashlib.md5(data).hexdigest()
    try:
        tv.dev.push(str(local), remote)
    except Exception:
        # Fallback for firmware whose sync service is broken: base64 chunks through the shell.
        tv.sh(f"rm -f {remote}")
        for i in range(0, len(data), 24 * 1024):
            chunk = base64.b64encode(data[i:i + 24 * 1024]).decode()
            tv.sh(f"echo {chunk} | base64 -d >> {remote}")
    got = tv.sh(f"md5sum {remote}").split(" ")[0]
    if got and got != want and not tv.demo:
        raise TVError("The launcher file did not arrive intact. Nothing was installed.")


def install_launcher(tv, disable_setup=False, log=print, keep_stock=False):
    """Install the PureTV launcher and make it Home; roll back if Home does not land on it.

    keep_stock leaves Google TV's own home enabled, as cleanyourtv's Google TV lists do where its
    profiles, Apps-only mode or boot safety depend on it (examples/acer-ar32hdigu2841at). Nothing is
    switched off then, so there is nothing to roll back.
    """
    if not APK.exists():
        fetch_apk(log)
    info = tv.info()
    if info["sdk"] and info["sdk"] < 23:
        raise TVError(f"This TV runs Android {info['android']}; the launcher needs Android 6 or newer. Cleaning still works.")
    backup(tv, log)
    stock = stock_launcher(tv, info["home"])
    home_pkg = (info["home"] or "").split("/")[0]
    if home_pkg and home_pkg != OUR_PKG and not stock and home_pkg != "android":
        log(f"Home belongs to {home_pkg}, which is not a stock launcher PureTV knows: it will be left on.")

    remote = "/data/local/tmp/purehome.apk"
    push_file(tv, APK, remote, log)
    out = tv.sh(f"pm install -r {remote}; rm -f {remote}", timeout=300.0)
    if "Success" not in out:
        raise TVError(f"The TV refused the launcher ({out[:200] or 'no answer'}). The stock Home was left untouched.")
    log("Launcher installed.")
    # lets PureTV hand its own updates to Android's installer later (Settings > Software Updates)
    tv.sh(f"appops set {OUR_PKG} REQUEST_INSTALL_PACKAGES allow")
    start = tv.sh(f"am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n {OUR_HOME}")
    if "Status: ok" not in start or re.search(r"^Error", start, re.M):
        raise TVError("The new launcher did not start, so the stock Home was left enabled.")
    tv.sh(f"cmd package compile -m speed -f {OUR_PKG}", timeout=300.0)

    if disable_setup and SETUP in tv.packages() and SETUP not in tv.packages("-d"):
        if tv.setup_owns_wifi():
            log("- kept the setup wizard: it owns the saved Wi-Fi network (disabling it would take the TV "
                "offline). Forget and re-join the network in Settings, then run this again.")
        else:
            tv.sh(f"pm disable-user --user 0 {SETUP}")
            log("Setup wizard switched off (phone casting may stop working on Chromecast devices).")
    if stock and keep_stock:
        tv.sh(f"cmd package set-home-activity --user 0 {OUR_HOME}")
        now = tv.home() or ""
        log(f"Google TV's home ({stock}) stays on, so its profiles keep working.")
        helper = False
        if now.startswith(OUR_PKG + "/"):
            log("PureTV is now the Home screen. Press Home on the remote.")
        else:
            log("Google's home still answers the Home button underneath; PureTV's Remote Helper hands it over.")
            helper = enable_helper(tv, log)
        record = load_record(info["id"])
        if record:
            record["kept_stock"] = stock
            _write(record_path(record["id"]), record)
        return {"home": now, "stock": stock, "kept": True, "homeOk": now.startswith(OUR_PKG + "/"),
                "helper": now.startswith(OUR_PKG + "/") or helper}
    if stock:
        tv.sh(f"pm disable-user --user 0 {stock}")
    tv.sh(f"cmd package set-home-activity --user 0 {OUR_HOME}")

    now = tv.home() or ""
    if not now.startswith(OUR_PKG + "/"):
        restored = False
        if stock:
            tv.sh(f"pm enable --user 0 {stock}")
            tv.sh(f"cmd package set-home-activity --user 0 $({HOME_QUERY} {stock} | tail -n 1)")
            restored = (tv.home() or "").startswith(stock + "/")
        hint = (" On Google TV the setup wizard often claims Home too: tick 'Also switch off the setup "
                "wizard' and install again." if SETUP in tv.packages("-e") else "")
        raise TVError(f"Home went to '{now or 'nothing'}' instead of PureTV. "
                      + (f"{stock} was switched back on, so the TV is as before." if restored
                         else "Press Restore to put the stock Home back.") + hint)
    if stock:
        log(f"Stock launcher {stock} switched off.")
    saver = tv.sh("settings get secure screensaver_components")
    if saver and saver.split("/")[0] in tv.packages("-d"):
        tv.sh(f"settings put secure screensaver_components {OUR_PKG}/.ClockDream")
    log("PureTV is now the Home screen. Press Home on the remote.")
    return {"home": now, "stock": stock}


# ---------------------------------------------------------------- Remote Helper (Home button, Google TV kept)

HELPER = f"{OUR_PKG}/{OUR_PKG}.HomeHelper"


def _a11y(tv):
    v = tv.sh("settings get secure enabled_accessibility_services")
    return [] if v in ("", "null") else [x for x in v.split(":") if x]


def enable_helper(tv, log=print):
    """Turn on PureTV's Remote Helper from the computer, so the Home button opens PureTV while Google TV's
    own home stays installed and on. Android 13+ blocks this switch for apps installed from a file
    ("restricted setting"); adb is allowed to lift that for PureTV alone."""
    tv.sh(f"appops set {OUR_PKG} ACCESS_RESTRICTED_SETTINGS allow")
    have = _a11y(tv)
    if not any(x.lower() in (HELPER.lower(), f"{OUR_PKG}/.HomeHelper".lower()) for x in have):
        new = ":".join(have + [HELPER])
        if not re.fullmatch(r"[\w.$/:]+", new):
            raise TVError("The TV's accessibility list looks unusual, so the Remote Helper was not switched on. "
                          "Turn it on by hand: Settings › System › Accessibility › PureTV Remote Helper.")
        tv.sh(f"settings put secure enabled_accessibility_services '{new}'")
    tv.sh("settings put secure accessibility_enabled 1")
    on = any(x.lower().startswith(OUR_PKG) for x in _a11y(tv))
    log("Remote Helper is on: the Home button opens PureTV." if on else
        "! The TV did not accept the Remote Helper. Turn it on by hand: Settings › System › Accessibility.")
    return on


def disable_helper(tv, log=print):
    have = _a11y(tv)
    keep = [x for x in have if not x.lower().startswith(OUR_PKG)]
    if keep == have:
        return False
    if keep:
        tv.sh(f"settings put secure enabled_accessibility_services '{':'.join(keep)}'")
    else:
        tv.sh("settings delete secure enabled_accessibility_services")
        tv.sh("settings put secure accessibility_enabled 0")
    log("Remote Helper switched off.")
    return True


def measure(tv):
    """Before/after numbers: memory in use and processes running."""
    mem = tv.sh("cat /proc/meminfo")
    val = lambda k: int((re.search(rf"^{k}:\s+(\d+)", mem, re.M) or [0, 0])[1]) // 1024
    total, avail = val("MemTotal"), val("MemAvailable")
    procs = tv.sh("ps -A -o NAME 2>/dev/null | wc -l") or tv.sh("ps -A | wc -l")
    third = len(tv.packages("-3"))
    return {"memTotalMB": total, "memUsedMB": max(0, total - avail), "processes": int(re.sub(r"\D", "", procs) or 0) - 1,
            "enabledApps": len(tv.packages("-e")), "disabledApps": len(tv.packages("-d")), "userApps": third}


# ---------------------------------------------------------------- restore

def restore(tv, log=print):
    """Put the TV back the way the backup says it was. What was already off before stays off."""
    info = tv.info()
    record = load_record(info["id"])
    if not record:
        raise TVError("There is no backup for this TV on this computer, so there is nothing to restore from.")
    disabled = tv.packages("-d")
    installed = tv.packages()
    back = [n for n, s in record["packages"].items()
            if s == "enabled" and n in disabled and n in installed and PACKAGE_RE.fullmatch(n)]
    for n in back:
        tv.sh(f"pm enable --user 0 {n}")
        log(f"on   {n}")
    home = record.get("home")
    if home and HOME_RE.fullmatch(home):
        tv.sh(f"cmd package set-home-activity --user 0 {home}")
    for k in SCALES:
        v = str(record.get("settings", {}).get(k, "")).strip()
        if v == "null":
            tv.sh(f"settings delete global {k}")
        elif SCALE_RE.fullmatch(v):
            tv.sh(f"settings put global {k} {v}")
    saver = record.get("screensaver") or ""
    if re.fullmatch(r"[\w.]+/[\w.$]+(,[\w.]+/[\w.$]+)*", saver):
        tv.sh(f"settings put secure screensaver_components {saver}")
    put_dns_back(tv, record)
    disable_helper(tv, log)

    still = [n for n in back if n in tv.packages("-d")]
    now = tv.home()
    short = lambda c: re.sub(r"^([^/]+)/\1\.", r"\1/.", c or "")
    home_ok = not home or short(now) == short(home)
    if still:
        log(f"! Still off (the TV refused): {', '.join(still)}")
    if not home_ok:
        log(f"! Home is {now}, the backup has {home}. Pick the Home app on the TV if asked.")
    log(f"Restored: {len(back) - len(still)} apps switched back on, Home "
        f"{'is back' if home and home_ok else 'left as it is'}. The PureTV launcher stays installed; "
        "remove it in Settings > Apps if you like.")
    return {"enabled": len(back) - len(still), "failed": still, "homeOk": home_ok}


# ---------------------------------------------------------------- what PureTV switched off

def remember_disabled(tv, names):
    """Add to the backup the packages PureTV itself switched off, for the check after an update."""
    record = load_record(tv.info()["id"])
    if not record:
        return
    mine = set(record.get("puretv_disabled", [])) | set(names)
    record["puretv_disabled"] = sorted(mine)
    _write(record_path(record["id"]), record)


def check_update(tv, fix=True, log=print):
    """After a firmware update: what came back on, and is Home still PureTV? (cleanyourtv's check.py)

    An update installs over the cleaned system, so listed packages come back and Home can return to
    the stock launcher. With fix, the packages go off again through the same guards as the first run.
    """
    record = load_record(tv.info()["id"])
    if not record or not record.get("puretv_disabled"):
        raise TVError("Nothing to check: this computer has no record of PureTV switching anything off on this TV.")
    mine = record["puretv_disabled"]
    enabled = tv.packages("-e")
    if not enabled:
        raise TVError("The TV listed no packages. Reconnect and try again.")
    back_on = [p for p in mine if p in enabled]
    installed = tv.packages()
    gone = [p for p in mine if p not in installed]
    home = tv.home() or ""
    has_launcher = OUR_PKG in installed
    kept = record.get("kept_stock")
    home_ok = (not has_launcher) or home.startswith(OUR_PKG + "/") or bool(kept and home.startswith(kept + "/"))
    if kept and home.startswith(kept + "/"):
        log(f"Google TV's home ({kept}) is kept on by choice, for profiles: that is expected.")
    log(f"Checked {len(mine)} apps PureTV switched off: {len(back_on)} came back on"
        + (f", {len(gone)} no longer installed" if gone else "") + ".")
    for p in back_on:
        log(f"back on  {p}")
    if not home_ok:
        log(f"! Home is {home or 'nothing'} now, not PureTV.")
    result = {"backOn": back_on, "homeOk": home_ok, "fixed": []}
    if fix and back_on:
        log("Switching them off again, with the same safety checks as the first time...")
        r = clean(tv, back_on, log=log)
        result["fixed"] = r["disabled"]
    if fix and not home_ok and has_launcher:
        tv.sh(f"cmd package set-home-activity --user 0 {OUR_HOME}")
        result["homeOk"] = (tv.home() or "").startswith(OUR_PKG + "/")
        log("Home handed back to PureTV." if result["homeOk"] else
            "! Home still goes to the stock launcher: run the Launcher step again.")
    if not back_on and home_ok:
        log("All good: nothing came back.")
    return result


# ---------------------------------------------------------------- ad-blocking DNS (optional)

DNS_HOST = "dns.adguard-dns.com"


def set_dns(tv, on, log=print):
    """Android's Private DNS pointed at AdGuard DNS, or put back as it was. Android 9+ only.

    Blocks ad and tracker domains in every app, not YouTube's ads. Skip it where the router already
    filters (Pi-hole, AdGuard Home, a VPN): Private DNS goes around the router's DNS.
    """
    info = tv.info()
    if info["sdk"] and info["sdk"] < 28:
        raise TVError(f"Private DNS needs Android 9 or newer; this TV runs Android {info['android']}.")
    record = backup(tv, log)
    if on:
        tv.sh(f"settings put global private_dns_specifier {DNS_HOST}")
        tv.sh("settings put global private_dns_mode hostname")
        ok = tv.sh("settings get global private_dns_mode") == "hostname"
        log(f"Ad-blocking DNS {'on' if ok else 'was refused by the TV'}: {DNS_HOST}. "
            "Open one app now to check it still plays.")
        return {"dns": ok}
    put_dns_back(tv, record)
    log("DNS is back to how it was before PureTV.")
    return {"dns": False}


def put_dns_back(tv, record):
    dns = record.get("dns") or {}
    mode, spec = str(dns.get("private_dns_mode", "")), str(dns.get("private_dns_specifier", ""))
    if re.fullmatch(r"[a-z_]+", mode) and mode != "null":
        tv.sh(f"settings put global private_dns_mode {mode}")
    else:
        tv.sh("settings put global private_dns_mode opportunistic")
    if re.fullmatch(r"[A-Za-z0-9.-]+", spec) and spec != "null":
        tv.sh(f"settings put global private_dns_specifier {spec}")
    elif tv.sh("settings get global private_dns_specifier") == DNS_HOST:
        tv.sh("settings delete global private_dns_specifier")


# ---------------------------------------------------------------- finding the TV, and closing ADB again

def _local_networks():
    """The /24 networks this computer is on (no packets sent: a UDP 'connect' only picks a route)."""
    ips = set()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))
        ips.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    try:
        ips.update(socket.gethostbyname_ex(socket.gethostname())[2])
    except OSError:
        pass
    nets = []
    for ip in ips:
        a = ipaddress.ip_address(ip)
        if a.is_private and not a.is_loopback and not ip.startswith("169.254."):
            net = ipaddress.ip_network(ip + "/24", strict=False)
            if net not in nets:
                nets.append(net)
    return nets, ips


def _arp_hosts():
    try:
        out = subprocess.run(["arp", "-a"], capture_output=True, text=True, timeout=5).stdout
    except (OSError, subprocess.SubprocessError):
        return []
    return re.findall(r"\b(\d{1,3}(?:\.\d{1,3}){3})\b", out)


def _port_open(host, port, timeout):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(timeout)
        return s.connect_ex((host, port)) == 0


def discover(timeout=0.4, ports=(5555,)):
    """TVs on this computer's Wi-Fi with network ADB open, as cleanyourtv's prompt finds them (ARP table,
    then the rest of the local network). Only this computer's own /24 networks are tried."""
    nets, mine = _local_networks()
    hosts = []
    for h in _arp_hosts():
        try:
            if any(ipaddress.ip_address(h) in n for n in nets) and h not in hosts:
                hosts.append(h)
        except ValueError:
            pass
    for n in nets:
        for h in n.hosts():
            if str(h) not in hosts:
                hosts.append(str(h))
    hosts = [h for h in hosts if h not in mine]

    def probe(h):
        for p in ports:
            if _port_open(h, p, timeout):
                return h, p
        return None

    with ThreadPoolExecutor(max_workers=96) as pool:
        found = [r for r in pool.map(probe, hosts) if r]
    # an open port is not a TV (some routers leave 5555 open): keep only what answers as ADB
    with ThreadPoolExecutor(max_workers=16) as pool:
        found = [r for r, ok in zip(found, pool.map(lambda r: adb_hello(*r), found)) if ok]

    def name(h):
        try:
            return socket.gethostbyaddr(h)[0].split(".")[0]
        except OSError:
            return ""

    with ThreadPoolExecutor(max_workers=16) as pool:
        names = list(pool.map(lambda r: name(r[0]), found))
    return {"networks": [str(n) for n in nets], "tvs": [{"host": h, "port": p, "name": nm} for (h, p), nm in zip(found, names)]}


def adb_closed(address):
    """Is network ADB closed again? Only a refused connection counts as closed (cleanyourtv adb-closed.py)."""
    host, port = split_host(address)
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(3)
        try:
            code = s.connect_ex((host, port))
        except OSError as e:
            return {"closed": None, "why": f"could not check ({e.__class__.__name__})"}
    if code == 0:
        return {"closed": False, "why": f"port {port} still answers: turn USB, Network and Wireless debugging off, then reboot the TV"}
    if code in (10061, 111, 61):   # connection refused on Windows, Linux, macOS
        return {"closed": True, "why": f"port {port} refuses connections: debugging is off"}
    return {"closed": None, "why": f"no answer from {host} (code {code}): is the TV on? A timeout is not proof the port is closed"}


def adb_hello(host, port, timeout=2.0):
    """True when the device answers ADB's opening message (CNXN) with AUTH or CNXN.

    Only the greeting is sent, never a key, so the TV shows no "Allow USB debugging?" prompt.
    """
    import struct
    payload = b"host::\x00"
    cmd = 0x4E584E43                                   # "CNXN"
    head = struct.pack("<6I", cmd, 0x01000001, 256 * 1024, len(payload), sum(payload) & 0xFFFFFFFF, cmd ^ 0xFFFFFFFF)
    try:
        with socket.create_connection((host, port), timeout=timeout) as s:
            s.settimeout(timeout)
            s.sendall(head + payload)
            reply = s.recv(24)
    except OSError:
        return False
    return reply[:4] in (b"AUTH", b"CNXN", b"STLS")


# ---------------------------------------------------------------- the launcher APK from the latest release

RELEASES = "https://github.com/advik-srivastava/puretv/releases/latest/download/purehome.apk"
LATEST_API = "https://api.github.com/repos/advik-srivastava/puretv/releases/latest"


def latest_apk_url():
    """The download link of the newest release's APK, or the old fixed name if GitHub can't be asked."""
    import urllib.request
    try:
        req = urllib.request.Request(LATEST_API, headers={"User-Agent": "PureTV", "Accept": "application/vnd.github+json"})
        with urllib.request.urlopen(req, timeout=30) as r:
            rel = json.loads(r.read(2_000_000))
        for a in rel.get("assets", []):
            url = a.get("browser_download_url", "")
            if a.get("name", "").endswith(".apk") and url.startswith("https://github.com/advik-srivastava/puretv/releases/download/"):
                return url
    except Exception:
        pass
    return RELEASES


def fetch_apk(log=print):
    """A copy of PureTV taken from GitHub's "Download ZIP" has no APK in it: take the latest release's."""
    import urllib.request
    log("Downloading the PureTV launcher from the latest GitHub release…")
    APK.parent.mkdir(parents=True, exist_ok=True)
    tmp = APK.with_suffix(".part")
    try:
        req = urllib.request.Request(latest_apk_url(), headers={"User-Agent": "PureTV"})
        with urllib.request.urlopen(req, timeout=60) as r, open(tmp, "wb") as out:
            total = 0
            while True:
                b = r.read(65536)
                if not b:
                    break
                total += len(b)
                if total > 64 * 1024 * 1024:
                    raise TVError("The download was larger than any PureTV launcher; stopped.")
                out.write(b)
    except TVError:
        raise
    except Exception as e:
        tmp.unlink(missing_ok=True)
        raise TVError(f"Could not download the launcher from GitHub ({e.__class__.__name__}). "
                      "Download purehome.apk from the Releases page into launcher/dist/ and try again.")
    with open(tmp, "rb") as f:
        if f.read(2) != b"PK":
            tmp.unlink(missing_ok=True)
            raise TVError("GitHub did not send an APK. Download purehome.apk from the Releases page into launcher/dist/.")
    os.replace(tmp, APK)
    log(f"Downloaded the launcher ({APK.stat().st_size // 1024} KB).")


# ---------------------------------------------------------------- Wireless debugging (pairing code)
# Some Google TV sets (Chromecast with Google TV, Google TV Streamer) keep port 5555 closed and only offer
# Wireless debugging with a pairing code. cleanyourtv's way: pair once with Google's own adb, connect,
# then `adb tcpip 5555` opens the usual port (until the TV restarts), and the rest works as normal.

def _tools_url():
    plat = {"win32": "windows", "darwin": "darwin"}.get(sys.platform, "linux")
    return f"https://dl.google.com/android/repository/platform-tools-latest-{plat}.zip"


def adb_binary(log=print, download=True):
    """Google's adb: already installed, in PureTV's folder, or downloaded once from Google (about 7 MB)."""
    import shutil
    exe = "adb.exe" if os.name == "nt" else "adb"
    mine = DATA / "platform-tools" / exe
    if mine.exists():
        return str(mine)
    found = shutil.which("adb")
    if found:
        return found
    if not download:
        return None
    import urllib.request, zipfile, io
    log("Downloading Google's platform-tools (adb, about 7 MB) from dl.google.com, once…")
    try:
        with urllib.request.urlopen(urllib.request.Request(_tools_url(), headers={"User-Agent": "PureTV"}), timeout=120) as r:
            data = r.read()
        DATA.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            for m in z.namelist():
                if m.startswith("platform-tools/") and ".." not in m:
                    z.extract(m, DATA)
    except Exception as e:
        raise TVError(f"Could not download Google's platform-tools ({e.__class__.__name__}). Install them from "
                      "developer.android.com/tools/releases/platform-tools and try again.")
    if os.name != "nt":
        mine.chmod(0o755)
    log("platform-tools ready.")
    return str(mine)


ADDR_RE = re.compile(r"\d{1,3}(?:\.\d{1,3}){3}:\d{2,5}")


def wireless_pair(pair_addr, code, connect_addr, log=print):
    """Pair with the TV's Wireless debugging, connect, and open port 5555. Returns the TV's address."""
    pair_addr, code, connect_addr = pair_addr.strip(), code.strip(), connect_addr.strip()
    if not ADDR_RE.fullmatch(pair_addr) or not ADDR_RE.fullmatch(connect_addr):
        raise TVError("Type both addresses the way the TV shows them, like 192.168.1.20:37099.")
    if not re.fullmatch(r"\d{6}", code):
        raise TVError("The pairing code is the six digits the TV shows.")
    if pair_addr.split(":")[0] != connect_addr.split(":")[0]:
        raise TVError("The two addresses should be the same TV (the same IP address before the colon).")
    adb = adb_binary(log)

    def run(*args, timeout=40):
        r = subprocess.run([adb, *args], capture_output=True, text=True, timeout=timeout)
        return (r.stdout + r.stderr).strip()

    out = run("pair", pair_addr, code)
    log(out[:200])
    if "Successfully paired" not in out:
        raise TVError("The TV did not accept the pairing. Open Wireless debugging › Pair device with pairing code "
                      "again and use the new code and port it shows.")
    out = run("connect", connect_addr)
    log(out[:200])
    if "connected" not in out or "cannot" in out or "failed" in out:
        raise TVError("Paired, but could not connect. Use the IP address and port shown on the Wireless debugging "
                      "screen itself (not the pairing one).")
    out = run("-s", connect_addr, "tcpip", "5555")
    log(out[:200])
    time.sleep(3)
    host = connect_addr.split(":")[0]
    if not _port_open(host, 5555, 3):
        raise TVError("The TV did not open port 5555. Try once more, or keep Wireless debugging open on the TV.")
    log(f"Port 5555 is open on {host}. It closes again when the TV restarts; that is normal.")
    return {"host": host}
