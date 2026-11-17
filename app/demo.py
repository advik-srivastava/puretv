"""A pretend Google TV that answers the shell commands core.py sends, so the app can be tried
(and tested) without touching a real TV. State lives in memory only."""
import re

SYSTEM = """
android com.android.systemui com.android.shell com.android.tv.settings com.android.providers.settings
com.android.providers.media com.android.bluetooth com.android.vending com.google.android.gms
com.google.android.gsf com.google.android.webview com.google.android.inputmethod.latin
com.google.android.permissioncontroller com.google.android.packageinstaller
com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith
com.google.android.feedback com.android.tv.feedbackconsent com.google.android.tv.remotecontrol.logging
com.google.android.apps.tv.netoscope com.google.android.federatedcompute
com.google.android.ondevicepersonalization.services com.google.android.adservices.api
com.google.android.ext.adservices.api com.google.android.play.games com.google.android.videos
com.android.printspooler com.google.android.marvin.talkback com.google.android.apps.tv.dreamx
com.google.android.backdrop com.google.android.katniss com.google.android.apps.mediashell
com.google.android.partnersetup android.autoinstalls.config.google.gtvpai com.google.android.tts
com.google.android.tv.remote.service com.google.android.youtube.tv com.google.android.youtube.tvmusic
com.tcl.ttvs com.tcl.logkit com.tcl.usercenter com.tcl.channelplus com.tcl.tv.tclhome_passive
com.tcl.partnercustomizer com.tcl.messagebox com.tcl.waterfall.overseas com.tcl.exhibit com.tcl.esticker
com.tcl.hotelmenu com.tcl.guard com.tcl.smartalexa com.tcl.ui_mediaCenter com.tcl.gallery com.tcl.tvinput
com.tcl.tv com.tcl.vendor.mystery
""".split()
THIRD = "com.netflix.ninja com.amazon.amazonvideo.livingroom com.disney.disneyplus in.startv.hotstar com.spotify.tv.android".split()
TILES = THIRD + ["com.google.android.youtube.tv", "com.google.android.youtube.tvmusic", "com.google.android.videos",
                 "com.google.android.play.games", "com.tcl.channelplus", "com.tcl.ui_mediaCenter", "com.tcl.gallery",
                 "com.android.vending", "com.android.tv.settings"]
PROPS = {"ro.build.version.sdk": "34", "ro.serialno": "DEMO-TV-0001", "ro.product.brand": "TCL",
         "ro.product.model": "Smart TV Pro (demo)", "ro.build.version.release": "14", "ro.adb.secure": "1"}


