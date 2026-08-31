#!/usr/bin/env bash
# Take a before/after reading. Run it once before you start and once at the end.
#   ./measure.sh <TV-IP>
set -euo pipefail
TV="${1:-}"; [ -n "$TV" ] || { echo "usage: $0 <TV-IP>"; exit 1; }
[[ "$TV" == *:* ]] || TV="$TV:5555"
adb connect "$TV" >/dev/null 2>&1 || true
A="adb -s $TV"

echo "=== $($A shell getprop ro.product.brand | tr -d '\r') $($A shell getprop ro.product.model | tr -d '\r') — Android $($A shell getprop ro.build.version.release | tr -d '\r') ==="
echo
echo "--- memory (kB) ---"
$A shell cat /proc/meminfo | tr -d '\r' | grep -E "^(MemTotal|MemFree|MemAvailable|Cached|SwapTotal|SwapFree):"
echo
echo "--- storage ---"
$A shell df /data 2>/dev/null | tr -d '\r' | tail -1
echo
echo "--- packages ---"
echo "total:    $($A shell pm list packages 2>/dev/null | tr -d '\r' | grep -c package:)"
echo "enabled:  $($A shell pm list packages -e 2>/dev/null | tr -d '\r' | grep -c package:)"
echo "disabled: $($A shell pm list packages -d 2>/dev/null | tr -d '\r' | grep -c package:)"
echo
echo "--- home screen ---"
$A shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | grep -m1 packageName
