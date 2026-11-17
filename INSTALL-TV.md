# Install PureTV Home on your TV

> **The best way is with Claude.** Paste `Set up my TV with https://github.com/advik-srivastava/puretv` into Claude Code on a computer on the same Wi-Fi. Claude backs the TV up, switches off tracking and bloat, makes PureTV the only Home screen and restarts the TV. This page is for installing just the Home screen by hand.

Made for the **Acer V Pro Series 32" QLED HD Ready Google TV** (AR32QDXGU2841AT, Android 14, 1366 × 768). It works the same on other Google TV and Android TV sets.

PureTV Home is an app like any other. Installing it doesn't change, clear or sign out of any app, account or setting on the TV. Google TV stays installed.

The file to install is `PureTV-Home-<version>.apk` (about 4 MB) from the [latest release](https://github.com/advik-srivastava/puretv/releases/latest).

## 1. Get the file onto the TV (pick one)

**A. From a USB stick**
1. Copy the PureTV APK onto a USB stick and plug it into the TV.
2. On the TV, install a file manager from the Play Store (for example *File Commander* or *X-plore*).
3. Open the stick in the file manager and select the APK.
4. If the TV asks, allow the file manager to install apps: **Settings › Apps › Security & restrictions › Unknown sources** › turn on the file manager.
5. Select **Install**.

**B. From the computer, over Wi-Fi (adb)**
1. On the TV: **Settings › System › About**, then press OK on **Android TV OS build** 7 times. This turns on Developer options.
2. **Settings › System › Developer options** › turn on **USB debugging** (on some TVs it's called **Network debugging**).
3. On the computer, run:
   ```bash
   adb connect 192.168.1.14:5555
   ```
   ```bash
   adb install -r PureTV-Home-3.1.apk
   ```
   Allow the prompt that appears on the TV. `192.168.1.14` is your TV's address on your Wi-Fi.

**C. With the Downloader app**
1. Install *Downloader* from the Play Store.
2. Enter this address: `github.com/advik-srivastava/puretv/releases/latest`
3. Select the APK and install it.

## 2. Open PureTV
PureTV Home appears in the TV's **Apps** row. Open it, and setup walks you through:
- your profile;
- the glass style (Look & Style);
- the weather;
- the wallpaper;
- the Home screen and remote.

## 3. Make the Home button open PureTV
Google TV doesn't let you pick another Home app in its settings. PureTV gets around this in one of two ways:

- **Without a computer: the Remote Helper.**
  1. At the end of setup, choose **Open Accessibility**.
  2. Go to **System › Accessibility › PureTV Remote Helper**, turn it on and confirm.
  3. From then on, the Home button and a TV restart both bring up PureTV. The helper only notices which app is on screen; it never reads anything inside your apps.
  - *If the switch is greyed out ("restricted setting")*, Android 14 is blocking an app that was installed from a file. Install with method B instead, or run this once from the computer:
    ```bash
    adb shell appops set tv.pure.home ACCESS_RESTRICTED_SETTINGS allow
    ```
- **With Claude on the computer (fastest).** Paste `Set up my TV with https://github.com/advik-srivastava/puretv` into Claude Code. It switches Google TV's home off (never uninstalled), so PureTV is the only Home screen, and the TV restarts with one Home running. To undo it, paste `Restore my TV with https://github.com/advik-srivastava/puretv`.

While Google TV's home is still on (Remote Helper setup), **Settings › Google TV › Use Google TV's Home Again** switches back at any time. Nothing is deleted.

## 4. Updates
- Whenever the TV is switched on, PureTV looks for a new version on its GitHub page, at most once every three hours.
- When it finds one, it asks: **Install Now** or **Later**. Nothing installs without your OK.
- The first time, Android asks you to allow PureTV to install updates. Turn PureTV on in the list that opens, then press Back.
- Android then shows its own "Update this app?" screen.
- Your profiles, top row, wallpaper and settings are kept.
- You can turn automatic checks off in **Settings › Software Updates**.

## Using PureTV
- **Up** from the apps opens the top bar: your profile, search, the Home / Apps / Live TV tabs, the weather, Control Center and Settings.
- **Menu** on the remote opens Control Center.
- **Hold OK** on an app to move or hide it.
- **Down** from the top row shows all your apps.
- After 90 seconds on Home, the screensaver starts. You can change that in **Settings › Screen Saver**.

## Why it's fast
- PureTV is a native Android app, with no web view.
- The Liquid Glass is drawn from a pre-blurred copy of the wallpaper, so nothing is blurred live.
- At rest the Home screen does nothing until the minute changes or a wallpaper fades.
- Wallpapers are sized for HD panels like the Acer's 1366 × 768. Only the one on screen and the next are kept in memory, and the next is dropped while you're in an app.
- With Google TV's home switched off (Claude does this), the TV runs one Home screen instead of two.
- Claude's report card shows how many frames are drawn late, before and after, measured on your TV.

Help: crazifiedstudios@gmail.com · crazifiedstudios.com