class DemoTV:
    def __init__(self):
        self.installed = set(SYSTEM) | set(THIRD)
        self.disabled = {"com.google.android.tts"}          # something the factory shipped disabled
        self.home = "com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity"
        self.settings = {"window_animation_scale": "1.0", "transition_animation_scale": "1.0",
                         "animator_duration_scale": "1.0",
                         "screensaver_components": "com.google.android.apps.tv.dreamx/.service.Backdrop"}
        self.log = []

    def close(self):
        pass

    def push(self, local, remote):
        pass

    def _on(self, p):
        return p in self.installed and p not in self.disabled

    def _claims(self):
        c = []
        if self._on("com.google.android.tungsten.setupwraith"):
            c.append("com.google.android.tungsten.setupwraith/.RecoveryActivity")
        if self._on("com.google.android.apps.tv.launcherx"):
            c.append("com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity")
        if self._on("tv.pure.home"):
            c.append("tv.pure.home/.MainActivity")
        return c

    def _resolve_home(self):
        # Like a real Google TV: with the stock launcher off, the setup wizard's Home has a higher
        # priority than any downloaded launcher, so it wins until it is switched off too.
        if self._on("com.google.android.tungsten.setupwraith") and not self._on("com.google.android.apps.tv.launcherx"):
            return "com.google.android.tungsten.setupwraith/.RecoveryActivity"
        # The stock launcher wins Home while it is enabled.
        if self._on(self.home.split("/")[0]) and not (
                self.home.startswith("tv.pure.home") and self._on("com.google.android.apps.tv.launcherx")):
            return self.home
        c = self._claims()
        lx = [x for x in c if x.startswith("com.google.android.apps.tv.launcherx/")]
        return lx[0] if lx else (c[0] if c else "No activity found")

    def shell(self, cmd, read_timeout_s=None, **kw):
        self.log.append(cmd)
        if "for p in " in cmd:                                    # the input-driver loop
            names = cmd.split("for p in ", 1)[1].split("; do", 1)[0].split()
            marker = cmd.rsplit("echo ", 1)[1].strip()
            out = [n for n in names if n == "com.tcl.tvinput"]
            return "\n".join(out + [marker])
        if " $(" in cmd and "set-home-activity" in cmd:           # rollback to the stock launcher
            pkg = cmd.split("HOME ", 1)[1].split(" ", 1)[0]
            if self._on(pkg):
                self.home = next(c for c in self._claims() if c.startswith(pkg + "/"))
            return "Success"
        m = re.fullmatch(r"getprop (\S+)", cmd)
        if m:
            return PROPS.get(m.group(1), "")
        m = re.fullmatch(r"pm list packages ?(-\w)? ?(--user 0)?", cmd.strip())
        if m:
            flag = m.group(1)
            names = {"-d": self.disabled & self.installed, "-e": self.installed - self.disabled,
                     "-3": (set(THIRD) | {"tv.pure.home"}) & self.installed}.get(flag, self.installed)
            return "\n".join(f"package:{n}" for n in sorted(names))
        if "resolve-activity" in cmd:
            return "priority=0 preferredOrder=0\n" + self._resolve_home()
        if "query-activities" in cmd:
            if "category.HOME" in cmd:
                return "\n".join(self._claims())
            if "LEANBACK_LAUNCHER" in cmd:
                return "\n".join(f"{p}/.Main" for p in TILES if self._on(p))
            if "category.LAUNCHER" in cmd:
                return ""
            if "cursor.item/channel" in cmd:
                return "com.tcl.tv/.InputActivity"
            if "settings.SETTINGS" in cmd:
                return "com.android.tv.settings/.MainSettings"
        m = re.fullmatch(r"pm disable-user --user 0 (\S+)", cmd)
        if m:
            if m.group(1) == "com.tcl.vendor.mystery":
                return "java.lang.SecurityException: Shell cannot change component state"
            self.disabled.add(m.group(1))
            return f"Package {m.group(1)} new state: disabled-user"
        m = re.fullmatch(r"pm enable (?:--user 0 )?(\S+)", cmd)
        if m:
            self.disabled.discard(m.group(1))
            return f"Package {m.group(1)} new state: enabled"
        m = re.fullmatch(r"cmd package set-home-activity --user 0 (\S+)", cmd)
        if m:
            self.home = m.group(1)
            return "Success"
        m = re.fullmatch(r"settings get (?:global|secure) (\S+)", cmd)
        if m:
            return self.settings.get(m.group(1), "null")
        m = re.fullmatch(r"settings put (?:global|secure) (\S+) '?([^']+?)'?", cmd)
        if m:
            self.settings[m.group(1)] = m.group(2)
            return ""
        m = re.fullmatch(r"settings delete (?:global|secure) (\S+)", cmd)
        if m:
            self.settings.pop(m.group(1), None)
            return ""
        if cmd == "cat /proc/meminfo":   # fewer apps running = more memory free
            used = 520 + 13 * len(self.installed - self.disabled)
            return ("MemTotal:        1520000 kB\nMemFree:          200000 kB\n"
                    f"MemAvailable:    {max(150, 1484 - used) * 1024} kB")
        if cmd.startswith("ps -A"):
            return str(60 + len(self.installed - self.disabled))
        # what the Home screen costs: Google TV's home is heavy, PureTV's native home is light
        HOME_COST = {"com.google.android.apps.tv.launcherx": (232448, "41.0\n43.0\n42.0"), "tv.pure.home": (62464, "0.7\n0.0\n0.3")}
        m = re.fullmatch(r"dumpsys meminfo (\S+)", cmd)
        if m and m.group(1) in HOME_COST:
            return f"** MEMINFO in pid 4242 [{m.group(1)}] **\n           TOTAL PSS:   {HOME_COST[m.group(1)][0]}   TOTAL RSS:   300000"
        m = re.fullmatch(r"pidof (\S+)", cmd)
        if m:
            return "4242" if m.group(1) in HOME_COST and self._on(m.group(1)) else ""
        if cmd.startswith("top -b"):
            home = self._resolve_home().split("/")[0]
            return "12.0\n" + HOME_COST.get(home, (0, "1.0"))[1]
        if cmd.startswith("input keyevent"):
            return ""
        if cmd.startswith("settings list"):
            return "\n".join(f"{k}={v}" for k, v in self.settings.items())
        if cmd.startswith("pm install"):
            self.installed.add("tv.pure.home")
            return "Success"
        if cmd.startswith("am start"):
            return "Starting: Intent { ... }\nStatus: ok\nActivity: tv.pure.home/.MainActivity"
        if cmd.startswith("dumpsys wifi"):   # who saved the Wi-Fi (tests set demo_wifi_owner)
            return "cname=" + self.settings.get("demo_wifi_owner", "android.uid.system:1000")
        if cmd.startswith("df "):
            free = 2400000 if any("trim-caches" in c for c in self.log[:-1]) else 1650000
            return f"Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-9 11800000 9000000 {free} 80% /data"
        if cmd.startswith("md5sum"):
            return ""
        return ""
