#!/usr/bin/env bash
# =============================================================================
# build_aria2_android.sh
#
# Cross-compiles aria2 for Android ARM64 (arm64-v8a) using the Android NDK.
# The resulting aria2c binary is a static executable (no dynamic Android libs
# required) suitable for embedding in the DownFlow APK asset.
#
# USAGE
#   export ANDROID_NDK_HOME=/path/to/ndk   # e.g. ~/Android/Sdk/ndk/26.3.11579264
#   bash scripts/build_aria2_android.sh
#
# The compiled binary is written to:
#   app/src/main/assets/aria2c
#
# DEPENDENCIES (on the build host – Ubuntu / Debian recommended)
#   sudo apt-get install -y \
#       autoconf automake libtool pkg-config \
#       make cmake git curl unzip zip
#
# VERSIONS (update these to build a different release)
ARIA2_VERSION="1.37.0"
EXPAT_VERSION="2.6.2"
C_ARES_VERSION="1.28.1"
LIBSSH2_VERSION="1.11.0"
SQLITE_VERSION="3450300"    # 3.45.3 – year×100+month is the sqlite tag
ZLIB_VERSION="1.3.1"
OPENSSL_VERSION="3.3.0"
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="${TMPDIR:-/tmp}/aria2-android-build"
SYSROOT_INSTALL="${BUILD_DIR}/sysroot"
OUTPUT="${PROJECT_ROOT}/app/src/main/assets/aria2c"

# ── NDK setup ─────────────────────────────────────────────────────────────────
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT}/ndk-bundle}"
if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
    echo "ERROR: ANDROID_NDK_HOME is not set or does not exist: $ANDROID_NDK_HOME"
    exit 1
fi

HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
HOST_ARCH="$(uname -m)"
TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${HOST_OS}-${HOST_ARCH}"
if [[ ! -d "$TOOLCHAIN" ]]; then
    # Some NDK versions use 'linux-x86_64' literally
    TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64"
fi
if [[ ! -d "$TOOLCHAIN" ]]; then
    echo "ERROR: Cannot find NDK toolchain under $ANDROID_NDK_HOME"
    exit 1
fi

API=26   # minSdkVersion

TARGET="aarch64-linux-android"
CC="${TOOLCHAIN}/bin/${TARGET}${API}-clang"
CXX="${TOOLCHAIN}/bin/${TARGET}${API}-clang++"
AR="${TOOLCHAIN}/bin/llvm-ar"
RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
STRIP="${TOOLCHAIN}/bin/llvm-strip"

export CC CXX AR RANLIB STRIP

COMMON_FLAGS="-Os -fPIC"
CFLAGS="${COMMON_FLAGS}"
CXXFLAGS="${COMMON_FLAGS} -std=c++17"
LDFLAGS="-static-libstdc++ -static-libgcc"

export CFLAGS CXXFLAGS LDFLAGS

CONFIGURE_COMMON=(
    --host="${TARGET}"
    --build="$(uname -m)-linux-gnu"
    --prefix="${SYSROOT_INSTALL}"
    --disable-shared
    --enable-static
)

mkdir -p "${BUILD_DIR}" "${SYSROOT_INSTALL}"
cd "${BUILD_DIR}"

echo "=== Build directory : ${BUILD_DIR}"
echo "=== Sysroot install  : ${SYSROOT_INSTALL}"
echo "=== Toolchain        : ${TOOLCHAIN}"

# ── Helper ─────────────────────────────────────────────────────────────────────
fetch_and_extract() {
    local url="$1" archive
    archive="$(basename "$url")"
    if [[ ! -f "$archive" ]]; then
        echo "--- Downloading $archive"
        curl -fSL "$url" -o "$archive"
    fi
    # Extract only once (check for marker file)
    local dir="${archive%.tar.*}"
    dir="${dir%.tgz}"
    if [[ ! -d "$dir" ]]; then
        tar xf "$archive"
    fi
    echo "$dir"
}

# ── zlib ──────────────────────────────────────────────────────────────────────
echo ">>> Building zlib ${ZLIB_VERSION}"
dir=$(fetch_and_extract "https://zlib.net/zlib-${ZLIB_VERSION}.tar.gz")
pushd "$dir"
./configure --prefix="${SYSROOT_INSTALL}" --static
make -j"$(nproc)" install
popd

