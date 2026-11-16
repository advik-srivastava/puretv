# Install PureTV Home on your TV

Tested for the **Acer V Pro Series 32" QLED HD Ready Google TV** (AR32QDXGU2841AT, Android 14, 1366 × 768). It works the same on other Google TV and Android TV sets.

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
   adb install -r PureTV-Home-2.1.apk
   ```
   Allow the prompt that appears on the TV. `192.168.1.14` is your TV's address on your Wi-Fi.

**C. With the Downloader app**
1. Install *Downloader* from the Play Store.
2. Enter this address: `github.com/advik-srivastava/puretv/releases/latest`
3. Select the APK and install it.

## 2. Open PureTV
PureTV Home appears in the TV's **Apps** row. Open it, and setup walks you through:
- your profile;
- the glass style;
- the weather;
- the wallpaper;
- the Home button.

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
- **With the PureTV agent on the computer.** The agent switches Google TV's home off (it's never uninstalled), and the TV makes PureTV its Home app by itself. To undo it, use **Settings › Google TV › Restore Original TV**, or ask the agent to restore the TV.

To go back to Google TV's home at any time, use **Settings › Google TV › Use Google TV's Home Again**. Nothing is deleted.

## 4. Updates
- Whenever the TV is switched on, PureTV looks for a new version on its GitHub page, at most once every three hours.
- When it finds one, it asks: **Install Now** or **Later**. Nothing installs without your OK.
- The first time, Android asks you to allow PureTV to install updates. Turn PureTV on in the list that opens, then press Back.
- Android then shows its own "Update this app?" screen.
- Your profiles, top row, wallpaper and settings are kept.
- You can turn automatic checks off in **Settings › Software Updates**.

## Tuned for this TV
- The Home screen is drawn at the panel's own 1366 × 768 size.
- The glass blur is lighter than on 4K sets, with no movement behind it.
- Wallpapers are resized for HD, and the cut-outs are WebP files.
- Only the wallpaper on screen and the next one are kept in memory.
- On TVs with less than about 1.2 GB of RAM, Low-End TV Mode turns on by itself. You can change this in **Settings › Appearance**.

Help: crazifiedstudios@gmail.com · crazifiedstudios.com
