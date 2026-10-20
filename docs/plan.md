# Google TV app plan

The goal is a Google TV / Android TV IPTV player with the same features as the mac app: M3U and
Xtream Codes sources, live TV, movies, series, favorites, recents and a programme guide (EPG). It
installs by sideloading (`adb install`). The Play Store can come later.

## Decisions

| Topic | Choice | Why |
|---|---|---|
| Language and UI | Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`) | Leanback is being phased out. Compose for TV handles D-pad focus. |
| Player | Media3 ExoPlayer with HLS and the OkHttp data source | Plays HLS and MPEG-TS natively, with hardware decoding. Fall back to libVLC or mpv-android (step 7) only if real streams need it. |
| Networking | OkHttp, one shared client | The user agent is `VLC/3.0.21 LibVLC/3.0.21`, because providers reject unknown clients. The same client is used by Media3 and Coil. |
| JSON | `org.json` for the Xtream API, kotlinx.serialization for the disk cache | Xtream responses are loosely typed (numbers as strings and back), so they're read leniently, like the Swift `XtreamClient`. |
| Images | Coil 3 with the OkHttp network fetcher | |
| Navigation | A back stack in state (`Screen` sealed interface) and `BackHandler` | Only four screens, so navigation-compose isn't needed. |
| State | `LibraryStore` as an `AndroidViewModel` exposing Compose `mutableStateOf` | Same role as the Swift `@Observable LibraryStore`. |
| SDK levels | minSdk 24, compile/target 36 | Covers older Android TV boxes as well as current Google TV devices. |
| Package | namespace `com.maiaxi.iptvplayer`, applicationId `com.maiaxi.iptvplayer.tv` | |
| Cleartext | `android:usesCleartextTraffic="true"` | Most IPTV servers use plain `http://`. |

The library versions in `gradle/libs.versions.toml` (AGP 8.12.0, Kotlin 2.2.0, Compose BOM
2025.06.00, tv-material 1.0.0, Media3 1.7.1, Coil 3.2.0) were chosen because they're known to work
together. Newer releases exist. Upgrade them in a step of their own, after the app builds.

## Status

☐ not started · ◐ in progress · ☑ done

| # | Step | Status |
|---|---|---|
| 1 | Project skeleton and data layer | ☑ |
| 2 | Browse UI and sources | ☑ |
| 3 | Player | ☑ |
| 4 | Series detail | ☑ |
| 5 | Programme guide (EPG) | ☑ |
| 6 | Search and adding sources from a phone | ☑ |
| 7 | Playback hardening | ◐ |
| 8 | Release builds | ☑ |

## Steps

### 1. Project skeleton and data layer

Done so far: the Gradle wrapper (8.14.3), `settings.gradle.kts`, the root and `app/build.gradle.kts`,
`gradle/libs.versions.toml`, `gradle.properties`, `.gitignore`, `local.properties`, and launcher
icons in `res/mipmap-*` (resized from the iOS app icon). The folder isn't a git repository yet.

Still to do:

- `AndroidManifest.xml`: `INTERNET` permission; `uses-feature` for `android.software.leanback`
  and `android.hardware.touchscreen`, both `required="false"`; `usesCleartextTraffic`;
  `android:banner`; one `MainActivity` with the `LEANBACK_LAUNCHER` and `LAUNCHER` categories.
- `res/drawable/banner.xml`: a 320×180dp layer-list with a dark background and the icon in the
  centre. The TV launcher needs a banner.
- `res/values/themes.xml`: a NoActionBar theme with a black window background.
- `res/values/strings.xml` and `res/values-pt/strings.xml`.
- `models/Models.kt`: `ContentKind` (LIVE, MOVIE, SERIES, serialized as `live`, `movie`,
  `series`, with `key = name.lowercase()` for ids), `MediaItem`, `MediaCategory`, `Episode`,
  `SeriesInfo`, `AccountInfo`, `SourceKind` (sealed: `M3u(url)`, `Xtream(server, username,
  password)`), `Source` (with `subtitle`), and `LibraryData`. URLs are `String`s and dates are
  epoch milliseconds. Ids follow the Swift app: `<sourceId>:<kind>:<nativeId>`.
- `models/SourceDraft.kt`: a port of `SourceDraft.swift`. It includes the conversion of a
  `get.php?username=&password=` M3U address into an Xtream source, and it checks Xtream
  credentials with `accountInfo()` before saving.
