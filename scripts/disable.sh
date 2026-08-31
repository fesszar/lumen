#!/usr/bin/env bash
# Disable one batch of packages on an Android TV. Never uninstalls. Never touches
# anything in protected.txt. Records what it did so undo.sh can reverse it.
#
#   ./disable.sh <TV-IP> <batch1|batch2|batch3|batch4|batch5>
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TV="${1:-}"; BATCH="${2:-}"
[ -n "$TV" ] && [ -n "$BATCH" ] || { echo "usage: $0 <TV-IP> <batch1..batch5>"; exit 1; }
[[ "$TV" == *:* ]] || TV="$TV:5555"

adb connect "$TV" >/dev/null 2>&1 || true
state=$(adb devices | awk -v t="$TV" '$1==t {print $2}')
[ "$state" = "device" ] || { echo "Not connected to $TV (state: ${state:-none}). See docs/01-connect.md"; exit 1; }

LOG="$HERE/disabled.txt"; touch "$LOG"
printf '%-46s %-14s %s\n' "PACKAGE" "RESULT" "NOTE"

while IFS=$'\t' read -r b pkg desc; do
  [[ "$b" =~ ^#|^$ ]] && continue
  [ "$b" = "$BATCH" ] || continue
  if grep -qxF "$pkg" "$HERE/protected.txt"; then
    printf '%-46s %-14s %s\n' "$pkg" "REFUSED" "on the protected list"; continue
  fi
  adb -s "$TV" shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
  if adb -s "$TV" shell pm list packages -d 2>/dev/null | tr -d '\r' | grep -qx "package:$pkg"; then
    printf '%-46s %-14s %s\n' "$pkg" "disabled" "$desc"
    grep -qxF "$pkg" "$LOG" || echo "$pkg" >> "$LOG"
  else
    printf '%-46s %-14s %s\n' "$pkg" "not-present" "nothing to do"
  fi
done < "$HERE/packages.txt"

echo
echo "Recorded in $LOG — $(wc -l < "$LOG" | tr -d ' ') packages disabled so far."
echo "Now test the TV: Inputs button, HDMI, Netflix, YouTube, sound, keyboard."
