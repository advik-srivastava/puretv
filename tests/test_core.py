"""End-to-end run of the engine against the demo TV: backup -> clean -> launcher -> restore.

    python tests/test_core.py
"""
import os
import sys
import tempfile
from pathlib import Path

os.environ["PURETV_HOME"] = tempfile.mkdtemp(prefix="puretv-test-")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app"))
import core  # noqa: E402
from demo import DemoTV  # noqa: E402

fake = DemoTV()
tv = core.TV("demo", fake, demo=True)
before = (set(fake.disabled), fake.home, dict(fake.settings))
log = []

s = core.scan(tv)
cats = {r["name"]: r["cat"] for r in s["packages"]}
assert cats["com.google.android.feedback"] == "recommended", cats["com.google.android.feedback"]
assert cats["com.tcl.logkit"] == "recommended"
assert cats["com.google.android.katniss"] == "optional"
assert cats["com.netflix.ninja"] == "app"
assert cats["com.google.android.gms"] == "protected"
assert cats["com.android.systemui"] == "protected"
assert cats["com.tcl.tvinput"] == "protected", "input driver must be protected"
assert cats["com.tcl.tv"] == "protected", "input handler must be protected"
assert cats["com.google.android.apps.tv.launcherx"] == "home"
assert cats["com.google.android.tungsten.setupwraith"] == "home"
assert "com.tcl.vendor.mystery" not in cats, "unknown system packages are not offered"
assert s["backup"] is None

# Clean: everything recommended, plus things that must be refused by the guards.
pick = [n for n, c in cats.items() if c == "recommended"]
evil = ["com.android.systemui", "com.google.android.gms", "com.google.android.apps.tv.launcherx",
        "com.google.android.tungsten.setupwraith", "com.tcl.tvinput", "com.tcl.tv", "com.android.tv.settings",
        "com.tcl.vendor.mystery"]
r = core.clean(tv, pick + evil, animations=True, log=log.append, pause=0)
for n in evil[:-1]:
    assert n in r["skipped"] and n not in fake.disabled, n
assert r["failed"] == ["com.tcl.vendor.mystery"], r["failed"]
assert set(pick) <= fake.disabled and len(pick) > 10, len(pick)
assert fake.settings["window_animation_scale"] == "0.5"
rec = core.load_record("DEMO-TV-0001")
assert rec["packages"]["com.google.android.feedback"] == "enabled"
assert rec["packages"]["com.google.android.tts"] == "disabled"
assert rec["home"].startswith("com.google.android.apps.tv.launcherx/")
try:
    core.clean(tv, ["x; reboot"], log=log.append, pause=0)
    raise AssertionError("shell syntax accepted")
except core.TVError:
    pass

# A second backup must not overwrite the original with the cleaned state.
core.backup(tv, log.append)
assert core.load_record("DEMO-TV-0001")["packages"]["com.google.android.feedback"] == "enabled"

# Launcher: needs an APK file to exist.
made = not core.APK.exists()
if made:
    core.APK.parent.mkdir(parents=True, exist_ok=True)
    core.APK.write_bytes(b"test")
try:
    out = core.install_launcher(tv, log=log.append)
    assert out["home"].startswith("tv.pure.home/") and "com.google.android.apps.tv.launcherx" in fake.disabled
finally:
    if made:
        core.APK.unlink()

# Rollback: a TV where Home refuses to move must end with the stock launcher back on.
fake2 = DemoTV()
tv2 = core.TV("demo2", fake2, demo=True)
orig = fake2.shell
fake2.shell = lambda c, **k: "Success" if c.startswith("cmd package set-home-activity --user 0 tv.pure") else orig(c, **k)
core.APK.parent.mkdir(parents=True, exist_ok=True)
made = not core.APK.exists()
if made:
    core.APK.write_bytes(b"test")
try:
    core.install_launcher(tv2, log=log.append)
    raise AssertionError("handover should have failed")
except core.TVError as e:
    assert "switched back on" in str(e), e
    assert "com.google.android.apps.tv.launcherx" not in fake2.disabled
finally:
    if made:
        core.APK.unlink()

# The backup remembers what PureTV switched off.
rec = core.load_record("DEMO-TV-0001")
assert set(pick) <= set(rec["puretv_disabled"]), "switched-off list not recorded"
assert "dns" in rec, "DNS settings not backed up"

# A firmware update switches three of them back on: the check finds them and switches them off again.
updated = sorted(pick)[:3]
for p in updated:
    fake.disabled.discard(p)
r = core.check_update(tv, fix=False, log=log.append)
assert sorted(r["backOn"]) == updated and not r["fixed"], r
r = core.check_update(tv, fix=True, log=log.append)
assert sorted(r["fixed"]) == updated and all(p in fake.disabled for p in updated), r
assert core.check_update(tv, fix=False, log=log.append)["backOn"] == []

# Ad-blocking DNS on, then off again: back to how the backup found it.
assert core.set_dns(tv, True, log.append)["dns"] is True
assert fake.settings["private_dns_mode"] == "hostname" and fake.settings["private_dns_specifier"] == core.DNS_HOST
core.set_dns(tv, False, log.append)
assert fake.settings["private_dns_mode"] == "opportunistic", fake.settings
core.set_dns(tv, True, log.append)   # Restore must also put DNS back

# Keeping Google TV's home (profiles): nothing is switched off, nothing to roll back.
fake3 = DemoTV()
tv3 = core.TV("demo3", fake3, demo=True)
made = not core.APK.exists()
if made:
    core.APK.parent.mkdir(parents=True, exist_ok=True)
    core.APK.write_bytes(b"test")
try:
    r = core.install_launcher(tv3, log=log.append, keep_stock=True)
    assert r["kept"] and "com.google.android.apps.tv.launcherx" not in fake3.disabled, r
finally:
    if made:
        core.APK.unlink()

# Closing ADB again: a refused connection is the only proof.
assert core.adb_closed("127.0.0.1:1")["closed"] is True
nets, _ = core._local_networks()
assert all(n.prefixlen == 24 for n in nets)

# Restore: exactly the state before, including what the factory had disabled, and DNS as it was.
r = core.restore(tv, log.append)
keys = [*core.SCALES, "screensaver_components"]
assert (fake.disabled, fake.home) == before[:2], (fake.disabled ^ before[0], fake.home)
assert {k: fake.settings[k] for k in keys} == {k: before[2][k] for k in keys}, fake.settings
assert fake.settings["private_dns_mode"] == "opportunistic", fake.settings
assert r["homeOk"] and not r["failed"]
print("\n".join(log[-6:]))
print("ALL OK")
