# 5. Troubleshooting

---

## Connecting

**`unable to connect ... Connection refused`**
Debugging isn't on, or the TV isn't listening yet. Re-check [Step 4](01-connect.md#step-4--turn-on-debugging), then reboot the TV. Some sets only start listening after Developer options has been opened once since boot.

**`unable to connect ... No route to host`**
Wrong IP, or the two devices are on different networks. Guest networks isolate devices from each other by design.

**`device unauthorized`**
The approval dialog is waiting on the television. Go and look at it.

**`device offline`**
```bash
adb disconnect
adb connect <TV-IP>:5555
```

**It hangs forever with no error**
Nearly always a VPN on the laptop. Turn it off.

**It worked yesterday and not today**
The TV's IP may have changed — routers hand out new ones. Check Settings → Network & Internet → Status again. Giving the TV a static lease in your router settings stops this recurring.

---

## After disabling packages

**The TV won't get past the boot animation**
You disabled something on the [never-disable list](02-debloat.md#never-disable-these) — most likely `com.android.location.fused`, which boot-loops. ADB usually still works during a boot loop:
```bash
adb connect <TV-IP>:5555
adb shell pm enable com.android.location.fused
adb reboot
```

**The Inputs / Source button does nothing**
`com.tcl.suspension` or your brand's equivalent. Its name looks like junk and it is not.
```bash
adb shell pm enable com.tcl.suspension
```

**Apps crash on launch, or won't sign in**
Play Services. `adb shell pm enable com.google.android.gms`

**The keyboard never appears**
```bash
adb shell ime list -s
```
If that's empty, you disabled every input method. Re-enable one.

**Something's broken and I don't know what I did**
```bash
./scripts/undo.sh <TV-IP>
```
Everything back, then start again one batch at a time.

---

## Lumen

**Black screen after setting it as home**
ADB still works. Put the old launcher back:
```bash
adb connect <TV-IP>:5555
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell input keyevent KEYCODE_HOME
```

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
You have a copy signed with a different key — usually your own build alongside the release APK. Uninstall first:
```bash
adb shell pm uninstall com.ghidi.lumen
```

**Set-as-home reports Success but the Google launcher still appears**
Known on several TVs, including the TCL this was built on. Use [the direct way](03-launcher.md#the-direct-way).

**An app is missing from the shelf**
It may be hidden — Settings → Apps on Home. Or it has no TV launcher entry, in which case it's in All apps but not the shelf.

**Selecting HDMI does nothing**
Some televisions won't hand passthrough inputs to a third-party app. Lumen falls back to opening the TV's own source menu. If neither works, use the remote's Source button; that always works.

**The Settings button is unreachable**
Press Up from the top row of apps. If you're in the Sources strip, Up twice.

---

## Still stuck

Open an issue with:

```bash
adb shell getprop ro.product.brand
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb logcat -d -t 200 | grep -i lumen
```

The brand, model and Android version matter most — nearly every problem in this list is brand-specific.
