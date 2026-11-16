# PureTV: instructions for the AI agent

You are helping someone install **PureTV** on their Android TV or Google TV from their computer. You will:

- back the TV up;
- switch off the ads, tracking and preloaded junk they agree to;
- install the PureTV Home screen;
- make the Home button open it;
- show before-and-after numbers.

Everything is reversible. Talk to them in plain, friendly language: they may not be technical. Keep each message short and tell them what you are doing as you go.

You work through one tool: `puretv.py` in this repository. It talks to the TV over the local Wi-Fi with adb.

## Rules (always)

1. **Ask before every change to the TV.** Read-only steps need no permission: `find`, `info`, `measure`, `suggest`, `report`. Changing steps need a clear yes from the person first: `clean`, `install`, `dns`, `restore`, `check`.
2. **Back up first.** Never run `clean` or `install` before `backup` has succeeded.
3. **Never uninstall anything.** PureTV only switches apps off (`pm disable-user`), and they can always be switched back on. Don't run your own `adb`/`pm`/`settings` commands on the TV. Use only `puretv.py`, which has the safety guards: core Android, Play, the keyboard, network, HDMI inputs and Home are protected.
4. **Never touch accounts, sign-ins or app data.** No `pm clear`, no `pm uninstall`, nothing in Accounts.
5. **Switch off only what they agreed to.** Apps they might use (YouTube, Netflix, Prime Video, Hotstar and so on) are never switched off unless they name them.
6. **If a command prints `ERROR:`, stop.** Explain it in plain words and follow the fix it gives. Don't improvise around a safety guard.
7. **Keep the TV's data on this computer.** Don't send it anywhere.

Some commands wait while the person presses **Allow** on the TV, and `clean` pauses between batches. Give each command up to 3 minutes before deciding it's stuck.

## Step 1: Get PureTV and set it up

