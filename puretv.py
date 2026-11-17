"""PureTV from the command line. This is what an AI agent (see PROMPT.md) or you can run, one step at a time.

    python puretv.py find                       look for TVs on this Wi-Fi with debugging on
    python puretv.py connect 192.168.1.14       first connection: press Allow on the TV
    python puretv.py pair IP:PORT CODE IP:PORT  TVs that only offer Wireless debugging with a code
    python puretv.py info                       the TV, its Home app, and whether PureTV is on it
    python puretv.py measure [before|after]     memory, processes and apps (kept for the report)
    python puretv.py backup                     record the TV as it is now (first backup is kept forever)
    python puretv.py features                   the everyday features to ask about (voice, casting, Live TV…)
    python puretv.py plan --keep voice,cast     exactly what would be switched off, keeping what they use
    python puretv.py clean --plan --keep voice,cast
                                                switch that plan off (never uninstalled, safety guards on)
    python puretv.py clean PKG [PKG ...]        switch named apps off
    python puretv.py suggest                    every candidate, grouped by how safe it is
    python puretv.py install [--keep-google-home]
                                                install PureTV Home as the TV's only Home screen (Google TV's
                                                home is switched off, not uninstalled); --keep-google-home keeps
                                                it running underneath and uses the Remote Helper instead
    python puretv.py reboot                     restart the TV, wait for it, reconnect and check Home
    python puretv.py check                      after a TV update: switch off what came back on
    python puretv.py dns on|off                 ad-blocking Private DNS (AdGuard), undone by restore
    python puretv.py smooth before|after        move around Home for 20 s; Android counts the frames drawn late
    python puretv.py report                     the report card: Home-screen memory, CPU and smoothness, before and after
    python puretv.py restore                    put everything back from the backup
    python puretv.py closed                     is debugging closed again? (run after turning it off)

Add --demo to any command to try it on a pretend TV. The TV last connected is remembered, or pass --tv IP.
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "app"))
for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import core  # noqa: E402

CURRENT = core.DATA / "current_tv.txt"
DEMO_STATE = core.DATA / "demo_tv.json"


def say(*a):
    print(*a, flush=True)


def open_tv(args):
    if args.demo:
        from demo import DemoTV
        fake = DemoTV()
        try:
            st = json.loads(DEMO_STATE.read_text(encoding="utf-8"))
            fake.installed, fake.disabled = set(st["installed"]), set(st["disabled"])
            fake.home, fake.settings = st["home"], st["settings"]
        except (OSError, ValueError, KeyError):
            pass
        return core.TV("demo", fake, demo=True)
    host = args.tv or (CURRENT.read_text().strip() if CURRENT.exists() else "")
    if not host:
        raise core.TVError("No TV yet. Run: python puretv.py find   then   python puretv.py connect <IP>")
    say(f"Connecting to {host}… (if the TV asks 'Allow USB debugging?', choose Always allow and press Allow)")
    return core.TV.connect(host, timeout=args.wait)


def close_tv(tv):
    if tv.demo:
        f = tv.dev
        core.DATA.mkdir(parents=True, exist_ok=True)
        DEMO_STATE.write_text(json.dumps({"installed": sorted(f.installed), "disabled": sorted(f.disabled),
                                          "home": f.home, "settings": f.settings}), encoding="utf-8")
    tv.close()


def measures_path(tv_id):
    return core.DATA / "backups" / f"{tv_id}.measure.json"


def show_measure(m):
    home = ""
    if m.get("homeMB") is not None:
        home = f"Home screen ({m['homePkg']}): {m['homeMB']} MB" + (f", CPU {m['homeCPU']}%" if m.get("homeCPU") is not None else "") + "\n"
    return home + (f"Whole TV: memory in use {m['memUsedMB']} MB of {m['memTotalMB']} MB · {m['processes']} processes · "
                   f"{m['enabledApps']} apps on, {m['disabledApps']} off")


def cmd_find(args):
    if args.demo:
        say("Found 1 TV:\n  demo  (pretend TV)")
        return
    say("Looking for TVs with debugging on, on this computer's Wi-Fi… (about 20 seconds)")
    r = core.discover()
    if not r["tvs"]:
        say("No TV answered. Check that the TV is on, on the same Wi-Fi as this computer, and that USB debugging "
            "(and Network debugging, if shown) is on in Developer options. Networks searched: " + ", ".join(r["networks"]))
        return
    say(f"Found {len(r['tvs'])} TV{'s' if len(r['tvs']) > 1 else ''}:")
    for t in r["tvs"]:
        say(f"  {t['host']}" + (f"  ({t['name']})" if t["name"] else ""))


def cmd_connect(args):
    if not args.demo:
        args.tv = args.host
    tv = open_tv(args)
    try:
        i = tv.info()
        if not args.demo:
            core.DATA.mkdir(parents=True, exist_ok=True)
            CURRENT.write_text(tv.host)
        say(f"Connected: {i['brand']} {i['model']} · Android {i['android']} · {tv.host}")
        say(f"Home app now: {i['home'] or 'unknown'}")
    finally:
        close_tv(tv)


def cmd_pair(args):
    r = core.wireless_pair(args.pair_addr, args.code, args.connect_addr, log=say)
    CURRENT.parent.mkdir(parents=True, exist_ok=True)
    CURRENT.write_text(r["host"] + ":5555")
    say(f"Paired. Next: python puretv.py connect {r['host']}")


def cmd_info(args):
    tv = open_tv(args)
    try:
        i = tv.info()
        pk = tv.packages()
        has = core.OUR_PKG in pk
        ver = ""
        if has:
            ver = (tv.sh(f"dumpsys package {core.OUR_PKG} | grep -m1 versionName") or "").split("=")[-1].strip()
        rec = core.load_record(i["id"])
        say(f"TV: {i['brand']} {i['model']} · Android {i['android']} (SDK {i['sdk']}) · id {i['id']}")
        say(f"Home app: {i['home'] or 'unknown'}")
        say(f"PureTV Home: {'installed ' + ver if has else 'not installed'}"
            + (f" · Remote Helper {'on' if has and any(x.lower().startswith(core.OUR_PKG) for x in core._a11y(tv)) else 'off'}" if has else ""))
        say(f"Apps: {len(pk)} installed, {len(tv.packages('-d'))} switched off")
        if core.SETUP in pk and core.SETUP not in tv.packages("-d"):
            say("Wi-Fi saved by Google's setup wizard: " + ("YES (forget and re-join the network on the TV before install)"
                                                            if tv.setup_owns_wifi() else "no"))
        say(f"Backup on this computer: {rec['created'] + ' · ' + str(core.record_path(i['id'])) if rec else 'none yet'}")
    finally:
        close_tv(tv)


def cmd_measure(args):
    tv = open_tv(args)
    try:
        i = tv.info()
        m = core.measure(tv)
        m.update(core.home_cost(tv))
        say(show_measure(m))
        if args.label:
            p = measures_path(i["id"])
            p.parent.mkdir(parents=True, exist_ok=True)
            data = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}
            if args.label == "before" and "before" in data:
                if data["before"].get("homeMB") is None and m.get("homeMB") is not None and m.get("homePkg") != core.OUR_PKG:
                    data["before"].update({k: m[k] for k in ("homePkg", "homeMB", "homeCPU")})
                    p.write_text(json.dumps(data, indent=1), encoding="utf-8")
                    say("(the earlier 'before' is kept; the Home screen's cost was added to it)")
                else:
                    say("(a 'before' measurement already exists for this TV and is kept)")
            else:
                data[args.label] = m
                p.write_text(json.dumps(data, indent=1), encoding="utf-8")
                say(f"Saved as '{args.label}'.")
    finally:
        close_tv(tv)


def cmd_backup(args):
    tv = open_tv(args)
    try:
        core.backup(tv, log=say)
    finally:
        close_tv(tv)


def cmd_suggest(args):
    tv = open_tv(args)
    try:
        s = core.scan(tv)
    finally:
        close_tv(tv)
    rows = [r for r in s["packages"] if r["state"] == "enabled"]
    groups = [("recommended", "RECOMMENDED to switch off (ads, tracking, preloaded junk; tested on many TVs)"),
              ("optional", "OPTIONAL (some people use these; ask first)"),
              ("app", "APPS THE OWNER MAY USE (switch off only if they say so)")]
    say(f"{s['info']['brand']} {s['info']['model']}: {s['installed']} apps, {s['disabled']} already off.")
    for cat, title in groups:
        items = [r for r in rows if r["cat"] == cat]
        if not items:
            continue
        say(f"\n{title}: {len(items)}")
        for r in items:
            note = (r["note"] or "").strip().replace("\n", " ")
            say(f"  {r['name']}" + (f"  — {note[:110]}" if note else ""))
    kept = [r for r in rows if r["cat"] in ("protected", "home")]
    say(f"\nKEPT ON for safety (never switched off): {len(kept)} (core Android, Play, keyboard, network, inputs, Home)")
    if args.json:
        say(json.dumps({c: [r["name"] for r in rows if r["cat"] == c] for c, _ in groups}))


def cmd_features(args):
    say("Ask the owner which of these they use (several can be ticked). What they don't tick is switched off;")
    say("software updates always stay on. Pass the ticked ones to plan/clean as --keep a,b,c")
    for k, (label, _) in core.FEATURES.items():
        say(f"  {k:7} {label}")


def keep_list(args):
    return [k.strip() for k in (args.keep or "").split(",") if k.strip()]


def cmd_plan(args):
    tv = open_tv(args)
    try:
        s, off, kept = core.plan(tv, keep_list(args))
    finally:
        close_tv(tv)
    say(f"{s['info']['brand']} {s['info']['model']}: {len(off)} apps would be switched off (never uninstalled).")
    for r in off:
        note = (r["note"] or "").strip().replace("\n", " ")
        say(f"  off   {r['name']}" + (f"  — {note[:100]}" if note else ""))
    if kept:
        say(f"Kept on because they use it: {len(kept)}")
        for r in kept:
            say(f"  keep  {r['name']}  ({r['keep']})")
    say("Apps they open themselves (YouTube, Netflix, Prime Video…) and everything Android needs stay on.")
    say("PLAN: " + " ".join(r["name"] for r in off))


def cmd_clean(args):
    tv = open_tv(args)
    try:
        if not core.load_record(tv.info()["id"]):
            core.backup(tv, log=say)
        names = list(args.packages or [])
        if args.plan:
            _, off, kept = core.plan(tv, keep_list(args))
            names += [r["name"] for r in off]
            say(f"Switching off {len(off)} apps (keeping {len(kept)} for features they use)…")
        if not names:
            raise core.TVError("Nothing to switch off. Pass package names, or --plan --keep …")
        r = core.clean(tv, names, animations=args.faster, log=say, pause=0 if tv.demo else core.PAUSE)
        core.remember_disabled(tv, r["disabled"])
    finally:
        close_tv(tv)


def cmd_install(args):
    tv = open_tv(args)
    try:
        keep = args.keep_google_home and not args.replace_google_home
        r = core.install_launcher(tv, disable_setup=not keep, log=say, keep_stock=keep)
        ok = r.get("helper") if r.get("kept") else (r.get("home") or "").startswith(core.OUR_PKG)
        say("RESULT: the Home button opens PureTV." if ok else "RESULT: PureTV is installed; open it from the Apps row.")
    finally:
        close_tv(tv)


def cmd_reboot(args):
    import time
    tv = open_tv(args)
    host = tv.host
    try:
        tv.sh("reboot", timeout=5)
    except core.TVError:
        pass   # the connection drops as the TV goes down
    close_tv(tv)
    if tv.demo:
        say("Restarted (pretend TV).")
        return
    say("The TV is restarting. Waiting for it to come back (up to 4 minutes)…")
    h, port = core.split_host(host)
    time.sleep(20)
    deadline = time.time() + 240
    while time.time() < deadline and not core._port_open(h, port, 2):
        time.sleep(5)
    if not core._port_open(h, port, 2):
        raise core.TVError("The TV restarted but debugging did not come back on the network. That is normal on some TVs: "
                           "turn USB/Network debugging on again in Developer options (or use Wireless debugging pairing), "
                           "then run connect again.")
    tv = None
    for _ in range(24):   # adb answers before Android has finished starting
        try:
            t = core.TV.connect(host, timeout=20)
            if t.prop("sys.boot_completed") == "1":
                tv = t
                break
            t.close()
        except core.TVError:
            pass
        time.sleep(5)
    if tv is None:
        raise core.TVError("The TV is back on the network but has not finished starting. Wait a minute, then run info.")
    try:
        time.sleep(8)
        home = tv.home() or ""
        say("The TV is back. Home app: " + home)
        say("RESULT: PureTV is the Home app." if home.startswith(core.OUR_PKG) else
            "RESULT: Home is not PureTV after the restart. Run: install (it will repair it)")
    finally:
        tv.close()


def cmd_smooth(args):
    import time
    tv = open_tv(args)
    try:
        pkg = core.smooth_start(tv)
        if not pkg:
            raise core.TVError("Couldn't tell which app is the Home screen. Press Home on the remote and try again.")
        secs = 0 if tv.demo else args.seconds
        say(f"Now move around the Home screen with the remote for {args.seconds} seconds: left, right, down into the apps and back up.")
        for left in range(secs, 0, -5):
            say(f"  {left} s…")
            time.sleep(min(5, left))
        r = core.smooth_read(tv, pkg)
        if not r.get("frames"):
            say("No frames were drawn: nothing moved on the Home screen. Run it again and press the arrows while it counts.")
            return
        say(f"Home screen ({pkg}): {r['frames']} frames drawn, {r['jankPct']}% late" + (f", 90% within {r['p90ms']} ms" if r.get("p90ms") else ""))
        p = measures_path(tv.info()["id"])
        p.parent.mkdir(parents=True, exist_ok=True)
        data = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}
        data.setdefault(args.label, {}).update(r)
        p.write_text(json.dumps(data, indent=1), encoding="utf-8")
        say(f"Saved with '{args.label}'.")
    finally:
        close_tv(tv)


def cmd_check(args):
    tv = open_tv(args)
    try:
        core.check_update(tv, fix=True, log=say)
    finally:
        close_tv(tv)


def cmd_dns(args):
    tv = open_tv(args)
    try:
        core.set_dns(tv, args.state == "on", log=say)
    finally:
        close_tv(tv)


def cmd_report(args):
    tv = open_tv(args)
    try:
        i = tv.info()
        now = core.measure(tv)
    finally:
        close_tv(tv)
    p = measures_path(i["id"])
    before = (json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}).get("before")
    if not before:
        say("Now: " + show_measure(now) + "\n(no 'before' measurement was saved, so there is nothing to compare)")
        return
    d = lambda k: now[k] - before[k]
    after = json.loads(p.read_text(encoding="utf-8")).get("after") or {}
    hb, ha = before.get("homeMB"), (after.get("homeMB") if after.get("homePkg") == core.OUR_PKG else None)
    if hb and ha:
        say("REPORT CARD")
        say(f"Home-screen memory: {hb} MB ({before.get('homePkg')}) → {ha} MB (PureTV) · {round(100 * (hb - ha) / hb)}% less")
        cb, ca = before.get("homeCPU"), after.get("homeCPU")
        if cb is not None and ca is not None:
            pct = f"{round(100 * (cb - ca) / cb)}% less" if cb > 0 else ""
            say(f"Home-screen CPU: {cb:g}% → {'under 1' if ca < 1 else f'{ca:g}'}% · {pct} (share of one core, Home idle on screen)")
        jb, ja = before.get("jankPct"), after.get("jankPct")
        if jb is not None and ja is not None:
            say(f"Smoothness: {jb:g}% of frames late → {ja:g}% (moving around Home; under 5% feels smooth)")
        say("")
    elif hb:
        say(f"Home screen before: {hb} MB. Run 'measure after' once PureTV is the Home screen for the comparison.")
    say(f"Memory in use: {before['memUsedMB']} MB → {now['memUsedMB']} MB ({d('memUsedMB'):+d} MB)")
    say(f"Processes running: {before['processes']} → {now['processes']} ({d('processes'):+d})")
    say(f"Apps switched off: {before['disabledApps']} → {now['disabledApps']} ({d('disabledApps'):+d})")


def cmd_restore(args):
    tv = open_tv(args)
    try:
        core.restore(tv, log=say)
    finally:
        close_tv(tv)


def cmd_closed(args):
    host = args.tv or (CURRENT.read_text().strip() if CURRENT.exists() else "")
    if not host:
        raise core.TVError("No TV address known. Pass --tv IP.")
    r = core.adb_closed(host)
    say(("CLOSED: " if r["closed"] else "STILL OPEN: " if r["closed"] is False else "UNKNOWN: ") + r["why"])


def main():
    ap = argparse.ArgumentParser(prog="puretv", description="PureTV: clean an Android TV / Google TV and give it a calm Home screen.")
    ap.add_argument("--tv", help="the TV's IP address (default: the last one connected)")
    ap.add_argument("--demo", action="store_true", help="use a pretend TV")
    ap.add_argument("--wait", type=int, default=60, help="seconds to wait for the TV (and for Allow)")
    common = argparse.ArgumentParser(add_help=False)   # the same options also work after the command
    common.add_argument("--tv", default=argparse.SUPPRESS)
    common.add_argument("--demo", action="store_true", default=argparse.SUPPRESS)
    common.add_argument("--wait", type=int, default=argparse.SUPPRESS)
    sub = ap.add_subparsers(dest="cmd", required=True)
    _add = sub.add_parser
    sub.add_parser = lambda *a, **k: _add(*a, parents=[common], **k)
    sub.add_parser("find")
    c = sub.add_parser("connect"); c.add_argument("host", nargs="?", default="")
    c = sub.add_parser("pair"); c.add_argument("pair_addr"); c.add_argument("code"); c.add_argument("connect_addr")
    sub.add_parser("info")
    c = sub.add_parser("measure"); c.add_argument("label", nargs="?", choices=["before", "after"])
    sub.add_parser("backup")
    c = sub.add_parser("suggest"); c.add_argument("--json", action="store_true")
    c = sub.add_parser("clean"); c.add_argument("packages", nargs="*"); c.add_argument("--faster", action="store_true", help="also set animations to 0.5x")
    c.add_argument("--plan", action="store_true", help="switch off the plan (see plan)"); c.add_argument("--keep", default="", help="features they use, e.g. voice,cast")
    sub.add_parser("features")
    c = sub.add_parser("plan"); c.add_argument("--keep", default="", help="features they use, e.g. voice,cast")
    c = sub.add_parser("install")
    c.add_argument("--keep-google-home", action="store_true", help="keep Google TV's home running underneath (Remote Helper)")
    c.add_argument("--replace-google-home", action="store_true", help="the default: PureTV is the only Home screen")
    sub.add_parser("reboot")
    c = sub.add_parser("smooth"); c.add_argument("label", choices=["before", "after"]); c.add_argument("--seconds", type=int, default=20)
    sub.add_parser("check")
    c = sub.add_parser("dns"); c.add_argument("state", choices=["on", "off"])
    sub.add_parser("report")
    sub.add_parser("restore")
    sub.add_parser("closed")
    args = ap.parse_args()
    try:
        globals()["cmd_" + args.cmd](args)
    except core.TVError as e:
        say(f"ERROR: {e}")
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)


if __name__ == "__main__":
    main()
