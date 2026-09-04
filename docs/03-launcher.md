# 3. Install Lumen

Five minutes, and it is reversible in one command.

---

## Get the APK

**[Download from Releases →](../../../releases/latest)**

Or build it yourself — see [below](#build-it-yourself). It takes one command and no Gradle.

### About the signature

The APK is **debug-signed**. That's normal for something you sideload, and it means:

- Your TV may warn you before installing. That warning is correct — you are installing software from someone you don't know.
- It will never auto-update. New versions come from here.
- If you later build your own copy, Android will treat it as a different app and you'll need to uninstall this one first.

If any of that bothers you, build it yourself. The whole point of the source being here is that you don't have to trust the binary.

---

## Install

With your TV [connected](01-connect.md):

```bash
adb install -r lumen.apk
```

```
Success
```

Then open it once to check it runs before you make it your home screen:

```bash
adb shell am start -n com.ghidi.lumen/.HomeActivity
```

You should see your apps on a dark, frosted shelf. Move around with the remote — left and right along the apps, down to the Sources strip, up to Settings.

**Don't skip this step.** Setting a launcher as your home screen before confirming it works is how people end up rebooting into a black screen.

---

## Make it your home screen

Two ways, depending on your television.

### The polite way — try this first

```bash
adb shell cmd package set-home-activity com.ghidi.lumen/.HomeActivity
```

Check it took:

```bash
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME | grep packageName
```

If that prints `com.ghidi.lumen`, you're done. Press Home on the remote.

Some televisions report `Success` and then keep resolving Home to the Google launcher anyway. The TCL this was built on does exactly that, and has neither `cmd role` nor `clear-package-preferred-activities` to fix it. If that's you, use the second way.

### The direct way

Disable the Google launcher, so Lumen is the only home screen left:

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell input keyevent KEYCODE_HOME
```

**Only do this once you have confirmed Lumen runs.** If something is wrong, one command puts the old home screen back:

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
```

ADB survives reboots on most sets, so you keep a way in even if the screen is black. Worth checking before you rely on it: reboot the TV, then `adb connect` again.

---

## Using it

| Where | What |
|---|---|
| **Left / Right** | Move along the app shelf |
| **Down** | Into the Sources strip — HDMI ports and the tuner |
| **Up** | To the Settings button in the top bar |
| **The last tile** | All apps — everything installed, in a grid |
| **OK on a source** | Switches the TV to that input |

## Settings

Sixteen options in four named groups. Left always goes back to the group list; OK switches a two-value option in one press and opens a marked list for anything longer. Everything that belongs to the television rather than the launcher is behind the last row.

**What is on Home**

| Setting | Options | What it does |
|---|---|---|
| **Apps on the shelf** | per-app | Choose which apps appear and in what order. Hidden ones stay in All apps. |
| **Carry on** | On / Off | The three resume cards above the shelf. |
| **Poster art** | On / Off | Artwork on those cards. Off means Lumen makes no network request at all. |
| **Sources strip** | Visible / Hidden | The HDMI row. |
| **Name your inputs** | — | Call HDMI 2 "PlayStation". Pick from a list, or type. |
| **App names** | Always / Focused only | Names under every tile, or only the focused one. |

**How it looks**

| Setting | Options | What it does |
|---|---|---|
| **Tile size** | Small 240 / Medium 288 / Large 336 px | Sized for your viewing distance. |
| **Glass strength** | 8 / 15 / 22 / 30% | Panel opacity. |
| **Background** | Adaptive, Aurora, Ember, Slate, Neutral | Adaptive tints the ground from whatever is focused. |

**Easier to see and use**

| Setting | Options | What it does |
|---|---|---|
| **High contrast** | On / Off | Near-black panels, thicker focus ring. |
| **Reduce motion** | On / Off | Animation duration to zero. Also obeys the TV's own animation scale. |
| **Text size** | Normal / Large / Larger | Names, the Carry on row and this screen. |

**This launcher**

| Setting | Options | What it does |
|---|---|---|
| **What changed on this TV** | — | The first-boot notice again, for whoever did not do this. |
| **Save your settings** | — | Writes them out, so a reinstall does not start from scratch. |
| **Load saved settings** | — | Reads them back. |
| **Open the TV settings** | — | Picture, sound, network. |

---

## The one permission, and the one request

Lumen asks for two things in the manifest and nothing else.

`READ_TV_LISTINGS` is a runtime permission — you are asked once, on first run, and the system dialog says exactly what it is for. It lets the launcher read the television's own watch-next table: what you were watching in each app, which episode, how far in, and the link that resumes it. Say no and the Carry on row simply never appears; nothing else changes. It is not the privileged `ACCESS_ALL_EPG_DATA`, which Lumen does not hold and does not need.

`INTERNET` is used for one thing: downloading the poster on a Carry on card, from the URL the streaming app itself wrote into the row. Nothing is uploaded and nothing is reported. **Settings › What is on Home › Poster art › Off** stops it entirely, and the cards keep their titles, episodes and progress, because all of that already lives on the device.

---

## Build it yourself

No Gradle, no Android Studio project, no downloads beyond the SDK you may already have.

**You need:** a JDK (17 works), and the Android SDK command-line tools — specifically `aapt2`, `d8`, `zipalign` and `apksigner` from any recent `build-tools`, plus one `platforms/android-XX/android.jar`.

```bash
git clone https://github.com/fesszar/lumen.git
cd lumen/app
./build.sh
```

It prints the APK path when it's done. The script deliberately **fails loudly** rather than producing a broken build — it checks that `javac` actually succeeded, that a `classes.dex` exists, and that the dex contains the entry-point class. Every one of those checks is there because a silent failure produced an installable APK with no code in it during development.

On macOS the script looks in `~/Library/Android/sdk`. Edit the `SDK=` line at the top for anywhere else.

---

## Uninstall

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm uninstall com.ghidi.lumen
adb shell input keyevent KEYCODE_HOME
```

Your old home screen is back, exactly as it was.

**Next: [the design →](04-design.md)**