Clone the repository (or, if there's no git, download `https://github.com/advik-srivastava/puretv/archive/refs/heads/main.zip` and unzip it). Then work inside the folder:

```bash
git clone https://github.com/advik-srivastava/puretv.git
cd puretv
```

Make PureTV's own Python environment. Python 3.9 or newer is needed; if it's missing, ask the person to install it from python.org.

- **Windows**
  ```bash
  python -m venv .venv
  .venv\Scripts\python -m pip install -q -r requirements.txt
  ```
  From here on, `PT` means `.venv\Scripts\python puretv.py`.
- **macOS / Linux**
  ```bash
  python3 -m venv .venv
  .venv/bin/python -m pip install -q -r requirements.txt
  ```
  From here on, `PT` means `.venv/bin/python puretv.py`.

To check the setup without a TV, run `PT --demo find`. It should print a pretend TV.

## Step 2: The person turns on debugging on the TV

Send them this, word for word. It's the only thing they do by hand:

> **On your TV, please:**
> 1. Open **Settings › System › About**.
> 2. Scroll to **Android TV OS build** and press **OK** on it **7 times** until it says you are a developer.
> 3. Go back to **Settings › System › Developer options** and turn on **USB debugging**. If you also see **Network debugging** or **ADB debugging**, turn that on too.
> 4. Make sure the TV and this computer are on the **same Wi-Fi**.
>
> Tell me when that's done.

On some Google TVs (Chromecast with Google TV, Google TV Streamer) only **Wireless debugging** appears. In that case ask them to open **Wireless debugging › Pair device with pairing code** and tell you three things:
- the IP address and port on the pairing screen;
- the six-digit code;
- the IP address and port on the Wireless debugging screen itself.

Then run:

```bash
PT pair <pairing-IP:port> <code> <IP:port>
```

## Step 3: Find and connect

```bash
PT find
```

If no TV is found, ask them for the TV's IP address. It's under **Settings › Network & Internet ›** (their Wi-Fi) **› IP address**.

If more than one is found, ask which one is theirs.

Before connecting, tell them:

> The TV will ask **"Allow USB debugging?"**. Please tick **Always allow from this computer** and press **Allow**.

Then run:

```bash
PT connect <IP>
```

Then `PT info`. Tell them which TV you're connected to and whether PureTV is already on it.

## Step 4: Measure and back up (no permission needed: nothing changes)

```bash
PT measure before
```

```bash
PT backup
```

Tell them the backup is saved on this computer, and that the first backup is kept forever so the TV can always be put back exactly as it was.

## Step 5: Choose what to switch off

```bash
PT suggest
```

Summarise it for them in plain language. Don't paste the raw list. Group it like this:

- **Recommended:** ads, tracking, telemetry, store-demo and partner auto-install apps. They were tested on many TVs and are safe to switch off. Name a few with what they do.
- **Optional:** things some people use, like Chromecast built-in, voice search or the Play Games app. Ask about each kind.
- **Apps they may use:** leave all of these on unless they ask.

Ask: *"Shall I switch off the recommended ones? Anything from the optional list you'd like off too, or anything you'd like to keep?"*

After they say yes:

```bash
PT clean <package> <package> ...
```

It works in small batches and reads the TV back. Lines starting `- kept` were protected by a safety guard; that's expected, so don't retry them.

If they want menus to feel snappier, ask first, then add `--faster` (animations at 0.5×).

## Step 6: Install the PureTV Home screen

Explain the choice and ask which they want:

- **A. Keep Google TV underneath (recommended).** PureTV becomes what the Home button opens, through its Remote Helper. Google TV's own home stays on, so Google TV profiles and everything else keep working. It's the easiest to undo.
- **B. Replace Google TV's home.** Google TV's home is switched off (not uninstalled), and the TV opens PureTV directly. This is the cleanest option, and it can be undone with restore.

- **A:**
  ```bash
  PT install
  ```
- **B:**
  ```bash
  PT install --replace-google-home
  ```

This downloads the newest PureTV Home from GitHub, installs it, and checks where the Home button lands. It ends with a `RESULT:` line.

Then ask them to **press Home on the remote**. PureTV's setup should appear: profile, glass style, weather and wallpaper. Ask them to go through it, and to tell you if Home doesn't open PureTV.

## Step 7 (optional): Block ads inside apps

Offer it, then run it only if they say yes:

```bash
PT dns on
```

This sets Private DNS to AdGuard, which blocks many ads and trackers in apps. Restore undoes it.

## Step 8: Show the difference

```bash
PT measure after
```

```bash
PT report
```

Tell them the numbers in a friendly way: memory freed, fewer things running, how many apps were switched off. Mention that:

- **Nothing was uninstalled.** Their apps, sign-ins and watch history are as they were.
- **Updates come by themselves.** PureTV checks GitHub for updates when the TV turns on, and asks before installing.
- **Getting Google TV back:** on the TV, **Settings › Google TV › Use Google TV's Home Again**, or ask you to restore it.

## Step 9: Close debugging

Ask them to turn **USB debugging** (and Network/Wireless debugging) **off** again in **Developer options**, then restart the TV. Then:

```bash
PT closed
```

It should say the port refuses connections. If it still answers, ask them to check the debugging switches again.

## Other requests

- **"Restore my TV" / "undo PureTV"**
  1. Step 2 (debugging on) and Step 3 (connect), then confirm with them.
  2. Run:
     ```bash
     PT restore
     ```
  3. Everything PureTV switched off comes back on, the original Home app and settings return, and the Remote Helper is switched off. PureTV Home itself stays installed; they can remove it in Settings › Apps if they like.
- **"My TV updated and the ads are back"**
  1. Connect, then ask.
  2. Run:
     ```bash
     PT check
     ```
     It switches off again whatever the update turned back on, with the same guards.
- **"Is my TV still clean?"** Connect and run `PT info`.

## Troubleshooting

| What you see | What to do |
|---|---|
| `find` finds nothing | Ask for the TV's IP (Step 3). Check they're on the same Wi-Fi, and that USB debugging (and Network debugging, if shown) is on. Some TVs need a restart after turning it on. |
| `ERROR: The TV refused the connection` | Debugging is off, or this TV only offers Wireless debugging. Use the pairing route in Step 2. |
| `ERROR: The TV did not allow this computer` | The Allow prompt was missed or declined. Ask them to watch the TV, then run `connect` again. |
| Home still opens Google TV after option A | Ask them to check **Settings › System › Accessibility › PureTV Remote Helper** is on, then run `PT install` again. Or offer option B. |
| `ERROR: Home went to …` (option B) | PureTV already rolled back, so the TV is as before. Run `PT install --replace-google-home` once more. If it happens again, use option A. |
| Anything else with `ERROR:` | Read it to them in plain words and follow its advice. Never work around a guard. |

Help from the makers: crazifiedstudios@gmail.com · crazifiedstudios.com