- `services/Http.kt`: the shared OkHttp client, with an interceptor that adds the VLC user agent
  when a request has none.
- `services/AppError.kt`: `AppError(@StringRes res, vararg args)`, so services throw errors the UI
  can localize, plus `Throwable.userMessage(context)`.
- `services/M3uParser.kt`: a port of `M3UParser.swift`, in pure Kotlin so it's unit-testable. It
  takes the "Uncategorized" label as a parameter.
- `services/XtreamClient.kt`: a port of `XtreamClient.swift` (`loadLibrary`, `accountInfo`,
  `seriesInfo`). Keep the sequential requests, the 1/2/4 s retries on 429/502/503/504 and on empty
  200 responses, and the lenient `string`/`long`/`url`/`rating`/`base64` readers. Build URLs with
  OkHttp's `HttpUrl`. Encode credentials in stream paths with `Uri.encode(s, "!$&'()*+,;=:@")`.
  Movies and series are optional: a failure there returns an empty list but must rethrow
  `CancellationException`. Leave `shortEPG` for step 5.
- `services/Storage.kt`: `filesDir/sources.json` (app-private, so the mac app's chmod isn't needed),
  `cacheDir/<id>-v1.json` for `LibraryData`, and atomic writes.
- `services/LibraryStore.kt`: a port of `LibraryStore.swift` without the guide. It loads from the
  cache first and refetches when the cache is older than 12 h. Keep the lookup maps (by kind, by
  category, by id) in one `mutableStateOf`, so that Compose recomposes when they change. Favorites,
  recents (up to 50) and the selected source go in SharedPreferences.
- `src/test/.../M3uParserTest.kt`: attributes, a group title that contains a comma,
  `#EXTVLCOPT` user agent and referrer, `url-tvg`, and kind detection from `/movie/`, `/series/`
  and file extensions.

Done when: `./gradlew assembleDebug` and `./gradlew test` pass. The app can show a placeholder
screen at this point.

Notes:

- Android Studio's bundled JDK is now 25, and Gradle 8.14.3's Kotlin DSL compiler can't parse that
  version (`IllegalArgumentException: 25.0.3`). Builds use Homebrew's `openjdk@21` instead (see
  CLAUDE.md). AGP 8.12 installed build-tools 35.0.0 into the SDK on the first build.
- `MainActivity` is a placeholder that only shows the welcome text. Step 2 replaces it.
- Differences from the Swift app: `xtreamId` is a `Long`; there's no file-picker M3U, since TV has
  no files to pick, so M3U sources must be `http(s)` URLs; recents are stored as a
  newline-joined string, because a `StringSet` loses order.
- `XtreamClient` takes a `Context` for the localized fallback names ("Channel %d" and so on).
  `SourceDraft.save` takes one too.

### 2. Browse UI and sources

- `app/MainActivity.kt`: sets up Coil's singleton `ImageLoader` with `OkHttpNetworkFetcherFactory`
  and `Http.client`. It overrides `dispatchKeyEvent` and checks a `keyHandler` property that the
  player sets, so remote keys reach the player before Compose moves focus.
- `app/AppRoot.kt`: the `Screen` back stack (Browse, SourceForm, Series, Player). When there are
  no sources, it shows the source form as a welcome screen.
- `views/Theme.kt`: tv-material `darkColorScheme`, inside a full-screen `Surface`.
- `views/BrowseScreen.kt`: a `TabRow` with Live TV, Movies, Series, Favorites, Recents and
  Sources. A tab is selected when it gets focus, and the row has `focusRestorer()`. Live, movies and
  series show a category list on the left (`ListItem`s, selected on focus, with `focusRestorer()`)
  and a `LazyVerticalGrid` of cards on the right. Cards are 16:9 with a fitted logo for live
  channels and 2:3 with a cropped poster otherwise. Holding OK (`onLongClick`) toggles a favorite,
  shown as a star. Use safe-area padding of about 48dp horizontally and 24dp vertically. Show the
  loading and error states with a Try Again button.
- `views/SourcesPane.kt`: the source list. OK selects a source and holding OK edits it. It also
  has Add Source, Refresh Library and the account expiry date.
- `views/SourceFormScreen.kt`: Xtream/M3U mode buttons, and `BasicTextField`s with a focus border
  (tv-material has no text field). Password fields hide their text. Remove asks for confirmation
  by changing the button label on the first press.

