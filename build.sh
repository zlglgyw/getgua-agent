#!/usr/bin/env bash
# Build manual APK Agen getgua (tanpa Gradle — JVM tidak bisa akses
# network di sandbox ini, jadi Gradle tidak bisa jalan).
# Pola yang sama dipakai getgua client v1.0.0/v1.0.1 + prototype UI.
#
# Output: getgua-agent-1.0.0-debug.apk (di direktori ini).
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

SDK="$HOME/Android/sdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34"
JDK="$HOME/jdk/jdk17"
KOTLINC="$HOME/workspace/tools/kotlinc/bin/kotlinc"
STDLIB="$HOME/workspace/tools/kotlinc/lib/kotlin-stdlib.jar"

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

VERSION_CODE=1
VERSION_NAME="1.0.0"
APK_OUT="getgua-agent-1.0.0-debug.apk"

echo "== Agen getgua $VERSION_NAME (code $VERSION_CODE) =="
echo "Java: $(javac -version 2>&1)"

rm -rf build
mkdir -p build/gen build/classes build/dex build/apk

echo "-> aapt2 compile (resources)..."
"$BT/aapt2" compile --dir app/src/main/res -o build/res.zip

echo "-> aapt2 link..."
"$BT/aapt2" link \
    -I "$PLATFORM/android.jar" \
    --manifest app/src/main/AndroidManifest.xml \
    --min-sdk-version 30 \
    --target-sdk-version 34 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    --java build/gen \
    -o build/unaligned.apk \
    build/res.zip

echo "-> javac (R.java)..."
javac -encoding UTF-8 \
    -cp "$PLATFORM/android.jar" \
    -d build/classes \
    $(find build/gen -name "*.java")

echo "-> kotlinc..."
"$KOTLINC" \
    -cp "$PLATFORM/android.jar:build/classes" \
    -jvm-target 17 \
    -d build/classes \
    $(find app/src/main/java -name "*.kt")

echo "-> d8 (dex)..."
"$BT/d8" \
    --lib "$PLATFORM/android.jar" \
    --output build/dex \
    --min-api 30 \
    $(find build/classes -name "*.class") \
    "$STDLIB"

echo "-> Packaging DEX..."
python3 - <<'PY'
import glob
import zipfile

with zipfile.ZipFile("build/unaligned.apk", "a", compression=zipfile.ZIP_DEFLATED) as apk:
    for dex in sorted(glob.glob("build/dex/*.dex")):
        apk.write(dex, dex.split("/")[-1])
PY

echo "-> zipalign..."
"$BT/zipalign" -f -p 4 build/unaligned.apk build/aligned.apk

echo "-> Signing..."
if [ ! -f debug.keystore ]; then
    echo "Creating debug.keystore..."
    keytool -genkey -v -keystore debug.keystore \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Agen Getgua Debug,O=getgua,C=ID"
fi
"$BT/apksigner" sign \
    --ks debug.keystore \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$APK_OUT" \
    build/aligned.apk

echo "-> Verifikasi..."
"$BT/apksigner" verify "$APK_OUT"
"$BT/aapt2" dump badging "$APK_OUT" | head -n 4
ls -lh "$APK_OUT"
echo "== BUILD OK: $APK_OUT =="
