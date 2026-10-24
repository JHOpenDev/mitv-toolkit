#!/usr/bin/env bash
# 按清单隐藏包(pm hide,可逆)。用法: TV_IP=x.x.x.x scripts/debloat.sh debloat/tier1-ads-telemetry.txt
set -uo pipefail
TV_IP=${TV_IP:?请先 export TV_IP=电视IP}
LIST=${1:?用法: debloat.sh <清单文件>}
S="$TV_IP:5555"
ADB=${ADB:-adb}

$ADB connect "$S" >/dev/null 2>&1
[ "$($ADB -s "$S" get-state 2>/dev/null)" = device ] || { echo "连不上"; exit 1; }
[ -r "$LIST" ] || { echo "读不了清单: $LIST"; exit 1; }

# 改动前存档
$ADB -s "$S" shell dumpsys package > "pkgdump-backup-$(date +%Y%m%d-%H%M%S).txt" 2>/dev/null

PKGS=$(grep -vE '^\s*(#|$)' "$LIST" | sed -E 's/\s+#.*$//' | awk '{print $1}')
for p in $PKGS; do
  OUT=$($ADB -s "$S" shell pm hide "$p" 2>&1 | tr -d '\r')
  case "$OUT" in
    *true*) echo "$p -> 已隐藏";;
    *)      echo "$p -> 异常: $OUT";;
  esac
done

echo; echo "== 验证 =="
$ADB -s "$S" shell dumpsys package 2>/dev/null | \
  awk '/^  Package \[/{p=$2} /^    User 0:.*hidden=true/{print p}' | sort -u | grep -E "$(echo $PKGS | tr ' ' '|')" || echo "(无匹配,检查上面输出)"