Done when: you can add an Xtream or M3U source on the emulator, browse categories and grids with
the D-pad, and toggle favorites.

Notes:

- `LibraryStore.items(sidebar, filter)` mirrors the Swift `items(for:filter:search:)` without
  search, which comes in step 6. `SourceDraft.Mode` has no title yet: use "Xtream Codes" and
  `R.string.m3u_playlist`.
- Tested on the emulator with the iptv-org Portugal playlist
  (`https://iptv-org.github.io/iptv/countries/pt.m3u`): add, browse, favorites, recents, edit,
  and focus going back to the card after Back. Xtream wasn't tested against a real server because
  there are no credentials to test with.
- The emulator is an AVD written by hand, `tv_1080p` in `~/.android/avd`, because the SDK has no
  cmdline-tools/`avdmanager`. Start it with
  `~/Library/Android/sdk/emulator/emulator -avd tv_1080p`. Google TV is 960×540 dp, so screens
  must fit that size.
- Text fields sit behind a focusable `Surface` and only become focusable after OK. Focusing a
  `BasicTextField` opens the IME, which then takes the D-pad, so you'd get stuck moving through
  the form.
- The grid has no item keys, because some panels list the same stream twice and duplicate keys
  crash lazy layouts.
- `Series` and `Player` are placeholder screens in `AppRoot`. Recents are already updated when an
  item is opened.

### 3. Player

- `player/PlayerScreen.kt`: an `ExoPlayer` inside a `PlayerView` (`AndroidView`), released in
  `onDispose`, with `keepScreenOn` and buffering always shown. Each item gets a new
  `DefaultMediaSourceFactory(OkHttpDataSource.Factory(Http.client))`, with the item's user agent
  and `Referer`. Set the `m3u8` MIME type when the path ends in `.m3u8`. `.ts` streams are detected
  as progressive.
- Live channels: no controller. ↑/↓ and CH+/CH− switch channel within the queue (the list the
  channel was opened from). OK shows an overlay with the number and name, which hides after 4 s.
  On `ERROR_CODE_BEHIND_LIVE_WINDOW`, seek to the default position. On other errors, retry up to
  3 times, 2 s apart, before showing "This content couldn't be played (%s)."
- VOD: the standard PlayerView controller, operated with the D-pad. Check on the emulator that
  `PlayerView` gets focus.
- Every item played is added to recents.

Done when: a live channel, a movie and an HLS stream play on the emulator, and channel zapping
works.

Notes:

- Tested on the emulator: live HLS channels from iptv-org (zapping with ↑/↓ and CH+, and the OK
  overlay), MP4 movies with the controller, and a channel that returns 404, which shows the error
  after 3 retries. The movies came from a local test playlist served from the Mac at
  `http://10.0.2.2:8765/test.m3u`, with test-videos.co.uk MP4s. Google's `gtv-videos-bucket`
  samples now return 403.
- With targetSdk 36, Back arrives through the `OnBackInvokedDispatcher`, not as a `KeyEvent`, so
  `MainActivity.keyHandler` never sees it. The player uses a `BackHandler` that's enabled while the
  controller is visible.
- ↑ and CH− go to the previous channel, ↓ and CH+ to the next. The channel number is the position
  in the queue.
- The player is paused on `ON_STOP`. Live channels seek to the live edge on `ON_START`.
- Series move on to the next episode in the queue when one ends, as in the Swift app.
- Also fixed from step 2: moving down from the last form field landed on Cancel, because it was the
  closest button. The last field now sets `focusProperties.down` to Add. `focusRestorer` with a
  fallback didn't work for this.

### 4. Series detail

Cover, name, genre and plot on the left. On the right, seasons (selected on focus) and episodes.
Choosing an episode plays it, with the whole season as the queue. Episode names use
`S%1$d:E%2$d · %3$s` (`T…` in Portuguese).

Notes:

- `Episode.item(in:)` from the Swift app isn't ported yet. Add it here, with the formatted name.
- Done. `Episode.item(series, name)` is in `Models.kt`. `Screen.Player` carries `recent`, the series,
  which `PlayerScreen` puts in Recents instead of the episode.
