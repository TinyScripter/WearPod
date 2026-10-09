# AntennaPod Unofficial WearOs Mod

[![License: GPL v3](https://img.shields.io/github/license/AntennaPod/AntennaPod)](https://www.gnu.org/licenses/gpl-3.0)

**This is a private, unofficial fork of [AntennaPod](https://github.com/AntennaPod/AntennaPod).**
It is not affiliated with, endorsed by or supported by the AntennaPod project.
The fork adds a Wear OS app that can **play podcasts directly on the watch, without the smartphone**.
Everything else is the regular AntennaPod, the easy-to-use, flexible and open-source podcast manager for Android.

<img src="https://raw.githubusercontent.com/AntennaPod/StoreMetadata/main/listings/en-US/graphics/phone-screenshots/00.png" alt="Screenshot 0" height="200"> <img src="https://raw.githubusercontent.com/AntennaPod/StoreMetadata/main/listings/en-US/graphics/phone-screenshots/01.png" alt="Screenshot 1" height="200"> <img src="https://raw.githubusercontent.com/AntennaPod/StoreMetadata/main/listings/en-US/graphics/phone-screenshots/02.png" alt="Screenshot 2" height="200">

## Please use the official version if you can

**Unless you really need the Wear OS functionality of this fork, please install the official AntennaPod instead:**

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play"
      height="70">](https://play.google.com/store/apps/details?id=de.danoeh.antennapod)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="70">](https://f-droid.org/app/de.danoeh.antennapod)

This fork is maintained by one person in their spare time:
- It follows the official releases only with a delay and may contain bugs that the official app does not have.
- It has its own package name and signing key. Your data is not shared with an installed official AntennaPod. To move your subscriptions and progress, use the import and export functions in the settings.

### 🧪 Installation & Testing (Wear OS & Android Auto)

You can install this build either via the Google Play Internal Test Track or manually via GitHub Releases:

#### Option 1: Google Play Beta (Recommended)
* **Features:** Automatic updates, one-click installation on Wear OS, and full **Android Auto** support.
* **Requirements:** Submit your Google Play account email address so you can be added to the internal test track:
  
  👉 **[Request Access via Form](https://forms.gle/yyEuUSFzQbTkvgVt5)**

---

#### Option 2: Manual Download (GitHub Releases)
If you prefer not to share your email address, you can download the APKs directly from the [Releases](https://github.com/swalczyk/AntennaPod/releases) page.

⚠️ **Limitations of manual installation (Sideloading):**
* **No Android Auto:** Sideloaded apps are blocked by Android Auto and will not appear on your car display without advanced developer settings.
* **Manual Wear OS Setup:** Installing the APK on your smartwatch requires manual sideloading via ADB (e.g. using wireless debugging and tools like GeminiMan Wear OS Manager or Bugjaeger).
* **No Auto-Updates:** You will need to manually download and install future releases.

## Features of the Wear OS app

- Browse the queue, downloads, subscriptions and episodes of the phone on the watch
- Remote control the playback on the phone
- Download episodes to the watch (over Wi-Fi, directly from the podcast's server) and play them without the phone
- Downloads show a notification, wait for Wi-Fi if none is available and continue where they stopped after an interruption
- Separate lists for playback on the watch and playback on the phone on the start screen
- Own order of the episodes on the watch, which can be changed on the watch
- Playback controls on the watch: play and pause, skip back and forward, next episode
- Skip amounts, playback speed and "smart mark as played" are taken over from the settings of the phone
- Volume control with the rotating crown
- Playback on Bluetooth headphones
- Synchronization of the playback progress from the watch to the phone and from the phone to the watch. The most recent position wins, and positions from the watch are passed on to the gpodder synchronization of the phone
- Played episodes are marked as played on the phone and removed from the queue, and their files are deleted from the watch
- Episodes that are played or deleted on the phone are removed from the watch on the next synchronization

The phone stays in charge: subscriptions, episodes and the queue are managed on the phone only.
See [wearos_concept.md](wearos_concept.md) for the design of the feature.

## Feedback

**Please do not report problems of this fork to the official AntennaPod forum or the official AntennaPod repository.**
The AntennaPod developers do not know this fork and cannot help with it.

Bug reports and feature requests for this fork can only be submitted [in this repository](https://github.com/swalczyk/AntennaPod/issues).
If you are not sure whether a problem also occurs in the official app, please test the official version first and report it to the official project only if it happens there, too.

## Installation

### Requirements

- Use the **`play` flavor** of both apps. The Wear OS support is only part of this flavor because it needs Google Play Services. The `free` flavor does not contain it.
- The apps on the phone and the watch must have the **same package name and be signed with the same key**. Otherwise, the watch does not find the phone and shows that the phone is not compatible. The debug builds (`.debug` suffix) and the release builds cannot be mixed.
- Both devices must be paired through the Wear OS app of the watch, as usual.
- Download the signed APKs for the phone and the watch from the [releases page](https://github.com/swalczyk/AntennaPod/releases). Both are signed with the same key. You can also build them from source (see below).

### Phone

Download `app-play-release.apk` and open it on the phone. Android will ask you to allow the installation from unknown sources. Alternatively, install it with adb:

```
adb install -r app-play-release.apk
```

### Watch

Wear OS apps from outside of the Play Store are installed with adb over Wi-Fi:

1. On the watch, open **Settings → System → About → Versions** and tap **Build number** seven times to enable the developer options.
2. Open **Settings → Developer options**, enable **ADB debugging** and **Debug over Wi-Fi**. The watch must be in the same Wi-Fi network as your computer.
3. Under **Wireless debugging**, tap **Pair new device**. Note the IP address, the port and the pairing code.
4. On the computer, pair and connect:

   ```
   adb pair <ip-address>:<pairing-port>
   adb connect <ip-address>:<connection-port>
   ```

   The connection port is shown on the main Wireless debugging screen, and it differs from the pairing port.
5. Download `app-wearos-play-release.apk` and install it on the watch. If the phone is also connected with adb, select the watch with `-s`:

   ```
   adb devices
   adb -s <watch-id> install -r app-wearos-play-release.apk
   ```

Update the apps the same way. As long as the package name and the key stay the same, installing the new APK keeps the data.

### Android Auto

Android Auto only shows apps that were not installed from the Play Store if the developer options are also enabled **in Android Auto**. In the settings of Android Auto, tap the version number several times and then enable the option to allow apps from unknown sources. Without this, AntennaPod does not appear in the car.

## Building

You can build the project like any other Android project. Use JDK 21 for Gradle. For general information, see the [instructions of the official project](https://github.com/AntennaPod/AntennaPod/blob/develop/CONTRIBUTING.md).

Debug builds:

```
./gradlew :app:assemblePlayDebug :app-wearos:assemblePlayDebug
```

For release builds, create a signing key once with `keytool` and add the following properties to `~/.gradle/gradle.properties` (never to the repository):

```
releaseStoreFile=/path/to/your-keystore.jks
releaseStorePassword=...
releaseKeyAlias=...
releaseKeyPassword=...
```

Use the same key for the phone app and the watch app. Then build:

```
./gradlew :app:assemblePlayRelease :app-wearos:assemblePlayRelease
```

The APKs are written to `app/build/outputs/apk/play/release/` and `app-wearos/build/outputs/apk/play/release/`.

## License

This fork is licensed under the GNU General Public License (GPL-3.0), like AntennaPod. You can find the license text in the [LICENSE](LICENSE) file.

## About AntennaPod

AntennaPod is developed by the AntennaPod community. Please visit the [official repository](https://github.com/AntennaPod/AntennaPod) and the [AntennaPod Forum](https://forum.antennapod.org/) for the official app. If you want to translate AntennaPod, you can visit their [Weblate page](https://hosted.weblate.org/projects/antennapod/).
