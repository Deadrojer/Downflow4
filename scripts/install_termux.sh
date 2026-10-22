#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# install_termux.sh
#
# Installs the DownFlow APK on the same device running Termux.
# Requires "Install unknown apps" permission granted to Termux (or a file
# manager). Uses `am start` + `content:` URI via Termux:API if available,
# or falls back to a plain adb install over localhost.
#
# USAGE (in Termux, from project root):
#   bash scripts/install_termux.sh [path/to/app.apk]
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Locate APK ────────────────────────────────────────────────────────────────
if [[ -n "${1:-}" && -f "$1" ]]; then
    APK="$1"
elif [[ -f "$PROJECT_ROOT/.last_apk_path" ]]; then
    APK="$(cat "$PROJECT_ROOT/.last_apk_path")"
else
    APK=$(find "$PROJECT_ROOT/app/build/outputs/apk" -name "*.apk" | sort | tail -1)
fi

if [[ -z "$APK" || ! -f "$APK" ]]; then
    error "No APK found. Build first: bash scripts/build_termux.sh"
fi

info "APK: $APK ($(ls -lh "$APK" | awk '{print $5}'))"

# ── Method 1: adb over loopback (preferred — no extra permissions) ────────────
# Termux can run adb against the local device via TCP if USB debugging is on.
if command -v adb &>/dev/null; then
    info "Attempting adb install over TCP loopback…"
    # Android 11+ blocks loopback adb by default; this works if the user has
    # enabled "Wireless debugging" in Developer Options.
    ADB_ADDR="${ADB_HOST:-localhost}"
    ADB_PORT="${ADB_PORT:-5555}"

    if adb connect "$ADB_ADDR:$ADB_PORT" 2>&1 | grep -q "connected"; then
        adb -s "$ADB_ADDR:$ADB_PORT" install -r "$APK" && {
            info "Installed via adb. Launching DownFlow…"
            adb -s "$ADB_ADDR:$ADB_PORT" shell \
                am start -n com.downflow.app/.ui.MainActivity || true
            exit 0
        }
    fi
    warn "adb connect failed — trying fallback method."
fi

# ── Method 2: Copy APK to shared Downloads and prompt user ───────────────────
# This works without adb or root — copies the APK to the public Downloads
# folder and opens the package installer via am start.
DOWNLOADS="/sdcard/Download"
mkdir -p "$DOWNLOADS"

DEST="$DOWNLOADS/DownFlow.apk"
cp "$APK" "$DEST"
info "Copied APK to $DEST"

# Try to trigger the system package installer
if command -v am &>/dev/null; then
    info "Opening package installer…"
    am start \
        -a android.intent.action.VIEW \
        -d "file://$DEST" \
        -t "application/vnd.android.package-archive" \
        --grant-read-uri-permission 2>/dev/null || true
elif command -v termux-open &>/dev/null; then
    termux-open --content-type "application/vnd.android.package-archive" "$DEST"
else
    echo ""
    echo -e "${YELLOW}Automatic install not available.${NC}"
    echo "Open your file manager and tap:"
    echo "  $DEST"
    echo "to install DownFlow."
fi

echo ""
info "Done. If the installer didn't open, navigate to $DEST in your file manager and tap the APK."