- Tested with `scripts/mock_xtream.py`, a fake Xtream panel. Run it with
  `python3 scripts/mock_xtream.py` and add a source with server `10.0.2.2:8766`, user `demo` and
  password `p@ss word`. It has a live channel, a movie and a two-season series, with numbers sent
  as strings and episodes out of order, and its streams redirect to public test videos. This also
  tested the Xtream client end to end: sequential requests, the VLC user agent, credential
  encoding, account expiry, series info, redirects and moving on to the next episode.
- The series screen is wrapped in the `SaveableStateProvider`, like browse, so the season and the
  played episode survive the trip to the player, and focus goes back to that episode.
  `LibraryStore` caches `SeriesInfo` per item for the session, and Refresh clears the cache.
- → from Favorite goes to the selected season, or the first episode when there's only one season,
  using `focusProperties.right`. Spatial search jumped to whichever episode row was nearest.

### 5. Programme guide (EPG)

- Port `XMLTVParser.swift` to `XmlPullParser`, with `GZIPInputStream` for `.gz` guides. Guides can
  be 10–100 MB, so stream the file and keep only the wanted channels within a window from −1 h to
  +24 h.
- Add Xtream `get_short_epg` as a fallback (base64 titles). The `base64` reader already exists in
  `XtreamClient`, and `LibraryStore` still needs the `epg(for:)` port.
- Cache the guide for 6 h and reload it every 3 h. Add a 30 s clock for "now playing".
- Show the current programme on live cards and in the player overlay.

Notes:

- `XmltvParser` takes the `XmlPullParser` as a parameter. The app passes `Xml.newPullParser()`; the
  unit tests pass kxml2's `KXmlParser` (a `testImplementation`), because the unit-test android.jar
  has no implementation. Relaxed mode skips undeclared entities like `&nbsp;`. A broken feed keeps
  the programmes read before the error, as `XMLParser` does in the Swift app.
- The guide downloads straight to `cacheDir/<id>-epg.xml` through a `.part` file
  (`OkHttpClient.download`), so a 100 MB guide never sits in memory. The parser detects gzip from
  the magic bytes. Unlike the Swift app, a failed download falls back to an older cached file.
- `LibraryStore` follows the Swift app: `guideKey` (source and URL), a 30 s `clock`, a reload every
  3 h, `schedule`, `currentProgram`, and `epg(item)` with the `get_short_epg` fallback, which only
  the player uses. Every library change goes through `apply()`, which also loads the guide.
- Tested with `scripts/mock_xtream.py`, which now serves a gzipped `xmltv.php` with half-hour slots
  around the current time, plus a second channel that only has a base64 short EPG. Checked: the
  "now playing" line on cards, the player overlay (now, times, progress, up next) for both
  channels, case-insensitive channel ids, and the cache reused after a restart.
- The emulator's clock can drift from the Mac's (it was 28 minutes behind), which shifts what
  counts as "now". The app was right; the clocks differed.

### 6. Search and adding sources from a phone

- Search in each tab.
- Typing credentials with a remote is painful. Serve a small form from the TV on the local network
  (for example with NanoHTTPD), and show its address and a QR code on the source form.

Notes:

- Search: a `TvTextField` above the categories in each library tab. It filters as you type within
  the selected category, ignoring case and accents (`String.folded()`, tested in `SearchTest`).
  Folded names are built lazily in `Index` on the first search. The query is saved per tab and
  source. The field component moved out of `SourceFormScreen` into `views/TvTextField.kt`.
- Phone setup: `RemoteSetupServer` (NanoHTTPD 2.3.1) runs only while the source form is open, on a
  random port, at a random 6-character path, for example `http://192.168.1.50:41519/k3x9pq`. Any
  other path returns 404. The path is short so the address can be typed if the QR can't be
  scanned. The page is a plain HTML form, localized with the app's strings. A POST fills the TV form
  on the main thread, shows "Details received from your phone" and moves focus to Add. The user
  still confirms on the TV. The QR code is drawn with zxing-core 3.5.3. No hosting and no internet
  are involved, but the phone must be on the same network.
- Tested on the emulator: search with results and with none. For the phone form, the port was
  forwarded with `adb forward` and the form exercised with curl: 404 on other paths, the page,
  and a POST with UTF-8 and `p@ss word` that filled the form. The on-screen QR was decoded with
  zxing to check its contents, and the server stops when the form closes. Not tried with a real
  phone yet, since the emulator's 10.0.2.15 isn't reachable from the LAN; check this on a real TV.

