#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# build_aria2_termux.sh
#
# Builds aria2 from source inside Termux — use this ONLY if you want a
# custom or newer aria2 than the one in Termux repos.
#
# For most users, setup_termux.sh copies the pre-packaged Termux aria2 binary
# directly. That binary is already compiled for your device's ARM64 ABI and is
# all you need.
#
# Use THIS script if:
#   - The Termux repo aria2 is too old
#   - You want to enable/disable specific features at compile time
#   - You want a fully static build (no dependency on Termux libs)
#
# USAGE (in Termux):
#   bash scripts/build_aria2_termux.sh
# =============================================================================

set -euo pipefail

ARIA2_VERSION="1.37.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$HOME/aria2-build"
OUTPUT="$PROJECT_ROOT/app/src/main/assets/aria2c"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── Install build dependencies ────────────────────────────────────────────────
info "Installing Termux build dependencies…"
pkg install -y \
    build-essential \
    autoconf \
    automake \
    libtool \
    pkg-config \
    libcares \
    libexpat \
    libsqlite \
    openssl \
    zlib \
    libssh2 \
    wget

# ── Download aria2 source ─────────────────────────────────────────────────────
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

TARBALL="aria2-${ARIA2_VERSION}.tar.bz2"
if [[ ! -f "$TARBALL" ]]; then
    info "Downloading aria2 $ARIA2_VERSION…"
    wget -q --show-progress \
        "https://github.com/aria2/aria2/releases/download/release-${ARIA2_VERSION}/${TARBALL}"
fi

if [[ ! -d "aria2-${ARIA2_VERSION}" ]]; then
    tar xjf "$TARBALL"
fi

cd "aria2-${ARIA2_VERSION}"

# ── Configure ─────────────────────────────────────────────────────────────────
info "Configuring aria2…"

# Termux prefix — all libraries are here
PREFIX="$PREFIX"   # Termux sets $PREFIX to /data/data/com.termux/files/usr

./configure \
    --prefix="$PREFIX" \
    --without-gnutls \
    --with-openssl \
    --with-libcares \
    --with-sqlite3 \
    --with-libexpat \
    --with-libssh2 \
    --without-libxml2 \
    --with-ca-bundle="$PREFIX/etc/tls/cert.pem" \
    CXXFLAGS="-O2 -std=c++17" \
    CFLAGS="-O2"

# ── Build ─────────────────────────────────────────────────────────────────────
info "Building aria2 (this takes a few minutes)…"
make -j"$(nproc)"

# ── Install to assets ─────────────────────────────────────────────────────────
info "Copying binary to assets…"
mkdir -p "$(dirname "$OUTPUT")"
cp src/aria2c "$OUTPUT"
# Strip debug info to reduce APK size
strip --strip-all "$OUTPUT" 2>/dev/null || true
chmod 644 "$OUTPUT"

echo ""
info "✓  aria2c built and installed to: $OUTPUT"
file "$OUTPUT"
ls -lh "$OUTPUT"
