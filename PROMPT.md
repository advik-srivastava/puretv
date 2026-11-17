# PureTV: instructions for the AI agent

Someone gave you this repository (often just its link) and wants their TV set up. You will:

- back the TV up;
- switch off the ads, tracking and preloaded junk they don't use;
- make **PureTV** (a fast, tvOS-inspired Home screen) the TV's only Home screen;
- restart the TV;
- show a report card of what changed.

Everything is reversible, and nothing is ever uninstalled.

## How to talk to them

- **Ask with tap-to-answer questions.** Every decision below is a multiple-choice question. If your harness has a question tool (in Claude Code it's `AskUserQuestion`), use it with the exact options given, the recommended option first. If it doesn't, ask the same question as a short numbered list.
- **Keep messages short and friendly.** The person may not be technical. Say what you're doing in one line as you go, and never paste raw command output; summarise it.
- **One tool does all the TV work:** `puretv.py` in this repository. It talks to the TV over Wi-Fi with adb and has the safety guards built in.

## Rules (always)

1. **Ask before changing the TV.** Read-only commands need no permission: `find`, `info`, `measure`, `features`, `plan`, `report`, `closed`. A clear yes is needed first for `clean`, `install`, `dns`, `reboot`, `restore` and `check`; the questions below are that yes.
2. **Back up before any change.** `backup` must succeed before `clean` or `install`.
3. **Use only `puretv.py` on the TV.** Never uninstall anything. Never run your own `adb`, `pm` or `settings` commands, and never touch accounts, sign-ins or app data.
4. **If a command prints `ERROR:`, stop.** Explain it in plain words and follow the fix it gives. Never work around a safety guard.
5. **Keep their data on their computer.** The TV's data never leaves it.
6. **Be patient with slow commands.** Some wait for the person to press **Allow** on the TV, or pause between batches. Give a command up to 4 minutes (8 minutes for `reboot`) before deciding it's stuck.

## Step 1: Which TV?

Ask: **"What TV do you have?"**
- **Android TV or Google TV** — Sony, TCL, Hisense, Philips, Xiaomi, Acer, OnePlus, Chromecast with Google TV, Nvidia Shield…
- **Samsung (Tizen)**
- **LG, Roku, Fire TV or other**

If it's not Android or Google TV, explain kindly that PureTV needs Android's debugging port, which those TVs don't have, so it can't be installed. Offer three privacy tips for their TV's own settings: turn off ACR or "viewing information", turn off ad personalisation, and turn off auto-play on the home screen. Then stop.

## Step 2: Set up PureTV on this computer

Clone the repository, or download `https://github.com/advik-srivastava/puretv/archive/refs/heads/main.zip` and unzip it, then work inside the folder:

```bash
git clone https://github.com/advik-srivastava/puretv.git
cd puretv
```

Make its Python environment. It needs Python 3.9 or newer; if Python is missing, ask them to install it from python.org.

- **Windows**
  ```bash
  python -m venv .venv
  .venv\Scripts\python -m pip install -q -r requirements.txt
  ```
  `PT` below means `.venv\Scripts\python puretv.py`.
- **macOS / Linux**
  ```bash
  python3 -m venv .venv
  .venv/bin/python -m pip install -q -r requirements.txt
  ```
  `PT` below means `.venv/bin/python puretv.py`.

## Step 3: Turn on debugging (the one thing they do by hand)

Send this, word for word:

> **On your TV, please:**
> 1. Open **Settings › System › About**.
> 2. Scroll to **Android TV OS build** and press **OK** on it **7 times** until it says you are a developer.
> 3. Go back to **Settings › System › Developer options** and turn on **USB debugging**. If you also see **Network debugging** or **ADB debugging**, turn that on too.
> 4. Make sure the TV and this computer are on the **same Wi-Fi**.

Then ask: **"Is debugging on?"**
- **Yes, it's on**
- **I only see Wireless debugging**
- **I'm stuck**

If they only see Wireless debugging (Chromecast with Google TV, Google TV Streamer), ask them to open **Wireless debugging › Pair device with pairing code** and type three things in chat:
- the IP address and port on the pairing screen;
- the six-digit code;
- the IP address and port on the Wireless debugging screen itself.

Then run:

```bash
PT pair <pairing-IP:port> <code> <IP:port>
```

## Step 4: Find and connect

```bash
PT find
```

- **Several TVs found:** ask which is theirs, with one option per TV.
- **None found:** ask for the TV's IP address (**Settings › Network & Internet ›** their Wi-Fi **› IP address**).

Tell them: *"Your TV will ask **Allow USB debugging?** Tick **Always allow from this computer** and press **Allow**."* Then:

```bash
PT connect <IP>
```

```bash
PT info
```

Tell them which TV you're connected to.

## Step 5: Measure and back up (nothing changes yet)

```bash
PT measure before
```

```bash
PT smooth before
```

Before it runs, tell them: *"Now move around your TV's Home screen with the remote for 20 seconds: left, right, down and back up."* It counts how many frames Google TV's home draws late.

```bash
PT backup
```

Mention that the first backup is kept forever, so the TV can always be put back exactly as it was.

## Step 6: What do they use?

Ask both questions in one go, with multiple answers allowed. Tick voice and Chromecast by default. Explain that anything left unticked is switched off (never uninstalled), and that updates and accessibility always stay on.

**"Which of these do you use?"**
- **Voice assistant / mic** — Google Assistant, voice search, the remote's mic button
- **Chromecast built-in** — casting from a phone to the TV
- **Phone as a remote** — the Google TV app on a phone
- **Live TV channels** — the tuner, Live Channels and free channels

**"And these?"**
- **Play Games**
- **YouTube Music**
- **Ambient mode photos** — the photo screensaver
- **Play Movies store** — buying films in Google TV

These map to `voice`, `cast`, `phone`, `live`, `games`, `music`, `photos` and `movies` (`PT features` lists them). Then preview the plan:

```bash
PT plan --keep voice,cast
```

Summarise it in a few lines, grouped like "ads and tracking", "store-demo and partner apps" and "things you don't use", with the number for each.

The plan also lists **APPS ON THE TV**: apps they could open themselves, such as preloaded games, MX Player, Discovery+ and Netflix. These are never switched off unless they choose. Ask with multiple answers allowed, putting likely preloads first (games, demo apps, duplicate players): **"Any of these came with the TV and you never use?"** Add the ones they pick to the `clean` command by name.

Then ask:

**"Switch these off?"**
- **Yes, switch them off**
- **Show me the list first**
- **Not now**

On yes:

```bash
PT clean --plan --keep voice,cast --privacy --faster [apps they picked]
```

- `--privacy` switches off background Wi-Fi and Bluetooth scanning, a tracking surface that also uses power. Wi-Fi and the remote keep working.
- `--faster` makes the TV's own menus and app transitions snappier (animations at 0.5×).
- Both are recorded in the backup and undone by `restore`. Mention them in one line as part of the yes.

It works in small batches and checks the TV after each one. Lines starting `- kept` were protected by a safety guard; that's expected.

## Step 7: Make PureTV the Home screen

Ask: **"How should PureTV run?"**
- **PureTV only (Recommended)** — Google TV's home is switched off (not deleted), so only one Home screen runs. This is the fast option.
- **Keep Google TV underneath** — keeps Google TV profiles, but uses about 200 MB more and can feel slow on 2 GB TVs.

Before installing **PureTV only**, run `PT info`. If it says **`Wi-Fi saved by Google's setup wizard: YES`**, Google's setup wizard is holding the Wi-Fi and must let go first. Send this:

> One quick step on the TV: open **Settings › Network & Internet**, select your Wi-Fi, choose **Forget network**, then select the same network again and type its password. Your apps and sign-ins aren't affected. Tell me when it's back on Wi-Fi.

Then run `PT connect <IP>` again; the TV already trusts this computer, so there's no prompt. Run `PT info` to confirm it now says `no`. Then:

- **PureTV only:**
  ```bash
  PT install
  ```
- **Keep Google TV underneath:**
  ```bash
  PT install --keep-google-home
  ```

It downloads the newest PureTV from GitHub, installs it, and checks where the Home button lands; if anything is wrong, it puts Google TV straight back. It ends with a `RESULT:` line. If you get `ERROR: WIFI_OWNER`, do the Wi-Fi step above.

**If PureTV is already installed** (they ran this before), the same command switches it to the chosen setup.

## Step 8: Block ads inside apps?

Ask: **"Also block ads inside apps?"**
- **Yes, block ads (Recommended)** — sets Private DNS to AdGuard, which blocks many ads and trackers in apps. Restore undoes it.
- **No thanks**

On yes:

```bash
PT dns on
```

## Step 9: Restart and show the report card

Ask: **"Restart the TV now?"** This is when it gets fast, because the switched-off apps stop running.
- **Restart now (Recommended)**
- **Later**

On yes:

```bash
PT reboot
```

It waits for the TV and reconnects. If debugging doesn't come back (some TVs turn it off when they restart), ask them to turn it on again (Step 3) and run `connect`.

Ask them to press **Home** and go through PureTV's short setup on the TV: profile, glass style and wallpaper. Then:

```bash
PT measure after
```

Then ask them to move around PureTV for 20 seconds while this counts:

```bash
PT smooth after
```

```bash
PT report
```

Show the **report card**: Home-screen memory before and after with the percentage saved, Home-screen CPU before and after, and smoothness (frames drawn late, before and after; under 5% feels smooth). If you can show visuals (cards or a widget), show each number big, before → after, with "73% less memory"-style headlines. Otherwise use a small table. Then add, in a line or two:

- **Nothing was uninstalled.** Their apps, sign-ins and watch history are as they were.
- **PureTV updates itself.** It checks GitHub when the TV turns on and asks before installing.
- **To undo everything,** paste the repo link into Claude Code again and say *"restore my TV"*.

## Step 10: Close debugging

Ask them to turn **USB debugging** (and Network/Wireless debugging) **off** again in Developer options. Then:

```bash
PT closed
```

It should report that the port is closed.

## Other requests

- **"Restore my TV" / "undo PureTV"**
  1. Steps 2–4.
  2. Ask: **"Put everything back as it was?"** (Yes / Not now).
  3. On yes, run:
     ```bash
     PT restore
     ```
  4. Everything switched off comes back on, including Google TV's home, the original settings and DNS. PureTV stays installed but no longer opens with Home; they can remove it in Settings › Apps.
- **"The ads came back after a TV update"**
  1. Steps 2–4, then ask.
  2. Run:
     ```bash
     PT check
     ```
     It switches off again whatever the update turned back on, including Google TV's home.
- **"Is my TV still clean?"** Steps 2–4, then `PT info` and `PT report`.

## Troubleshooting

| What you see | What to do |
|---|---|
| `find` finds nothing | Same Wi-Fi? Debugging on? Some TVs need a restart after turning it on. Or ask for the IP (Step 4). |
| `ERROR: The TV refused the connection` | Debugging is off, or the TV only offers Wireless debugging (Step 3). |
| `ERROR: The TV did not allow this computer` | The Allow prompt was missed. Ask them to watch the TV, then `connect` again. |
| `ERROR: WIFI_OWNER` | Do the Forget network / join again step (Step 7), reconnect, then `install`. |
| `ERROR: Home went to …` | PureTV already rolled back, so the TV is as before. Send `PT info` output to the makers. Offer `install --keep-google-home` meanwhile. |
| The TV feels slow | `PT info`. If the Remote Helper is on, Google TV is still running underneath: offer **PureTV only** (Step 7), then `reboot`. |

Help from the makers: crazifiedstudios@gmail.com · crazifiedstudios.com