### 7. Playback hardening

- Test with real providers: AC3/EAC3 audio, MPEG-2, broken TS streams, and redirects from http to
  https.
- Choosing audio and subtitle tracks.
- If ExoPlayer can't handle some streams, evaluate libVLC or mpv-android, either as a fallback
  player or a replacement.

Done when: real provider streams (live TS, HLS, VOD) play, including AC3/EAC3 audio, and any
streams that ExoPlayer can't play are either handled or written down with a decision on libVLC or
mpv-android.

Notes:

- Done without a provider account (2026-10-19):
  - Audio and subtitles: `player/TrackPanel.kt`, a panel on the right that lists the supported
    tracks and applies them as `TrackSelectionOverride`s, with Off for subtitles. Audio labels
    include the codec and channels ("English · AC3 5.1"). It opens with → on live channels and
    with Menu, Captions or Audio track on any item. Back closes it. PlayerView's gear still works
    for VOD.
  - Retrying a live channel shows the channel overlay with "Loading…" instead of a black screen.
  - A container parsing error (`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`) retries once as HLS,
    for `.ts` addresses that redirect to playlists. The mock panel now advertises `ts`, so its
    channels exercise this.
  - `DefaultRenderersFactory.setEnableDecoderFallback(true)`, for TV decoders that fail to start
    with some AC3/EAC3 or MPEG-2 streams.
  - Network errors (`IOException`) show "Couldn't connect to the server…" instead of OkHttp's
    English message.
  - OkHttp follows http→https redirects by default (`followSslRedirects`), so nothing was needed.
- Tested with a real provider on the emulator (2026-10-19). It was an Xtream panel given as a
  `get.php` M3U address, live only, with 269 Portuguese channels and 1 connection allowed. Its
  credentials are not stored in the repo.
  - The M3U address was converted to Xtream, the account checked, and its expiry shown. The
    library loaded in under 5 s. The 11 MB XMLTV guide was streamed and parsed, and cards show
    the current programme. The Java heap was about 18 MB.
  - Live TS plays (H.264 + AAC). Zapping works within the single-connection limit: each new
    channel's request answers in under 0.5 s.
  - The panel load-balances with 302 redirects to several stream servers. One of them
    doesn't answer from this network, possibly an ISP block, so the first
    attempt sometimes times out. Streams now use `Http.streamClient`, which has a 5 s connect
    timeout instead of 15 s, so ExoPlayer's own retry reaches another server sooner.
  - Bug found and fixed: after the HLS fallback failed, the retries stayed in HLS mode, so a
    channel that only needed a second TS attempt failed for good. The fallback is now tried once
    per channel (`hlsTried`), and a manifest error switches back to the original type before the
    normal retries.
  - One channel answers `204 No Content` from the provider and correctly shows the playback
    error.
- Tested on a real box (2026-10-20): a Xiaomi TV Box S (`MiTV-AFKR0`, Amlogic, Android 11,
  1080p), connected with `adb connect <box-ip>:5555` after turning on USB debugging. The
  source was added from a phone through the QR code, which confirms the step 6 flow on a real
  network.
  - Bug found and fixed: there was sound but no picture. The Amlogic decoder was producing
    frames, but the `SurfaceView` layer sat behind the app window, and the transparent hole above
    it doesn't happen inside Compose on this box. `PlayerView` is now inflated from
    `res/layout/player_view.xml` with `surface_type="texture_view"`, which can only be set from
    XML. The emulator (Android 16) didn't show the problem.
  - `MainActivity` is now `launchMode="singleTask"`. `am start`, and the launcher on some
    boxes, stacked a second instance on top of the one that was playing.
  - Zapping between the RTP 1 variants shows a picture in under 2 s, and the overlay with the
    guide draws over the video.
- Deferred (2026-10-20), until a provider with movies, series or Dolby audio is available. The
  user has none, so step 8 went ahead while this step stays ◐. VOD was covered with public MP4s
  and series with the mock panel; what's left needs a real provider.
