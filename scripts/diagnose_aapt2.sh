#!/data/data/com.termux/files/usr/bin/bash
# Run this FIRST to see exactly what aapt2 situation you have.
# Usage: bash scripts/diagnose_aapt2.sh

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT_DIR="$ANDROID_SDK_ROOT/build-tools/35.0.0"

echo "=== AAPT2 Diagnosis ==="
echo ""

echo "--- SDK aapt2 ---"
echo "Path: $BT_DIR/aapt2"
if [[ -f "$BT_DIR/aapt2" ]]; then
    file "$BT_DIR/aapt2"
    ls -lh "$BT_DIR/aapt2"
    echo "Try running it:"
    "$BT_DIR/aapt2" version 2>&1 || echo "(failed to execute)"
else
    echo "NOT FOUND"
fi

echo ""
echo "--- Termux aapt2 ---"
which aapt2 2>/dev/null || echo "not in PATH"
if command -v aapt2 &>/dev/null; then
    file "$(which aapt2)"
    aapt2 version 2>&1 || echo "(failed to execute)"
fi

echo ""
echo "--- Termux aapt ---"
which aapt 2>/dev/null || echo "not in PATH"

echo ""
echo "--- Device arch ---"
uname -m
file /data/data/com.termux/files/usr/bin/bash

echo ""
echo "--- All build-tools ---"
ls "$ANDROID_SDK_ROOT/build-tools/" 2>/dev/null || echo "no build-tools dir"

echo ""
echo "--- Gradle build-tools override in app/build.gradle ---"
grep -n "aapt2\|buildToolsVersion\|aaptOptions" ~/DownFlow/app/build.gradle.kts 2>/dev/null | head -20 || echo "not found"
