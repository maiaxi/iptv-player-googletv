# IPTV Player for Google TV

Android TV / Google TV port of the native IPTV player at `~/dev/pessoal/iptv-player` (SwiftUI and
libmpv for macOS and iOS). It's a separate app written in Kotlin with Jetpack Compose for TV and
Media3 (ExoPlayer). No code is shared with the Swift app. The data layer is ported from it by hand.

## Current work

The plan lives in [docs/plan.md](docs/plan.md). At the start of every session:

1. Read the plan, and find the first step in the Status table that isn't ☑. If one is ◐, carry on
   from its Notes.
2. Tell the user which step is next and what it involves, and wait for a go-ahead before changing
   code.
3. Work on that step only. If something belongs to a later step, note it in that step's Notes.
4. Before calling a step done, check its "Done when" criteria and run `./gradlew assembleDebug`
   (and `./gradlew test` when there are tests).
5. Update the plan: set the step's status, fill in its Notes, and add a Log line (date, step,
   summary).

Commit, push or create a GitHub repo only when the user asks.

## Conventions

- Talk to the user in Portuguese (pt-PT). Everything in the repo is in English: code, comments,
  docs, commit messages.
- The UI is localized in English (`res/values`) and Portuguese (`res/values-pt`). New user-facing
  strings need both. Reuse the wording of the Swift app's
  `~/dev/pessoal/iptv-player/Resources/pt-PT.lproj/Localizable.strings` where a string matches.
- Match the Swift app's layout: small files in the packages `app`, `models`, `services`, `views`
  and `player` under `com.maiaxi.iptvplayer`. Use few comments, each explaining why rather than
  what.
- When porting logic, keep the Swift app's behaviour, including its comments about IPTV panel
  quirks (lenient JSON, retries on 429/503, sequential Xtream requests).

## Tooling on this Mac

- JDK: Homebrew's `openjdk@21`. Android Studio's bundled JDK is 25, which Gradle 8.14.3 can't run
  on. Gradle needs `export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"`.
- Android SDK: `~/Library/Android/sdk`, set in `local.properties` (git-ignored). It has
  platform 36, build-tools 36.0.0, platform-tools (`adb`), the emulator and the
  `system-images;android-36;android-tv;arm64-v8a` image.
- `adb` is at `~/Library/Android/sdk/platform-tools/adb`. It isn't on `PATH`.
- Homebrew's `gradle` is 9.x, which can't load AGP 8. Always use `./gradlew` (Gradle 8.14.3).
- Release builds (`./gradlew assembleRelease`) are signed with
  `~/.android/keystores/iptv-player-tv-release.jks`, through the git-ignored `keystore.properties`.
  Never commit either.
- Publish with `scripts/release.sh <x.y.z> ["notes"]` from a clean tree. It bumps the version,
  builds, tags and creates the GitHub release that the in-app updater reads. The repo is
  public: https://github.com/maiaxi/iptv-player-googletv. Keep provider details, credentials
  and LAN addresses out of it.
- Test devices: the `tv_1080p` emulator (`~/Library/Android/sdk/emulator/emulator -avd tv_1080p`)
  and a Xiaomi TV Box S on the LAN (`adb connect <box-ip>:5555`; ask the user for the IP). With both connected, pick
  one with `ANDROID_SERIAL`.