- Still to do: AC3/EAC3 and MPEG-2 on a real TV (the emulator has no AC3 decoder, and this
  provider's channels were AAC), VOD and series from a real provider (this account has none), and
  deciding whether libVLC or mpv-android is needed. Nothing so far needs them.

### 8. Release builds

A signing config, R8 rules (kotlinx.serialization, Media3), a versioned APK, and a GitHub repo and
releases if the user wants them.

Notes:

- Signing: the release keystore is `~/.android/keystores/iptv-player-tv-release.jks` (RSA 4096,
  valid 100 years), outside the repo. `keystore.properties` in the project root holds its path
  and password, and it's git-ignored along with `*.jks` and `*.keystore`. Without that file,
  `assembleRelease` builds an unsigned APK. Back up the keystore: losing it means users must
  uninstall before installing a new version, and lose their sources.
- R8 with resource shrinking is on for release. No extra rules were needed: every library ships
  its own. The release APK is 2.5 MB, against 14 MB for debug.
- APKs are named `iptv-player-tv-<versionName>.apk`: `iptv-player-tv-0.1.0.apk` for release and
  `iptv-player-tv-0.1.0-debug.apk` for debug.
- Debug builds use the id `com.maiaxi.iptvplayer.tv.debug` and the name "IPTV Player Debug", so
  they can be installed next to release. `app_name` moved from `strings.xml` to `resValue`. The
  old debug build had the release id, so the box needed one uninstall, which lost its source.
- Checked the release build on the Xiaomi TV Box S. Cold start takes 0.71–0.88 s, so no baseline
  profile is needed. The emulator's ~6 s was a fresh debug build. Sources and the library cache
  read back after a restart, so kotlinx.serialization survives R8. Playback, zapping, the guide,
  Back and the track panel all work.
- Bug found and fixed on the box: the track panel hid the only audio track (`Português ·
  MPEG-L2`). The box declares it as exceeding its capabilities, but ExoPlayer plays it anyway.
  The panel now uses `isTrackSupported(i, allowExceedsCapabilities = true)`.
- GitHub: the public repo is https://github.com/maiaxi/iptv-player-googletv, named so it's easy
  to find next to `maiaxi/iptv-player`. The local folder is still `iptv-player-tv`. Commits use
  the noreply identity of the Swift repo, configured for this repo only. The README is for people
  installing the app. Provider details and LAN IPs were removed from the docs before publishing.
- Releases (v0.1.0 and v0.1.1 were built on the Mac): from 0.1.2, they come from GitHub
  Actions, as in the Swift repo. The user chose this, accepting that the keystore and its
  password live in the repo's secrets. `.github/workflows/build.yml` runs the tests and builds
  the debug APK and an unsigned release APK on every PR and push to `main`, kept as artifacts. On
  a `v*` tag, it writes `keystore.properties` from the secrets, passes `-PappVersionName` and
  `-PappVersionCode` (x·10000 + y·100 + z) from the tag, verifies the signature, and creates
  the release with `--generate-notes`. `scripts/release.sh <x.y.z>` only checks that `main` is
  clean and in sync, then pushes an annotated tag. `build.gradle.kts` no longer holds the
  version: local builds are `0.0.0-dev`.
- Updater: `services/Updater.kt`, an `AndroidViewModel` created in `AppRoot`. Release builds check
  `releases/latest` of the repo once at launch; debug builds never do. A newer tag shows
  "Update to x" in the browse header and under Sources, next to the version and "Check for
  Updates". Updating downloads the release APK into `cacheDir/updates` and opens the system
  installer through a `FileProvider` (`res/xml/update_paths.xml`). It needs
  `REQUEST_INSTALL_PACKAGES`; without it, the app first opens the "install unknown apps"
  settings. `UpdaterTest` covers the version comparison.
- Tested on the box: a local 0.0.9 build found v0.1.0 on GitHub, downloaded it, and the
  system installer updated it to 0.1.0 with the source kept.
  After v0.1.1 was published, the box went from 0.1.0 to 0.1.1 in the same way: one press on
  "Update to 0.1.1", then Install.
- Launcher icon (0.1.2): the app only had square PNGs, so launchers that draw round icons showed
  it square. There's now an adaptive icon (`mipmap-anydpi-v26`, vector layers
  `ic_launcher_background` and `ic_launcher_foreground` ported from the Swift app's
  `make-icon.swift`) and a `roundIcon`. The box's custom launcher shows banners, and the old
  banner (dark with the icon in the middle) looked different from the others. It's now an
  edge-to-edge gradient with rounded corners and the TV symbol, checked on the box.
