#!/bin/bash
# Publishes a release: sets the version, builds and tests the signed APK, tags it and uploads it to
# GitHub, where the app's updater finds it. Runs on the Mac so the signing key never leaves it.
#
# Usage: scripts/release.sh 0.1.1 ["What changed"]
set -euo pipefail

version=${1:?Usage: scripts/release.sh <major.minor.patch> [notes]}
notes=${2:-}
cd "$(dirname "$0")/.."

[[ $version =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || { echo "Version must look like 1.2.3"; exit 1; }
[[ -f keystore.properties ]] || { echo "keystore.properties is missing, so the APK can't be signed"; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Commit or stash your changes first"; exit 1; }
git rev-parse "v$version" >/dev/null 2>&1 && { echo "v$version already exists"; exit 1; }

# versionCode must grow with every release, or Android refuses the update: 1.2.3 -> 10203.
code=$((BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 + BASH_REMATCH[3]))
sed -i '' -E "s/versionCode = [0-9]+/versionCode = $code/; s/versionName = \"[^\"]+\"/versionName = \"$version\"/" app/build.gradle.kts

export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
./gradlew --quiet clean test assembleRelease
apk="app/build/outputs/apk/release/iptv-player-googletv-$version.apk"
PATH="$JAVA_HOME/bin:$PATH" ~/Library/Android/sdk/build-tools/36.0.0/apksigner verify "$apk"

git commit --quiet -am "Release $version"
git tag "v$version"
git push --quiet origin HEAD "v$version"
gh release create "v$version" "$apk" \
    --title "IPTV Player for Google TV $version" \
    --notes "${notes:-Install the APK on the TV, or update from Sources → Update in the app.}"
echo "Released v$version ($apk)"
