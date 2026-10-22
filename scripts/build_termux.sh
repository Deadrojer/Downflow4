#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# build_termux.sh
#
# Builds the DownFlow debug APK entirely inside Termux.
# Run setup_termux.sh first.
#
# USAGE (in Termux, from project root):
#   bash scripts/build_termux.sh [release]
#
# Pass "release" as first argument to build a release APK (unsigned).
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_TYPE="${1:-debug}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
step()  { echo -e "${CYAN}[STEP]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

cd "$PROJECT_ROOT"

# ── Environment checks ────────────────────────────────────────────────────────
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
    export JAVA_HOME
fi

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    ANDROID_SDK_ROOT="$HOME/android-sdk"
    export ANDROID_SDK_ROOT
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
fi

export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Verify Java
java -version 2>&1 | head -1 || error "Java not found. Run setup_termux.sh first."

# Verify SDK
if [[ ! -d "$ANDROID_SDK_ROOT/platforms/android-35" ]]; then
    error "Android SDK platform 35 not found at $ANDROID_SDK_ROOT. Run setup_termux.sh first."
fi

# Verify aria2c in assets
ASSETS="$PROJECT_ROOT/app/src/main/assets"
if [[ ! -f "$ASSETS/aria2c" ]]; then
    warn "aria2c binary not found in assets. Attempting to copy from Termux…"
    ARIA2_BIN="$(which aria2c 2>/dev/null)" || error "aria2 not installed. Run: pkg install aria2"
    mkdir -p "$ASSETS"
    cp "$ARIA2_BIN" "$ASSETS/aria2c"
    chmod 644 "$ASSETS/aria2c"
    info "aria2c copied from $ARIA2_BIN"
fi

# ── Verify AAPT2 is ARM64 ─────────────────────────────────────────────────────
# The SDK's build-tools ship an x86_64 aapt2 which crashes on ARM64 Termux:
#   "Failed to start AAPT2 process / Process unexpectedly exit."
# Check EVERY build — the SDK dir persisting doesn't mean we fixed it yet.
BT_DIR="$ANDROID_SDK_ROOT/build-tools/35.0.0"
AAPT2_SDK="$BT_DIR/aapt2"
if [[ -f "$AAPT2_SDK" ]]; then
    AAPT2_ARCH=$(file "$AAPT2_SDK" 2>/dev/null | grep -oE 'x86-64|x86_64|aarch64|ARM64' | head -1 || echo "unknown")
    if [[ "$AAPT2_ARCH" == "x86-64" || "$AAPT2_ARCH" == "x86_64" ]]; then
        warn "SDK aapt2 is x86_64 — replacing before build…"
        # Try pkg first, then android-tools
        pkg install -y aapt2 2>/dev/null || pkg install -y android-tools 2>/dev/null || true
        AAPT2_NATIVE="$(command -v aapt2 2>/dev/null || echo "")"
        if [[ -n "$AAPT2_NATIVE" && "$(file "$AAPT2_NATIVE" 2>/dev/null)" == *aarch64* ]]; then
            [[ ! -f "$BT_DIR/aapt2.x86_64" ]] && mv "$AAPT2_SDK" "$BT_DIR/aapt2.x86_64"
            cp "$AAPT2_NATIVE" "$AAPT2_SDK"
            chmod 755 "$AAPT2_SDK"
            info "aapt2 replaced: $("$AAPT2_SDK" version 2>/dev/null || echo ok)"
            # Must clear build cache — AGP caches the old binary invocation
            info "Clearing Gradle build cache after aapt2 replacement…"
            rm -rf "$PROJECT_ROOT/.gradle/build-cache" "$PROJECT_ROOT/app/build" 2>/dev/null || true
            ./gradlew --stop 2>/dev/null || true
        else
            error "x86_64 aapt2 detected and no ARM64 replacement available.\nRun: bash scripts/fix_aapt2.sh"
        fi
    else
        info "aapt2 arch: ${AAPT2_ARCH} ✓"
    fi
fi

# Verify gradle-wrapper.jar exists and is a valid ZIP/JAR
WRAPPER_JAR="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.jar"
if [[ ! -f "$WRAPPER_JAR" ]] || ! unzip -t "$WRAPPER_JAR" &>/dev/null; then
    warn "gradle-wrapper.jar is missing or invalid — attempting to download…"
    WRAPPER_JAR_URL="https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
    if wget -q --show-progress "$WRAPPER_JAR_URL" -O "$WRAPPER_JAR" && \
       unzip -t "$WRAPPER_JAR" &>/dev/null; then
        info "gradle-wrapper.jar downloaded."
    else
        # Fallback: use system gradle to regenerate the wrapper
        pkg install -y gradle 2>/dev/null || true
        if command -v gradle &>/dev/null; then
            gradle wrapper --gradle-version 8.9 --distribution-type bin
            info "gradle-wrapper.jar regenerated via system gradle."
        else
            error "Cannot obtain gradle-wrapper.jar. Run setup_termux.sh first."
        fi
    fi
fi

# Verify gradlew
chmod +x gradlew

# ── Termux memory / Gradle tuning ─────────────────────────────────────────────
# Termux devices may have limited RAM. We cap the Gradle daemon to 1 GB and
# disable the daemon for single-shot CI-style builds to avoid OOM kills.
# Users with more RAM can remove --no-daemon for faster incremental builds.
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
TOTAL_RAM_MB=$((TOTAL_RAM_KB / 1024))
info "Device RAM: ${TOTAL_RAM_MB} MB"

if (( TOTAL_RAM_MB < 3000 )); then
    warn "Low RAM detected (${TOTAL_RAM_MB} MB). Using --no-daemon and reduced heap."
    DAEMON_FLAG="--no-daemon"
    JVM_MAX="768m"; JVM_MIN="256m"
else
    DAEMON_FLAG=""
    JVM_MAX="1536m"; JVM_MIN="512m"
fi

# JAVA_TOOL_OPTIONS is read by the JVM itself before any launcher argument
# parsing — it never suffers from shell quoting issues. This is the most
# reliable way to set heap sizes from a shell script on Termux.
export JAVA_TOOL_OPTIONS="-Xmx${JVM_MAX} -Xms${JVM_MIN} -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"
info "JVM options: $JAVA_TOOL_OPTIONS"

# Write local.properties so AGP finds the SDK without Android Studio
cat > "$PROJECT_ROOT/local.properties" << EOF
sdk.dir=${ANDROID_SDK_ROOT}
EOF
info "Wrote local.properties with sdk.dir=${ANDROID_SDK_ROOT}"

# Limit parallel workers to avoid OOM
CPU_CORES=$(nproc 2>/dev/null || echo 2)
MAX_WORKERS=$(( CPU_CORES > 2 ? 2 : CPU_CORES ))
info "Using ${MAX_WORKERS} Gradle worker(s)."

# ── Build ─────────────────────────────────────────────────────────────────────
if [[ "$BUILD_TYPE" == "release" ]]; then
    step "Assembling RELEASE APK (unsigned)…"
    TASK="assembleRelease"
else
    step "Assembling DEBUG APK…"
    TASK="assembleDebug"
fi

# Disable configuration cache on Termux — it can exhaust disk space on some
# devices and is not worth the overhead for one-off builds.
# -Dgradle.user.home lets us point at a writable Termux home directory.
# -Dorg.gradle.project.android.sdk.path tells AGP where the SDK lives without
#  needing a local.properties file.
./gradlew "$TASK" \
    $DAEMON_FLAG \
    --max-workers="$MAX_WORKERS" \
    --no-configuration-cache \
    --stacktrace \
    2>&1 | tee "$PROJECT_ROOT/build.log"

# ── Locate output APK ─────────────────────────────────────────────────────────
if [[ "$BUILD_TYPE" == "release" ]]; then
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
else
    APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
fi

if [[ ! -f "$APK_PATH" ]]; then
    # Try to find it
    APK_PATH=$(find "$PROJECT_ROOT/app/build/outputs/apk" -name "*.apk" | head -1)
fi

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
    error "Build completed but APK not found. Check build.log for details."
fi

APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Build successful!                                       ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
printf "${GREEN}║  APK : %-50s║${NC}\n" "$(basename "$APK_PATH") ($APK_SIZE)"
printf "${GREEN}║  Path: %-50s║${NC}\n" "${APK_PATH/#$HOME/\~}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Install it:  bash scripts/install_termux.sh             ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"

# Write APK path for install script to read
echo "$APK_PATH" > "$PROJECT_ROOT/.last_apk_path"
