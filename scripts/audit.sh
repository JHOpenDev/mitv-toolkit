#!/usr/bin/env bash
# 只读体检:版本/存储/内存/自启/已隐藏清单。不改动任何东西。
# 用法: TV_IP=192.168.x.x scripts/audit.sh
set -uo pipefail
TV_IP=${TV_IP:?请先 export TV_IP=电视IP}
S="$TV_IP:5555"
ADB=${ADB:-adb}

$ADB connect "$S" >/dev/null 2>&1
[ "$($ADB -s "$S" get-state 2>/dev/null)" = device ] || { echo "连不上 $S (电视待机?按遥控器唤醒)"; exit 1; }

echo "== 系统 =="
$ADB -s "$S" shell "getprop ro.build.version.incremental; getprop ro.product.model; getprop ro.build.version.release"
echo "== 存储 =="
$ADB -s "$S" shell df | grep -E 'Filesystem|/data|/system'
echo "== 内存 =="
$ADB -s "$S" shell cat /proc/meminfo | grep -E 'MemTotal|MemFree|MemAvailable|^Cached|Swap'
echo "== 包数量 =="
$ADB -s "$S" shell pm list packages | wc -l
echo "== 已隐藏/禁用核对 =="
$ADB -s "$S" shell dumpsys package > /tmp/pkgdump.$$ 2>&1
awk '/^  Package \[/{p=$2} /^    User 0:.*hidden=true/{print p}' /tmp/pkgdump.$$ | sort -u
rm -f /tmp/pkgdump.$$
echo "== 屏保/熄屏设置 =="
$ADB -s "$S" shell "settings get system screen_off_timeout; settings get secure screensaver_enabled"
echo "完成(以上未做任何改动)"
