#!/usr/bin/env bash
# 开机时间线:电视重启后尽快运行,抓取本次开机各阶段耗时
# 用法: TV_IP=192.168.x.x scripts/boot-timeline.sh
set -uo pipefail
TV_IP=${TV_IP:?请先 export TV_IP=电视IP}
S="$TV_IP:5555"
ADB=${ADB:-adb}

$ADB connect "$S" >/dev/null 2>&1
[ "$($ADB -s "$S" get-state 2>/dev/null)" = device ] || { echo "连不上"; exit 1; }

TMP=$(mktemp)
$ADB -s "$S" shell "logcat -d -b events -v time" > "$TMP" 2>/dev/null

echo "== boot_progress 时间线(毫秒,自内核启动) =="
grep -E 'boot_progress' "$TMP" | sed 's/I\/boot_progress//; s/([ 0-9]*)//'

echo; echo "== 开机进程启动数 =="
grep -c am_proc_start "$TMP"

echo; echo "== 桌面就绪 =="
grep -E 'am_create_activity.*tvhome|am_create_activity.*minihome|enable_screen' "$TMP" | tail -5

echo; echo "== 参考(小米电视2S清理后基线): 扫描~12.9s 屏幕点亮~34s 桌面~33s 进程40 =="
rm -f "$TMP"
