#!/usr/bin/env bash
# Install debug + test APKs on a connected device, clear app data, run the
# instrumented test suite, capture a screenshot + logcat, and scan logcat
# for regression signatures (FATAL, ANR, StrictMode, thread violations).
#
# Usage: ./scripts/device-smoke-test.sh
#
# Requires: adb on PATH, exactly one Android device connected (or
#   ANDROID_SERIAL set to choose among multiple).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PKG=com.migspod
OUT="$ROOT/build/smoke"
mkdir -p "$OUT"

device_count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
if [ "$device_count" -eq 0 ]; then
    echo "error: no Android device connected" >&2
    exit 1
fi
if [ "$device_count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
    echo "error: multiple devices connected; set ANDROID_SERIAL to choose one" >&2
    adb devices >&2
    exit 1
fi

echo "→ Building + installing debug + test APKs"
./gradlew :app:installDebug :app:installDebugAndroidTest

echo "→ Clearing app data for a true first-launch state"
adb shell pm clear "$PKG" || true

echo "→ Running instrumented tests"
./gradlew :app:connectedDebugAndroidTest

echo "→ Capturing screenshot + logcat"
adb exec-out screencap -p > "$OUT/screen.png"
adb logcat -d > "$OUT/logcat.txt"

echo "→ Scanning logcat for regressions"
regressions=$(grep -E 'FATAL|ANR in|StrictMode|StrictMode policy violation' "$OUT/logcat.txt" || true)
if [ -n "$regressions" ]; then
    echo "✗ Regression signatures found in logcat:"
    echo "$regressions" | head -30
    echo "  Full logcat: $OUT/logcat.txt"
    exit 1
fi

echo "✓ Smoke test passed. Artifacts in $OUT"
