#!/bin/bash
# Starts a release: tags the current main and pushes the tag. GitHub Actions then builds the signed
# APK from that tag and publishes the release, where the app's updater finds it.
#
# Usage: scripts/release.sh 0.1.2
set -euo pipefail

version=${1:?Usage: scripts/release.sh <major.minor.patch>}
cd "$(dirname "$0")/.."

[[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3"; exit 1; }
[[ $(git branch --show-current) == main ]] || { echo "Release from main"; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "Commit or stash your changes first"; exit 1; }
git fetch --quiet origin main --tags
[[ $(git rev-parse HEAD) == $(git rev-parse origin/main) ]] || { echo "main isn't in sync with origin/main"; exit 1; }
git rev-parse "v$version" >/dev/null 2>&1 && { echo "v$version already exists"; exit 1; }

git tag -a "v$version" -m "IPTV Player for Google TV $version"
git push --quiet origin "v$version"
echo "Pushed v$version. Follow the build with: gh run watch"
