#!/bin/bash
set -e
P="$HOME/Desktop/tv-launcher"
SDK="$HOME/Library/Android/sdk"
cd "$P"

BT=$(ls -1 "$SDK/build-tools" | sort -V | tail -1)
BTP="$SDK/build-tools/$BT"
PLAT=$(ls -1 "$SDK/platforms" | sort -V | tail -1)
JAR="$SDK/platforms/$PLAT/android.jar"
echo "build-tools=$BT  platform=$PLAT"
[ -f "$JAR" ] || { echo "NO android.jar at $JAR"; exit 1; }

rm -rf out gen classes dex
mkdir -p out gen classes dex

echo "--- aapt2 compile ---"
"$BTP/aapt2" compile --dir res -o out/res.zip

echo "--- aapt2 link ---"
"$BTP/aapt2" link \
  -o out/base.apk \
  -I "$JAR" \
  --manifest AndroidManifest.xml \
  -R out/res.zip \
  --java gen \
  --min-sdk-version 30 \
  --target-sdk-version 30 \
  --auto-add-overlay

echo "--- javac ---"
find src gen -name '*.java' > out/sources.txt
# Do NOT pipe javac: a pipeline's exit status is the last command's, so errors vanish.
if ! javac -source 11 -target 11 -nowarn -encoding UTF-8 \
      -classpath "$JAR" -d classes @out/sources.txt 2> out/javac.log; then
  echo "FATAL: javac failed"
  grep -v "bootstrap class path" out/javac.log | head -40
  exit 1
fi
grep -v "bootstrap class path" out/javac.log | head -3
NCLASSES=$(find classes -name '*.class' | wc -l | tr -d ' ')
echo "compiled $NCLASSES class files"
[ "$NCLASSES" -gt 0 ] || { echo "FATAL: javac produced no classes"; exit 1; }

echo "--- d8 ---"
find classes -name '*.class' > out/classes.txt
"$BTP/d8" --release --lib "$JAR" --min-api 30 --output dex @out/classes.txt

echo "--- package ---"
[ -f dex/classes.dex ] || { echo "FATAL: d8 produced no dex/classes.dex"; ls -la dex; exit 1; }
echo "dex: $(ls -la dex/classes.dex | awk '{print $5}') bytes"
cp out/base.apk out/unsigned.apk
cp dex/classes.dex ./classes.dex
zip -q -X "$P/out/unsigned.apk" classes.dex
rm -f ./classes.dex
unzip -l out/unsigned.apk | grep -q "classes.dex" \
  || { echo "FATAL: classes.dex did not make it into the apk"; unzip -l out/unsigned.apk; exit 1; }
# And the dex must actually contain our entry point.
unzip -p out/unsigned.apk classes.dex | strings | grep -q "HomeActivity" \
  || { echo "FATAL: dex exists but has no HomeActivity in it"; exit 1; }

echo "--- keystore ---"
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$HOME/.android"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  echo "created debug keystore"
else
  echo "using existing debug keystore"
fi

echo "--- zipalign + sign ---"
"$BTP/zipalign" -f -p 4 out/unsigned.apk out/aligned.apk
"$BTP/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$P/lumen.apk" out/aligned.apk
"$BTP/apksigner" verify --print-certs "$P/lumen.apk" | head -3

# Never report success on an apk with no code in it. This shipped twice.
unzip -l "$P/lumen.apk" | grep -q "classes.dex" \
  || { echo "FATAL: signed apk has no classes.dex"; exit 1; }
unzip -l "$P/lumen.apk" | grep -E "classes.dex|resources.arsc|AndroidManifest"
ls -la "$P/lumen.apk"
echo "BUILD OK"
