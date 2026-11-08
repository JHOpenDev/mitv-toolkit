#!/usr/bin/env bash
# MiniHome 一键构建: 自动下载SDK组件 -> 从电视提取老系统磁贴图标 -> 编译签名 -> (可选)安装
# 依赖: adb, java 11+, curl, unzip, zip; 电视 adb 在线可自动提取图标(离线则用占位图)
# 用法: TV_IP=192.168.x.x ./build.sh        构建+安装(免人值守,自动点掉安装确认框)
#       ./build.sh --no-install             只构建
set -euo pipefail
cd "$(dirname "$0")"
TV_IP=${TV_IP:-}
INSTALL=1
[ "${1:-}" = "--no-install" ] && INSTALL=0

SDK=${SDK:-$PWD/../sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-22/android.jar

# ── 1. SDK 组件(仅首次,约260MB;也可用环境变量 SDK= 指向已有目录) ──
if [ ! -f "$JAR" ]; then
  echo "== 下载 Android SDK 组件 =="
  mkdir -p "$SDK" && cd "$SDK"
  curl -sL -o bt34.zip "https://dl.google.com/android/repository/build-tools_r34-macosx.zip"
  curl -sL -o p22.zip  "https://dl.google.com/android/repository/android-22_r02.zip"
  unzip -q -o bt34.zip -d bt34raw && unzip -q -o p22.zip -d p22raw
  mkdir -p build-tools/34.0.0/lib platforms/android-22
  cp bt34raw/android-14/aapt2 build-tools/34.0.0/
  cp bt34raw/android-14/lib/d8.jar bt34raw/android-14/lib/apksigner.jar build-tools/34.0.0/lib/
  printf '#!/bin/sh\nexec java -cp "$(dirname "$0")/lib/d8.jar" com.android.tools.r8.D8 "$@"\n'  > build-tools/34.0.0/d8
  printf '#!/bin/sh\nexec java -cp "$(dirname "$0")/lib/apksigner.jar" com.android.apksigner.ApkSignerTool "$@"\n' > build-tools/34.0.0/apksigner
  chmod +x build-tools/34.0.0/{aapt2,d8,apksigner}
  cp "$(find p22raw -name android.jar | head -1)" platforms/android-22/
  cd - >/dev/null
fi

# ── 2. 老系统磁贴图标(版权原因不入库:优先从电视固件提取,离线生成占位图) ──
if [ ! -f res/drawable-xhdpi/mitv_icn_apps.png ]; then
  if [ -n "$TV_IP" ]; then
    echo "== 从电视提取老桌面图标 =="
    adb connect "$TV_IP:5555" >/dev/null 2>&1 || true
    adb -s "$TV_IP:5555" pull /system/vendor/app/TvHome/TvHome.apk /tmp/TvHome_icon_src.apk >/dev/null 2>&1 \
    && mkdir -p res/drawable-xhdpi \
    && unzip -o -q /tmp/TvHome_icon_src.apk "res/drawable-xhdpi-v4/mitv_icn_apps.png" -d /tmp/icons_$$ \
    && cp /tmp/icons_$$/res/drawable-xhdpi-v4/*.png res/drawable-xhdpi/ && rm -rf /tmp/icons_$$ || true
  fi
  if [ ! -f res/drawable-xhdpi/mitv_icn_apps.png ]; then
    echo "== (离线)生成占位图标,可自行替换 res/drawable-xhdpi/mitv_icn_apps.png =="
    mkdir -p res/drawable-xhdpi
    python3 - <<'PY'
import struct, zlib, pathlib
def png(path, rgb):
    W = H = 96; r = 22; rows = []
    for y in range(H):
        row = bytearray([0])
        for x in range(W):
            cx = min(max(x, r), W - 1 - r); cy = min(max(y, r), H - 1 - r)
            inside = (x - cx) ** 2 + (y - cy) ** 2 <= r * r or (r <= x < W - r) or (r <= y < H - r)
            row += bytes(rgb) if inside else b"\x00\x00\x00\x00"
        rows.append(bytes(row))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    pathlib.Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b""))
png("res/drawable-xhdpi/mitv_icn_apps.png", (120, 134, 168, 255))
PY
  fi
fi

# ── 3. 编译(注意: d8 对内部类/匿名类有 NPE bug, 源码必须全部是顶层类) ──
echo "== 编译 =="
rm -rf obj out build gen; mkdir -p obj out build gen
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 21 --target-sdk-version 22 \
  --version-code 49 --version-name 5.8 \
  --java gen -o build/app.unsigned.apk build/res.zip
javac --release 8 -cp "$JAR" -d obj \
  gen/com/minihome/tv/R.java src/com/minihome/tv/*.java
( cd obj && zip -q -r ../classes.jar com/ )
$BT/d8 --lib "$JAR" --release --min-api 21 --output out classes.jar
( cd out && cp ../build/app.unsigned.apk app.unaligned.apk && zip -q -j app.unaligned.apk classes.dex )

# ── 4. 签名(keystore 不入库; 升级安装必须同签名, 请自行保管) ──
if [ ! -f release.keystore ]; then
  echo "== 生成签名密钥(请保管好, 丢失则无法覆盖升级) =="
  keytool -genkeypair -keystore release.keystore -alias minihome \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass minihome -dname "CN=MiniHome" >/dev/null 2>&1
fi
$BT/apksigner sign --ks release.keystore --ks-pass pass:minihome \
  --min-sdk-version 21 --out out/MiniHome.apk out/app.unaligned.apk
$BT/apksigner verify --min-sdk-version 21 out/MiniHome.apk
echo "构建完成: out/MiniHome.apk ($(du -h out/MiniHome.apk | cut -f1))"

# ── 5. 免人值守安装(MIUI 的「安装确认」框会自动点掉) ──
if [ "$INSTALL" = 1 ]; then
  [ -n "$TV_IP" ] || { echo "跳过安装(未设置 TV_IP)"; exit 0; }
  adb connect "$TV_IP:5555" >/dev/null 2>&1 || true
  adb -s "$TV_IP:5555" push out/MiniHome.apk /data/local/tmp/minihome.apk >/dev/null
  ( adb -s "$TV_IP:5555" shell "pm install -r /data/local/tmp/minihome.apk" >/dev/null 2>&1 & )
  sleep 5
  for t in 1 2 3; do
    adb -s "$TV_IP:5555" shell "uiautomator dump /sdcard/mh_install.xml" >/dev/null 2>&1
    adb -s "$TV_IP:5555" pull /sdcard/mh_install.xml /tmp/mh_install.xml >/dev/null 2>&1
    XY=$(python3 -c "
import re
try:
    s = open('/tmp/mh_install.xml', encoding='utf-8', errors='ignore').read()
    if '安装确认' in s:
        for m in re.finditer(r'<node[^>]*>', s):
            n = m.group(0)
            if re.search(r'text=\"确定\"', n):
                b = re.search(r'bounds=\"(\[[0-9,\]\[]+\])\"', n)
                l = [int(x) for x in re.findall(r'\d+', b.group(1))]
                print((l[0]+l[2])//2, (l[1]+l[3])//2); break
except Exception: pass" | head -1)
    [ -n "$XY" ] && { echo "自动点掉安装确认框 @$XY"; adb -s "$TV_IP:5555" shell "input tap $XY"; break; }
    sleep 3
  done
  for w in 1 2 3 4 5; do
    sleep 3
    adb -s "$TV_IP:5555" shell "dumpsys package com.minihome.tv | grep -m1 versionCode" | grep -q "versionCode=49" && break
  done
  adb -s "$TV_IP:5555" shell "rm /data/local/tmp/minihome.apk" >/dev/null 2>&1
  adb -s "$TV_IP:5555" shell "am start -n com.minihome.tv/.MainActivity" >/dev/null 2>&1
  echo "安装完成并已启动"
fi
