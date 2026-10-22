#!/data/data/com.termux/files/usr/bin/bash
# fix_aapt2.sh — replaces x86_64 aapt2 in Android SDK with native ARM64 binary
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
BT_DIR="$ANDROID_SDK_ROOT/build-tools/35.0.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

[[ -d "$BT_DIR" ]] || error "build-tools dir not found: $BT_DIR"

info "SDK aapt2: $(file "$BT_DIR/aapt2" 2>/dev/null || echo 'not found')"

# ── Find the native ARM64 aapt2 ───────────────────────────────────────────────
# Termux installs aapt2 to /data/data/com.termux/files/usr/bin/
# but $PATH inside the script may not include it; probe all known locations.
AAPT2_NATIVE=""
SEARCH_PATHS=(
    "/data/data/com.termux/files/usr/bin/aapt2"
    "$PREFIX/bin/aapt2"
    "$(command -v aapt2 2>/dev/null || echo '')"
)
for P in "${SEARCH_PATHS[@]}"; do
    if [[ -n "$P" && -f "$P" && -x "$P" ]]; then
        AAPT2_NATIVE="$P"
        break
    fi
done

# If still not found, install and try again
if [[ -z "$AAPT2_NATIVE" ]]; then
    info "aapt2 not found in PATH — installing…"
    pkg install -y aapt2 2>/dev/null || true
    for P in "/data/data/com.termux/files/usr/bin/aapt2" "$PREFIX/bin/aapt2"; do
        if [[ -f "$P" && -x "$P" ]]; then
            AAPT2_NATIVE="$P"
            break
        fi
    done
fi

[[ -n "$AAPT2_NATIVE" ]] || error "Cannot find aapt2 binary. Run: pkg install aapt2"

info "Found native aapt2 at: $AAPT2_NATIVE"
info "Native aapt2: $(file "$AAPT2_NATIVE" 2>/dev/null)"
info "Version: $("$AAPT2_NATIVE" version 2>/dev/null || echo 'unknown')"

# ── Replace SDK aapt2 ─────────────────────────────────────────────────────────
[[ -f "$BT_DIR/aapt2" && ! -f "$BT_DIR/aapt2.x86_64" ]] && \
    mv "$BT_DIR/aapt2" "$BT_DIR/aapt2.x86_64" && \
    info "Backed up original as aapt2.x86_64"

cp "$AAPT2_NATIVE" "$BT_DIR/aapt2"
chmod 755 "$BT_DIR/aapt2"
info "Installed: $(file "$BT_DIR/aapt2")"
info "Runs:      $("$BT_DIR/aapt2" version 2>/dev/null || echo 'FAILED — wrong arch?')"

# ── Clear ALL Gradle caches ───────────────────────────────────────────────────
# AGP caches the aapt2 daemon path at configuration time; must wipe everything.
info "Stopping Gradle daemons…"
"$PROJECT_ROOT/gradlew" --stop 2>/dev/null || true

info "Clearing project build outputs…"
rm -rf "$PROJECT_ROOT/app/build" 2>/dev/null || true

info "Clearing project Gradle cache…"
rm -rf "$PROJECT_ROOT/.gradle" 2>/dev/null || true

info "Clearing global Gradle build-cache…"
rm -rf "$HOME/.gradle/caches/build-cache-1" 2>/dev/null || true
# Also clear the transforms cache — AGP stores aapt2 location in here
rm -rf "$HOME/.gradle/caches/transforms-3" 2>/dev/null || true
rm -rf "$HOME/.gradle/caches/*/transforms-*" 2>/dev/null || true

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Done! aapt2 replaced + all caches cleared.              ║${NC}"
echo -e "${GREEN}║  Now run:  bash scripts/build_termux.sh                  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
