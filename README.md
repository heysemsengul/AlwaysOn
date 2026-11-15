<div align="center">

# AlwaysOn

**A calm, minimal Always On Display for Android. Open source and permission-free.**

[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/heysemsengul/AlwaysOn/releases/latest/download/AlwaysOn.apk)
[![Latest release](https://img.shields.io/github/v/release/heysemsengul/AlwaysOn?style=for-the-badge)](https://github.com/heysemsengul/AlwaysOn/releases/latest)
![Min Android](https://img.shields.io/badge/Android-8.1%2B-555?style=for-the-badge)
![Permissions](https://img.shields.io/badge/Permissions-none-555?style=for-the-badge)

<img src="docs/screenshot.png" alt="AlwaysOn screenshot" width="300">

</div>

---

## About

AlwaysOn turns your phone into a quiet bedside or desk clock. It keeps the screen awake while it's open and shows only what matters: the time, the date, your next alarm and your battery. It sits on a soft, blurred mountain-lake background.

It asks for **no permissions at all**. The app doesn't use the internet, storage access, notifications access or anything else.

## Features

- 🕒 **Large, thin clock** in the upper middle, using 12-hour or 24-hour time to match your phone
- 📅 **Date** and your **next alarm** (shown if it's within 24 hours)
- 🔋 **Battery level** at the top right. It turns green with a ⚡ while charging and red at 15% or below
- 🏔️ **Blurred mountain-lake background**, or **your own photo** (blurred automatically), or **pure black**
- 🌙 **Tap anywhere to dim**: switches between very dim and your normal brightness
- 🔒 **Works over the lock screen**, so it stays visible after the phone locks
- 🛡️ **Burn-in protection**: everything glides gently up or down a few pixels each minute and always stays centered
- ✕ **Unobtrusive close button**: a faint × in the bottom-right corner
- 🪶 **Tiny**: under 100 KB, with no ads, no tracking and no internet access

## Download & install

1. On your phone, download **[AlwaysOn.apk](https://github.com/heysemsengul/AlwaysOn/releases/latest/download/AlwaysOn.apk)** (or pick a version from the [Releases](https://github.com/heysemsengul/AlwaysOn/releases) page).
2. Open the file. If Android asks, allow your browser or file manager to **install unknown apps**. This is a one-time phone setting, not a permission for AlwaysOn.
3. If Google Play Protect warns you, tap **Install anyway**. It warns about any app that doesn't come from the Play Store.
4. Open **Always On** from your app drawer.

**Requires Android 8.1 (Oreo) or newer.**

## How to use

| Action | What it does |
|---|---|
| **Tap** anywhere | Toggle dim mode |
| **Long-press** anywhere | Choose background: Mountain lake · Your own photo · Pure black |
| Tap the faint **×** (bottom right) | Close the app |

> 💡 **Battery tip:** on OLED screens, black pixels are switched off. Choose **Pure black** if you leave AlwaysOn running for hours.

## Permissions

None. These features don't need any permission on Android:

| Feature | How it works |
|---|---|
| Keep screen on | `FLAG_KEEP_SCREEN_ON`, active only while the app is open |
| Battery level | The system's `ACTION_BATTERY_CHANGED` broadcast |
| Next alarm | `AlarmManager.getNextAlarmClock()` |
| Show over lock screen | `setShowWhenLocked(true)` |
| Pick a photo | The system photo picker; only a small blurred copy is kept, inside the app |

You can check this yourself in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). It has no `<uses-permission>` entries.

## Build from source

1. Clone the repo:
   ```bash
   git clone https://github.com/heysemsengul/AlwaysOn.git
   ```
2. In **Android Studio**, choose **File → Open** and select the `AlwaysOn` folder.
3. Wait for Gradle to sync. Install SDK 35 if Android Studio asks.
4. Pick a device or emulator and press **▶ Run**.

Or from the command line:
```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/app-release.apk
```

### Project structure

```
app/src/main/
├── AndroidManifest.xml          # no permissions
├── java/com/heysem/alwayson/
│   ├── MainActivity.kt          # keep-awake, lock screen, battery, alarm, menu
│   ├── AodView.kt               # draws the clock, battery, background, close button
│   └── Backgrounds.kt           # mountain image, photo import and blur
└── res/drawable-nodpi/
    └── bg_mountain.jpg          # default blurred background
```

## Contributing

Ideas, bug reports and pull requests are welcome. Open an [issue](https://github.com/heysemsengul/AlwaysOn/issues) to start.

## License

Released under the [MIT License](LICENSE).
