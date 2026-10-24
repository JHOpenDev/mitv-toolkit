#!/usr/bin/env bash
# 按清单恢复包(pm unhide)。用法: TV_IP=x.x.x.x scripts/restore.sh <清单文件>
set -uo pipefail
TV_IP=${TV_IP:?请先 export TV_IP=电视IP}
LIST=${1:?用法: restore.sh <清单文件>}
S="$TV_IP:5555"
ADB=${ADB:-adb}

$ADB connect "$S" >/dev/null 2>&1
[ "$($ADB -s "$S" get-state 2>/dev/null)" = device ] || { echo "连不上"; exit 1; }

PKGS=$(grep -vE '^\s*(#|$)' "$LIST" | sed -E 's/\s+#.*$//' | awk '{print $1}')
for p in $PKGS; do
  OUT=$($ADB -s "$S" shell pm unhide "$p" 2>&1 | tr -d '\r')
  case "$OUT" in
    *false*) echo "$p -> 已恢复";;
    *)       echo "$p -> 异常: $OUT";;
  esac
done
