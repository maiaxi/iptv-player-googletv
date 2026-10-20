# IPTV Player for Google TV

An IPTV player for Google TV and Android TV, with M3U playlists and Xtream Codes accounts: live
TV with channel zapping and a programme guide (XMLTV), movies, series, favorites, recents and
search. It's the TV counterpart of [IPTV Player](https://github.com/maiaxi/iptv-player) for
macOS and iOS, written in Kotlin with Jetpack Compose for TV and Media3 (ExoPlayer).

The app doesn't come with any channels. You need your own playlist or account from an IPTV
provider.

## Install

Download `iptv-player-googletv-<version>.apk` from the
[latest release](https://github.com/maiaxi/iptv-player-googletv/releases/latest) and install it
on the TV:

- With the **Downloader** app: enter the APK's address and install it.
- With **adb**: turn on developer options and USB or wireless debugging on the TV, then run
  `adb connect <tv-ip>:5555` and `adb install iptv-player-googletv-<version>.apk`.

After that, the app updates itself: when a new release is out, **Update** appears at the top of
the screen and under Sources. The first time, Android asks you to allow the app to install
unknown apps.

## Adding a source

Typing with a remote is slow, so the source form shows a QR code. Scan it with a phone on the
same Wi-Fi network, fill in the form on the phone, and the details appear on the TV. Your details
are only stored on the TV.

## Building

You need JDK 21 and the Android SDK (platform 36).

```sh
./gradlew assembleDebug   # app/build/outputs/apk/debug, installs next to the release as "IPTV Player Debug"
./gradlew test
```

Every pull request and push to `main` runs the tests and builds the APKs in GitHub Actions.
Pushing a tag like `v1.2.3` (`scripts/release.sh 1.2.3` does it from `main`) builds the release
APK signed with a key kept in the repository's secrets and publishes it, and installed apps
offer the update.