- Xiaomi TV Box S quirk: it has a second profile (user 10). The settings screen the app opens
  applied the switch to the app in that profile, so `canRequestPackageInstalls()` stayed false
  and the app sent the user back to the same screen. It was granted for user 0 with
  `adb shell appops set --user 0 com.maiaxi.iptvplayer.tv REQUEST_INSTALL_PACKAGES allow`. The
  app now explains where to allow it on the second try instead of looping. The TV's own path
  (Settings → Apps → Security or Special app access) wasn't tried.

## Log

- 2026-10-19 · Step 1 · Project created in a separate folder from `iptv-player`. Android Studio,
  the SDK and the Gradle wrapper are installed, and the Gradle files and icons are written. The
  session that started this project ran in the `iptv-player` folder, so work continues from a new
  session here.
- 2026-10-19 · Step 1 · Manifest, banner, theme, strings (en/pt), models, `SourceDraft`, `Http`,
  `AppError`, `M3uParser`, `XtreamClient`, `Storage` and `LibraryStore` written, plus a placeholder
  `MainActivity`. `assembleDebug` and 7 `M3uParserTest` tests pass. The build needs JDK 21, since
  Android Studio now ships JDK 25.
- 2026-10-19 · Step 2 · `MainActivity` (Coil and `keyHandler`), `AppRoot` back stack, theme,
  `BrowseScreen` (tabs, categories, grid, empty and error states), `MediaCard`, `SourcesPane` and
  `SourceFormScreen`. Checked on the TV emulator with an M3U source.
- 2026-10-19 · Step 3 · `PlayerScreen` with ExoPlayer and PlayerView, live zapping with an overlay,
  retries and error message, the VOD controller with Back handling, and lifecycle pause. Checked on
  the emulator with live HLS, MP4 and a broken stream. Fixed the source form's focus moving down to
  Cancel.
- 2026-10-19 · Step 4 · `SeriesScreen` (details, season chips selected on focus, episode list, Play
  and Favorite), `Episode.item`, Recents use the series, state and focus restored after playback,
  and `SeriesInfo` cached. Added `scripts/mock_xtream.py` and tested Xtream end to end on the
  emulator.
- 2026-10-19 · Step 5 · `XmltvParser` (streaming, gzip, time window, dedupe) with 6 tests,
  `XtreamClient.shortEpg`, streaming guide download and 6 h cache, guide and clock in
  `LibraryStore`, "now playing" on live cards, and now/next in the player overlay. Tested with the
  mock panel.
- 2026-10-19 · Step 6 · Accent-insensitive search in each tab. Adding a source from a phone through
  a local NanoHTTPD form with a QR code on the source form. Checked on the emulator with curl and
  by decoding the QR from a screenshot.
- 2026-10-19 · Step 7 (in progress) · Audio and subtitle panel, loading state while reconnecting,
  HLS fallback for `.ts` playlists, decoder fallback, and localized network errors. Checked on the
  emulator with the mock panel and Apple's bipbop stream. Real-provider testing is still to do.
- 2026-10-19 · Step 7 (in progress) · Tested with a real live-only Xtream provider. Fixed the HLS
  fallback getting stuck during retries. Added a stream client with a 5 s connect timeout for
  load-balanced panels.
- 2026-10-20 · Step 7 (in progress) · Tested on a Xiaomi TV Box S. Fixed sound without picture
  (TextureView instead of SurfaceView) and duplicate activities (`singleTask`). The phone setup
  worked on a real network.
- 2026-10-20 · Step 7 · Left in progress. The real-provider VOD, series and AC3/EAC3 checks are
  deferred because no provider with them is available. Moved on to step 8.
- 2026-10-20 · Step 8 (in progress) · Release signing with a keystore outside the repo, R8 and
  resource shrinking, versioned APK names, and a separate debug id. Release checked on the Xiaomi
  box: 0.7–0.9 s cold start. Fixed the track panel hiding audio tracks that exceed capabilities.
  GitHub and the updater wait on the user.
- 2026-10-20 · Step 8 · Public repo `maiaxi/iptv-player-googletv`, `scripts/release.sh`, and
  release v0.1.0. In-app updater from GitHub releases, tested on the box from 0.0.9 to 0.1.0.
  Found the Xiaomi profile quirk with the install permission and added a fallback message.
- 2026-10-20 · Step 8 · Adaptive and round launcher icon and a new banner. Releases move to
  GitHub Actions on tags, with the version from the tag and signing from repo secrets.
