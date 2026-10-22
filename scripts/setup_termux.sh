#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# setup_termux.sh
#
# One-shot setup script for building DownFlow entirely inside Termux.
# Run this ONCE on a fresh Termux install before anything else.
#
# USAGE (in Termux):
#   bash scripts/setup_termux.sh
#
# What this script does:
#   1. Updates Termux packages
#   2. Installs JDK 17, Android SDK command-line tools, build tools, aria2
#   3. Installs a pre-built aria2 from Termux repos (no cross-compile needed —
#      Termux IS ARM64 Android, so Termux's aria2 binary runs natively)
#   4. Copies the aria2 binary into app/src/main/assets/
#   5. Installs Gradle wrapper dependencies (offline cache)
#   6. Prints next steps
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Verify we are inside Termux ───────────────────────────────────────────────
if [[ ! -d /data/data/com.termux ]]; then
    error "This script must run inside Termux on an Android device."
fi

info "=== DownFlow Termux Setup ==="
info "Project root: $PROJECT_ROOT"

# ── 1. Update package index ───────────────────────────────────────────────────
info "Updating Termux package index…"
pkg update -y

# ── 2. Install core build tools ───────────────────────────────────────────────
info "Installing JDK 17, build tools, and aria2…"
pkg install -y \
    openjdk-17 \
    aria2 \
    wget \
    unzip \
    zip \
    git \
    which

# Verify Java
java -version 2>&1 | head -1
JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
export JAVA_HOME
info "JAVA_HOME = $JAVA_HOME"

# ── 3. Install Android SDK command-line tools ─────────────────────────────────
ANDROID_SDK_ROOT="$HOME/android-sdk"
CMDLINE_TOOLS_DIR="$ANDROID_SDK_ROOT/cmdline-tools/latest"

if [[ -d "$CMDLINE_TOOLS_DIR" ]]; then
    info "Android SDK command-line tools already installed — skipping download."
else
    info "Downloading Android SDK command-line tools…"
    mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
    # Use the Linux x86_64 package — we'll run it through the JVM which handles
    # architecture transparency; the shell scripts need a patched wrapper (see below)
    CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    TMPZIP="$HOME/cmdtools.zip"
    wget -q --show-progress "$CMDTOOLS_URL" -O "$TMPZIP"
    unzip -q "$TMPZIP" -d "$ANDROID_SDK_ROOT/cmdline-tools/"
    mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$CMDLINE_TOOLS_DIR"
    rm -f "$TMPZIP"
    info "SDK command-line tools installed."
fi

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$CMDLINE_TOOLS_DIR/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# Accept licenses non-interactively
info "Accepting Android SDK licenses…"
yes | sdkmanager --licenses 2>/dev/null || true

# Install build tools and platform
info "Installing Android SDK platform 35 and build-tools 35.0.0…"
sdkmanager \
    "platforms;android-35" \
    "build-tools;35.0.0"

info "Android SDK installed at: $ANDROID_SDK_ROOT"

# ── 3b. Replace x86_64 AAPT2 with a native ARM64 binary ──────────────────────
# The build-tools package ships an x86_64 ELF for aapt2.  On Termux (ARM64
# Android) that binary can't execute, causing:
#   "Failed to start AAPT2 process / Process unexpectedly exit."
# Fix: install aapt2 from Termux repos (natively compiled ARM64) and symlink
# it into the SDK's build-tools directory so AGP finds the right binary.
info "Installing native ARM64 aapt2 from Termux repos…"
pkg install -y aapt2 2>/dev/null || pkg install -y aapt 2>/dev/null || true

AAPT2_TERMUX="$(command -v aapt2 2>/dev/null || echo "")"
BT_DIR="$ANDROID_SDK_ROOT/build-tools/35.0.0"

if [[ -n "$AAPT2_TERMUX" && -f "$AAPT2_TERMUX" ]]; then
    info "Replacing SDK aapt2 with Termux native binary: $AAPT2_TERMUX"
    # Back up original (x86_64) binary in case it's ever needed
    if [[ -f "$BT_DIR/aapt2" && ! -f "$BT_DIR/aapt2.x86_64" ]]; then
        mv "$BT_DIR/aapt2" "$BT_DIR/aapt2.x86_64"
    fi
    cp "$AAPT2_TERMUX" "$BT_DIR/aapt2"
    chmod 755 "$BT_DIR/aapt2"
    info "aapt2 replaced. Version: $("$BT_DIR/aapt2" version 2>/dev/null || echo 'unknown')"