# ── OpenSSL ───────────────────────────────────────────────────────────────────
echo ">>> Building OpenSSL ${OPENSSL_VERSION}"
dir=$(fetch_and_extract "https://www.openssl.org/source/openssl-${OPENSSL_VERSION}.tar.gz")
pushd "$dir"
./Configure android-arm64 \
    -D__ANDROID_API__=${API} \
    --prefix="${SYSROOT_INSTALL}" \
    --openssldir="${SYSROOT_INSTALL}/ssl" \
    no-shared \
    ANDROID_NDK_ROOT="${ANDROID_NDK_HOME}"
make -j"$(nproc)" install_sw
popd

# ── c-ares ────────────────────────────────────────────────────────────────────
echo ">>> Building c-ares ${C_ARES_VERSION}"
dir=$(fetch_and_extract "https://c-ares.org/download/c-ares-${C_ARES_VERSION}.tar.gz")
pushd "$dir"
./configure "${CONFIGURE_COMMON[@]}" \
    --disable-tests
make -j"$(nproc)" install
popd

# ── expat ─────────────────────────────────────────────────────────────────────
echo ">>> Building expat ${EXPAT_VERSION}"
dir=$(fetch_and_extract "https://github.com/libexpat/libexpat/releases/download/R_${EXPAT_VERSION//./_}/expat-${EXPAT_VERSION}.tar.bz2")
pushd "$dir"
./configure "${CONFIGURE_COMMON[@]}" \
    --without-xmlwf
make -j"$(nproc)" install
popd

# ── SQLite ────────────────────────────────────────────────────────────────────
echo ">>> Building SQLite ${SQLITE_VERSION}"
dir=$(fetch_and_extract "https://www.sqlite.org/2024/sqlite-autoconf-${SQLITE_VERSION}.tar.gz")
pushd "$dir"
./configure "${CONFIGURE_COMMON[@]}"
make -j"$(nproc)" install
popd

# ── libssh2 ───────────────────────────────────────────────────────────────────
echo ">>> Building libssh2 ${LIBSSH2_VERSION}"
dir=$(fetch_and_extract "https://www.libssh2.org/download/libssh2-${LIBSSH2_VERSION}.tar.gz")
pushd "$dir"
./configure "${CONFIGURE_COMMON[@]}" \
    --with-openssl \
    --with-libssl-prefix="${SYSROOT_INSTALL}" \
    --without-libgcrypt
make -j"$(nproc)" install
popd

# ── aria2 ─────────────────────────────────────────────────────────────────────
echo ">>> Building aria2 ${ARIA2_VERSION}"
dir=$(fetch_and_extract "https://github.com/aria2/aria2/releases/download/release-${ARIA2_VERSION}/aria2-${ARIA2_VERSION}.tar.bz2")
pushd "$dir"

PKG_CONFIG_PATH="${SYSROOT_INSTALL}/lib/pkgconfig:${SYSROOT_INSTALL}/lib64/pkgconfig" \
./configure "${CONFIGURE_COMMON[@]}" \
    --without-gnutls \
    --with-openssl \
    --with-openssl-prefix="${SYSROOT_INSTALL}" \
    --with-libcares \
    --with-sqlite3 \
    --with-libexpat \
    --with-libssh2 \
    --without-libxml2 \
    --with-ca-bundle='/etc/ssl/certs/ca-certificates.crt' \
    ARIA2_STATIC=yes \
    PKG_CONFIG_PATH="${SYSROOT_INSTALL}/lib/pkgconfig:${SYSROOT_INSTALL}/lib64/pkgconfig"

make -j"$(nproc)"

popd

# ── Install ────────────────────────────────────────────────────────────────────
BINARY="${BUILD_DIR}/${dir}/src/aria2c"
echo ">>> Stripping binary"
"${STRIP}" --strip-all "$BINARY"

echo ">>> Installing to assets"
mkdir -p "$(dirname "$OUTPUT")"
cp "$BINARY" "$OUTPUT"
chmod 644 "$OUTPUT"   # assets are read-only; setExecutable happens at runtime

echo ""
echo "✓  aria2c built successfully"
file "$OUTPUT"
ls -lh "$OUTPUT"
echo "Output: $OUTPUT"
