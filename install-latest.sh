#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-/Users/chrisalvis/Library/Android/sdk/platform-tools/adb}"
REPO="Alvis1337/rotato"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

echo "Fetching latest release info..."
TAG=$(gh release view --repo "$REPO" --json tagName -q '.tagName')
APK_NAME=$(gh release view "$TAG" --repo "$REPO" --json assets -q '.assets[] | select(.name | endswith(".apk")) | .name')

if [[ -z "$APK_NAME" ]]; then
  echo "No APK found in release $TAG"
  exit 1
fi

echo "Downloading $APK_NAME ($TAG)..."
gh release download "$TAG" --repo "$REPO" --pattern "*.apk" --dir "$TMPDIR"

APK_PATH="$TMPDIR/$APK_NAME"

# Check for a connected device
DEVICES=$("$ADB" devices | tail -n +2 | grep -v '^$' | grep -v 'offline')
if [[ -z "$DEVICES" ]]; then
  echo "No ADB device connected. Plug in your phone and enable USB debugging."
  exit 1
fi

DEVICE_COUNT=$(echo "$DEVICES" | wc -l | tr -d ' ')
if [[ "$DEVICE_COUNT" -gt 1 ]]; then
  echo "Multiple devices detected — picking the first one:"
  echo "$DEVICES"
fi

echo "Installing to device..."
"$ADB" install -r "$APK_PATH"

echo "Done — rotato $TAG installed."
