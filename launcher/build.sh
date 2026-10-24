#!/usr/bin/env bash
# MiniHome 一键构建:自动下载SDK组件 -> 从电视提取信号源图标 -> 编译签名 -> (可选)安装
# 依赖: adb, java 11+, curl, unzip, zip; 电视需 adb 在线(提取图标)
# 用法: TV_IP=192.168.x.x ./build.sh        构建+安装
#       ./build.sh --no-install             只构建
set -euo pipefail
cd "$(dirname "$0")"
TV_IP=${TV_IP:-}
INSTALL=1
[ "${1:-}" = "--no-install" ] && INSTALL=0

SDK=${SDK:-$PWD/sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-22/android.jar

# ── 1. SDK 组件(仅首次,约200MB) ──
if [ ! -f "$JAR" ]; then
  echo "== 下载 Android SDK 组件 =="
  mkdir -p "$SDK" && cd "$SDK"
  curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"
  unzip -q -o cmdtools.zip
  yes | cmdline-tools/bin/sdkmanager --sdk_root="$SDK" "platforms;android-22" "build-tools;34.0.0" >/dev/null
  cd - >/dev/null
fi

# ── 2. 从电视提取信号源图标(版权原因仓库不带,从你自己设备固件提取) ──
if [ ! -f res/drawable-xhdpi/icon_tv_source.png ]; then
  echo "== 从电视提取图标 =="
  [ -n "$TV_IP" ] || { echo "需要 TV_IP=... 提取图标(首次构建)"; exit 1; }
  adb connect "$TV_IP:5555" >/dev/null 2>&1
  adb -s "$TV_IP:5555" pull /system/vendor/app/TvHome/TvHome.apk /tmp/TvHome_icon_src.apk >/dev/null
  mkdir -p res/drawable-xhdpi
  unzip -o -q /tmp/TvHome_icon_src.apk \
    "res/drawable-xhdpi-v4/icon_tv_source.png" \
    "res/drawable-xhdpi-v4/icon_hdmi_source.png" \
    "res/drawable-xhdpi-v4/icon_av_source.png" \
    "res/drawable-xhdpi-v4/mitv_icn_setting.png" -d /tmp/icons_$$
  cp /tmp/icons_$$/res/drawable-xhdpi-v4/*.png res/drawable-xhdpi/ && rm -rf /tmp/icons_$$
fi

# ── 3. 编译(注意:d8 有内部类NPE bug,源码必须全部是顶层类) ──
echo "== 编译 =="
rm -rf obj out build gen; mkdir -p obj out build gen
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 21 --target-sdk-version 22 \
  --version-code 13 --version-name 2.2 \
  --java gen -o build/app.unsigned.apk build/res.zip
javac --release 8 -cp "$JAR" -d obj \
  gen/com/minihome/tv/R.java src/com/minihome/tv/*.java
( cd obj && zip -q -r ../classes.jar com/ )
$BT/d8 --lib "$JAR" --release --min-api 21 --output out classes.jar
( cd out && cp ../build/app.unsigned.apk app.unaligned.apk && zip -q -j app.unaligned.apk classes.dex )

# ── 4. 签名(keystore 不入库;升级安装必须同签名,请自行保管) ──
if [ ! -f release.keystore ]; then
  echo "== 生成签名密钥(请保管好,丢失则无法覆盖升级) =="
  keytool -genkeypair -keystore release.keystore -alias minihome \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass minihome -keypass minihome -dname "CN=MiniHome" >/dev/null 2>&1
fi
$BT/apksigner sign --ks release.keystore --ks-pass pass:minihome --key-pass pass:minihome \
  --min-sdk-version 21 --out out/MiniHome.apk out/app.unaligned.apk
$BT/apksigner verify --min-sdk-version 21 out/MiniHome.apk
echo "构建完成: out/MiniHome.apk ($(du -h out/MiniHome.apk | cut -f1))"

# ── 5. 安装(电视若弹「允许安装」框,遥控器按允许) ──
if [ "$INSTALL" = 1 ]; then
  [ -n "$TV_IP" ] || { echo "跳过安装(未设置 TV_IP)"; exit 0; }
  adb -s "$TV_IP:5555" install -r out/MiniHome.apk
  adb -s "$TV_IP:5555" shell am start -n com.minihome.tv/.MainActivity
  echo "已启动。设为默认桌面: adb shell pm hide com.mitv.tvhome (HOME键即归 MiniHome)"
fi
