#!/usr/bin/env bash
# Put everything back. Re-enables every package disable.sh recorded, restores the
# animation scales, and reboots. Nothing was ever uninstalled, so this is complete.
#
#   ./undo.sh <TV-IP>
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TV="${1:-}"; [ -n "$TV" ] || { echo "usage: $0 <TV-IP>"; exit 1; }
[[ "$TV" == *:* ]] || TV="$TV:5555"
adb connect "$TV" >/dev/null 2>&1 || true

LOG="$HERE/disabled.txt"
if [ -f "$LOG" ]; then
  while read -r pkg; do
    [ -n "$pkg" ] || continue
    echo "enabling $pkg"
    adb -s "$TV" shell pm enable "$pkg" >/dev/null 2>&1 || true
  done < "$LOG"
else
  echo "No disabled.txt found — nothing recorded to undo."
fi

for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb -s "$TV" shell settings put global "$k" 1
done

echo "Removing Lumen, if it is installed."
adb -s "$TV" shell pm uninstall com.ghidi.lumen >/dev/null 2>&1 || true
adb -s "$TV" shell pm enable com.google.android.apps.tv.launcherx >/dev/null 2>&1 || true

echo "Rebooting."
adb -s "$TV" reboot