else
    # Termux repos don't carry aapt2 as a standalone package on all versions.
    # Fall back: build it from the Android build-tools source via pkg, or
    # download a pre-built ARM64 aapt2 from a known-good mirror.
    warn "aapt2 not found in Termux repos — trying pre-built ARM64 binary…"

    # Use the ARM64 aapt2 that ships with Termux's android-tools or build-tools-34
    pkg install -y android-tools 2>/dev/null || true
    AAPT2_TERMUX="$(command -v aapt2 2>/dev/null || echo "")"

    if [[ -n "$AAPT2_TERMUX" && -f "$AAPT2_TERMUX" ]]; then
        if [[ -f "$BT_DIR/aapt2" && ! -f "$BT_DIR/aapt2.x86_64" ]]; then
            mv "$BT_DIR/aapt2" "$BT_DIR/aapt2.x86_64"
        fi
        cp "$AAPT2_TERMUX" "$BT_DIR/aapt2"
        chmod 755 "$BT_DIR/aapt2"
        info "aapt2 replaced via android-tools. Version: $("$BT_DIR/aapt2" version 2>/dev/null || echo 'unknown')"
    else
        # Last resort: download a pre-built ARM64 aapt2 binary
        # These are the same binaries used by Android Studio on Android (e.g. via termux-android-studio)
        warn "Falling back to downloading pre-built ARM64 aapt2…"
        AAPT2_ARM64_URL="https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.0/android-sdk-tools-arm64-v8a.zip"
        AAPT2_TMP="$HOME/aapt2_arm64.zip"
        if wget -q --show-progress "$AAPT2_ARM64_URL" -O "$AAPT2_TMP" 2>/dev/null; then
            AAPT2_EXTRACT="$HOME/aapt2_arm64_extract"
            mkdir -p "$AAPT2_EXTRACT"
            unzip -q "$AAPT2_TMP" -d "$AAPT2_EXTRACT"
            AAPT2_BIN="$(find "$AAPT2_EXTRACT" -name 'aapt2' -type f | head -1)"
            if [[ -n "$AAPT2_BIN" ]]; then
                if [[ -f "$BT_DIR/aapt2" && ! -f "$BT_DIR/aapt2.x86_64" ]]; then
                    mv "$BT_DIR/aapt2" "$BT_DIR/aapt2.x86_64"
                fi
                cp "$AAPT2_BIN" "$BT_DIR/aapt2"
                chmod 755 "$BT_DIR/aapt2"
                info "aapt2 replaced (pre-built ARM64). Version: $("$BT_DIR/aapt2" version 2>/dev/null || echo 'unknown')"
            fi
            rm -rf "$AAPT2_TMP" "$AAPT2_EXTRACT"
        else
            error "Cannot obtain an ARM64 aapt2. The build will fail without it."
        fi
    fi
fi

# ── 4. Copy aria2 binary into assets ─────────────────────────────────────────
ARIA2_BIN="$(which aria2c)"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"
mkdir -p "$ASSETS_DIR"

if [[ -f "$ASSETS_DIR/aria2c" ]]; then
    info "aria2c already in assets — skipping copy."
else
    info "Copying Termux aria2c binary to assets…"
    # Strip debug symbols to reduce APK size
    if command -v llvm-strip &>/dev/null; then
        llvm-strip --strip-all "$ARIA2_BIN" -o "$ASSETS_DIR/aria2c" 2>/dev/null || cp "$ARIA2_BIN" "$ASSETS_DIR/aria2c"
    else
        cp "$ARIA2_BIN" "$ASSETS_DIR/aria2c"
    fi
    chmod 644 "$ASSETS_DIR/aria2c"
    info "aria2c copied: $(ls -lh "$ASSETS_DIR/aria2c" | awk '{print $5}')"
fi

