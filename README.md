# PureTV

Clean up an Android TV / Google TV over Wi-Fi and give it a calm home screen.
Nothing is uninstalled, nothing is rooted, and one button puts everything back.

## Install

You need a Windows PC on the same Wi-Fi as the TV, with [Python 3](https://www.python.org/downloads/) installed.

1. **Get PureTV:** on GitHub press **Code › Download ZIP** and unzip it, or run
   `git clone https://github.com/advik-srivastava/puretv`.
2. **On the TV:** Settings › System › About › press OK on *Android TV OS build* seven times, then
   Settings › System › Developer options › turn on **USB debugging** (and *Network debugging* if shown).
3. **On the PC:** double-click **Start PureTV.bat**. Press **Find my TV automatically**, accept
   "Allow USB debugging?" on the TV, and follow the steps: Back up → Clean → Launcher → Finish.
4. **When done:** turn debugging off on the TV again and press *Check debugging is off*.

The TV launcher APK is also on the [Releases page](https://github.com/advik-srivastava/puretv/releases);
the PC app installs it for you, and after that the launcher updates itself from new releases.

**Try the TV design first:** run `Start PureTV.bat` and open <http://127.0.0.1:8765/tv.html>
(arrow keys, Enter, Esc, M for Control Center). That page is a design demo; the launcher on the TV is
still the simpler v1.0 until the tvOS-style launcher is built into the APK.

## Use it

1. Double-click **Start PureTV.bat** (first run sets itself up; needs Python 3).
2. Follow the five steps in the browser page that opens:
   **Connect → Back up → Clean → Launcher → Finish**.
   No TV nearby? Click *Try it with a demo TV* on the first screen.

## What each step does

| Step | On the TV |
|---|---|
| **Back up** | Records every app's on/off state, the Home app, animation and screensaver settings, plus a full settings dump, in `~/.puretv/backups/`. The first backup is the original and is never overwritten. |
| **Clean** | `pm disable-user --user 0` on the apps you tick, in small batches, then reads the TV back to confirm. Suggestions come from 494 community-tested lists (`app/knowledge.json`). |
| **Launcher** | Installs PureTV Home (24 KB, no internet permission), starts it, and only then switches the stock launcher off. If Home does not land on PureTV, the stock launcher is switched straight back on. |
| **Restore** | Re-enables exactly what was on at backup time, returns Home and settings. What was already off before stays off. |

## Following cleanyourtv's own steps

| cleanyourtv (PROMPT.md) | PureTV |
|---|---|
| Find the TV on the Wi-Fi | **Find my TV automatically**: ARP table, then this computer's /24 network, keeping only devices that answer ADB's greeting (no prompt on the TV) |
| Back up first | Every app's state, Home, animation, screensaver and Private DNS settings, plus a full settings dump |
| Ask before touching what you use | The Clean list and the TV's own setup checklist |
| Disable in small batches, read the TV back | Batches of 4 with a pause, then `pm list packages -e` |
| Keep launcherx on Google TV where profiles need it | **Keep Google TV's home** option on the Launcher step |
| Optional ad-blocking DNS | **Block ads inside apps** (AdGuard via Private DNS), undone by Restore |
| After a firmware update (check.py) | **Check after an update**: finds what came back on and switches it off again |
| Close network ADB (adb-closed.py) | **Check debugging is off**: only a refused connection counts as closed |

## Safety guards (never overridable from the UI)

- Core Android, Play services/Store, keyboard, Bluetooth, WebView and networking are locked.
- Anything that answers the Home button is only touched by the Launcher step.
- Apps that drive HDMI/AV/tuner inputs, or that open Settings or inputs, are kept on.
- The setup wizard is never disabled while it owns the saved Wi-Fi network.
- Package names are validated before they reach the TV's shell.

## Layout

```
app/core.py        the engine (ADB, backup, guards, clean, launcher handover, restore)
app/server.py      local web server, this computer only (127.0.0.1:8765)
app/demo.py        pretend TV for trying the app and for tests
app/ui/            the interface
launcher/          PureTV Home source (plain Java) and build.py (no Gradle)
tests/test_core.py end-to-end test against the demo TV
tools/build_knowledge.py  regenerates knowledge.json from a cleanyourtv checkout
```

Rebuild the launcher after editing it: `python launcher/build.py`.

On the TV: hold OK (or press Menu) on an app to move or hide it; hold OK on Settings for the launcher's own menu.

Credits and licence: see [NOTICE.md](NOTICE.md).

## Releasing an update

PureTV Home updates itself from your GitHub releases (Hold OK on Settings › *Check for updates*, or
*Automatic updates*, which checks once a day).

1. Put your repository in `launcher/res/values/strings.xml` › `update_repo` (for example `yourname/puretv`)
   once, and build and install that version on the TV.
2. For each new version: `PURETV_VERSION=1.1 python launcher/build.py`, then create a GitHub release with
   the tag `v1.1` and attach `launcher/dist/purehome.apk`.
3. The TV finds it, downloads it, refuses it unless it is signed with the same key
   (`~/.puretv/puretv.keystore` — keep it safe, a new key means updates are refused), and installs it with
   Android's own installer. The first update asks for confirmation on the TV.