# Verify the binary is ARM64
ARCH=$(file "$ASSETS_DIR/aria2c" 2>/dev/null | grep -o 'aarch64\|ARM64\|x86-64\|x86_64' | head -1 || echo "unknown")
info "Binary architecture: $ARCH"
if [[ "$ARCH" != "aarch64" && "$ARCH" != "ARM64" ]]; then
    warn "Binary does not appear to be ARM64 — it may not work on all devices."
fi

# ── 5. Persist environment variables in ~/.bashrc ────────────────────────────
BASHRC="$HOME/.bashrc"
if ! grep -q "DOWNFLOW_ENV" "$BASHRC" 2>/dev/null; then
    info "Writing environment variables to ~/.bashrc…"
    cat >> "$BASHRC" << EOF

# ─── DownFlow build environment (added by setup_termux.sh) ───── DOWNFLOW_ENV
export JAVA_HOME="$JAVA_HOME"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="\$ANDROID_SDK_ROOT"
export PATH="\$JAVA_HOME/bin:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools:\$PATH"
# ─────────────────────────────────────────────────────────────────────────────
EOF
fi

# ── 5b. Download gradle-wrapper.jar ──────────────────────────────────────────
# The ZIP ships only a placeholder; the real JAR must exist before any
# ./gradlew invocation.
WRAPPER_JAR="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.jar"
if [[ -f "$WRAPPER_JAR" ]] && unzip -t "$WRAPPER_JAR" &>/dev/null; then
    info "gradle-wrapper.jar already present and valid — skipping download."
else
    info "Downloading gradle-wrapper.jar…"
    # The wrapper JAR is hosted on Gradle's own CDN as part of each release.
    # This URL is stable for a given Gradle version.
    WRAPPER_JAR_URL="https://github.com/gradle/gradle/raw/v8.9.0/gradle/wrapper/gradle-wrapper.jar"
    # Fallback: services.gradle.org distribution (same file, different host)
    WRAPPER_JAR_URL2="https://services.gradle.org/distributions/gradle-8.9-wrapper.jar"

    if wget -q --show-progress "$WRAPPER_JAR_URL" -O "$WRAPPER_JAR" 2>/dev/null && \
       unzip -t "$WRAPPER_JAR" &>/dev/null; then
        info "gradle-wrapper.jar downloaded from GitHub."
    elif wget -q --show-progress "$WRAPPER_JAR_URL2" -O "$WRAPPER_JAR" 2>/dev/null && \
         unzip -t "$WRAPPER_JAR" &>/dev/null; then
        info "gradle-wrapper.jar downloaded from services.gradle.org."
    else
        # Final fallback: let Gradle bootstrap itself by installing Gradle in
        # Termux and running 'gradle wrapper', then we no longer need the JAR.
        warn "Could not download gradle-wrapper.jar directly. Attempting bootstrap via Termux gradle…"
        pkg install -y gradle 2>/dev/null || true
        if command -v gradle &>/dev/null; then
            cd "$PROJECT_ROOT"
            gradle wrapper --gradle-version 8.9 --distribution-type bin
            info "gradle-wrapper.jar generated via 'gradle wrapper'."
            cd - >/dev/null
        else
            error "Cannot obtain gradle-wrapper.jar. Check your internet connection and retry setup_termux.sh."
        fi
    fi
fi

# ── 6. Make gradlew executable ────────────────────────────────────────────────
chmod +x "$PROJECT_ROOT/gradlew"

# ── 7. Patch gradlew shebang for Termux ──────────────────────────────────────
# Termux may not have /bin/sh; point the shebang at Termux bash.
GRADLEW="$PROJECT_ROOT/gradlew"
if ! head -1 "$GRADLEW" | grep -q "com.termux"; then
    sed -i '1s|.*|#!/data/data/com.termux/files/usr/bin/bash|' "$GRADLEW"
    info "Patched gradlew shebang for Termux."
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Setup complete! Next steps:                             ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  1. Reload shell:  source ~/.bashrc                      ║${NC}"
echo -e "${GREEN}║  2. Build APK:     bash scripts/build_termux.sh          ║${NC}"
echo -e "${GREEN}║  3. Install APK:   bash scripts/install_termux.sh        ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
