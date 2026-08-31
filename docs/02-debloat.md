# 2. Clear out the factory clutter

Your television ships with software you never chose: an engine that fills the home screen with promoted content, two services uploading what you watch, a screensaver that serves adverts, a demo reel meant for a shop floor, and — on the set this was built for — a text messaging app.

This page switches all of that off. Nothing is deleted.

**[Jump straight to the script →](#the-script)**

---

## The one rule that matters

Everything here uses:

```bash
adb shell pm disable-user --user 0 <package>
```

and never `pm uninstall`.

A **disabled** package sits on the TV doing nothing and comes back instantly:

```bash
adb shell pm enable <package>
```

An **uninstalled** system package on a locked-down television may never come back — not from the Play Store, not from a factory reset in some cases. The disk saving is a few megabytes. It is not worth it.

## What this guide will not tell you to do

**Do not root the TV, unlock the bootloader, or flash a custom ROM** to get further than this. On modern televisions that can drop the Widevine DRM certificate from L1 to L3, which permanently caps Netflix and Prime Video at standard definition. You cannot put it back. The extra packages you'd reach are not worth a permanently worse picture.

---

## Work in batches, and test between them

Ten packages at a time, then stop and check the television still does the things you need:

- The remote's **Inputs / Source** button opens the input menu
- You can switch to **HDMI** and back
- **Netflix** opens
- **YouTube** opens
- **Sound** works
- The **on-screen keyboard** appears when you search

If something breaks, re-enable that whole batch first, then go one at a time to find the culprit. This is dull and it is why this guide has no horror stories in it.

---

## Never disable these

Some of these have names that look like junk. They are not.

| Package | Why it stays |
|---|---|
| `com.tcl.suspension` | **The Inputs / Source menu.** The name looks like nonsense. Disable it and you cannot reach HDMI. |
| `com.tcl.dashboard` | TCL's quick-settings panel. Same class of trap. |
| `com.tcl.tv`, `com.tcl.tvinput` | HDMI and antenna input services |
| `com.tvos`, `com.tcl.system.server` | Core platform services |
| `com.google.android.tv.remote.service` | The remote |
| `com.tcl.tcl_bt_rcu_service`, `com.tcl.autopair` | Bluetooth remote pairing |
| `com.google.android.gms`, `com.google.android.gsf` | Play Services. Half your apps stop working without it. |
| `com.android.vending` | Play Store |
| `com.android.location.fused` | **Disable this and the TV boot-loops.** |
| `*.inputmethod.*` | Every keyboard |
| `com.google.android.apps.tv.launcherx` | The Google home screen — **only** after another launcher is installed and confirmed working |
| `com.google.android.marvin.talkback`, `com.google.android.tts` | **The screen reader and its speech engine.** Leave these alone. |

**On the screen reader:** it looks like a voice assistant in a package list, and it is not. Disabling it takes the television away from anyone who needs it read aloud. This guide got that wrong once and had to reverse it.

**On other brands:** find your own equivalents before you start. `adb shell pm list packages | grep <yourbrand>` gets you the list. If you cannot work out what a package does, leave it.

---

## The batches

Every one of these was disabled and tested on a TCL Smart TV Pro running Android 11.

### Batch 1 — advertising and telemetry

The batch that does the most visible work.

| Package | What it is |
|---|---|
| `com.tcl.waterfall.overseas` | TCL's "waterfall" engine — the promo and recommendation rows on the home screen. **The main offender.** |
| `com.tcl.overseasappshow` | TCL's app-promotion showcase |
| `com.tcl.bi` | TCL usage analytics upload |
| `com.tcl.logkit` | TCL device log collection |
| `com.tcl.t_solo` | TCL marketing app |
| `com.tcl.esticker` | In-store demo sticker overlay |
| `com.tcl.ocean.instructions` | First-run demo reel |
| `com.tcl.factory.view` | Factory test menu |
| `com.google.android.apps.tv.dreamx` | Google TV ambient screensaver, which serves promoted content |
| `android.autoinstalls.config.google.gtvpai` | Google TV's silent installer for sponsored apps |

### Batch 2 — voice, mirroring, unused preloads

| Package | What it is |
|---|---|
| `com.google.android.katniss` | Google voice search service. **The one with real memory weight** — it releases its memory the moment it's disabled. |
| `com.google.android.tv.assistant` | Google Assistant front end |
| `com.tcl.assistant` | TCL's own voice assistant |
| `com.tcl.miracast` | Miracast screen mirroring |
| `com.tcl.MultiScreenInteraction_TV` | TCL multi-screen service |
| `com.tcl.messagebox` | TCL marketing message inbox |
| `com.tcl.usercenter` | TCL account centre |

Skip the voice ones if you use the microphone button on your remote.

### Batch 3 — setup wizards and reporting

| Package | What it is |
|---|---|
| `com.tcl.channelplus` | TCL Channel — free ad-supported streaming |
| `com.tcl.tv.tclhome_passive` | Home-screen passive promo content |
| `com.tcl.partnercustomizer` | Partner preload installer |
| `com.google.android.feedback` | Crash and feedback reporting |
| `com.google.android.partnersetup` | Partner attribution reporting |
| `com.google.android.tungsten.setupwraith` | Google TV setup wizard — done its job |
| `com.google.android.onetimeinitializer` | Runs once at first setup, never again |
| `com.tcl.initsetup` | TCL setup wizard |
| `com.tcl.useragreement` | Terms prompts |
| `com.tcl.keyhelp` | On-screen remote-key help |

### Batch 4 — things that make no sense on a television

| Package | What it is |
|---|---|
| `com.tcl.guard` | TCL "guard" cleaner and its memory manager |
| `com.android.messaging` | An SMS app. On a TV. |
| `com.android.camera2` | Camera app, no camera |
| `com.android.printspooler` | Print queue |
| `com.android.dynsystem` | Dynamic System Updates, a developer feature |
| `com.android.cts.ctsshim`, `com.android.cts.priv.ctsshim` | Empty compliance-test stubs |
| `org.chromium.webview_shell` | WebView developer test shell |
| `com.tcl.gamebar` | Gaming overlay |
| `com.tcl.micmanager` | Far-field microphone manager |

### Batch 5 — your call

Only you know whether you use these.

| Package | What it is |
|---|---|
| `com.google.android.videos` | Google TV movie store |
| `com.seraphic.openinet.pre` | A preloaded browser component that behaves like adware |
| `com.tcl.browser` | TCL's web browser |
| `com.tcl.gallery`, `com.tcl.ui_mediaCenter` | Photo viewer and USB media player |
| `com.google.android.apps.nbu.smartconnect.tv` | Phone-assisted setup |
| `com.tcl.xian.StartandroidService` | An undocumented TCL startup service |
| Preinstalled streaming apps | Disney+, HBO Max, Prime Video, Canal+, Showmax and friends — disable the ones you don't subscribe to |

---

## The script

Clone the repo, or just grab the two scripts.

```bash
# See what you have, before touching anything
./scripts/measure.sh 192.168.1.2

# Disable a batch, with the protected list enforced
./scripts/disable.sh 192.168.1.2 batch1

# ... test the TV, then
./scripts/disable.sh 192.168.1.2 batch2
```

`disable.sh` **refuses** to touch anything on the never-disable list, even if you pass it by hand. It writes every package it disables to `disabled.txt`, which is what the undo script reads.

## Speed and storage, while you're here

```bash
# Halve the animation speeds. Makes the whole TV feel quicker.
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

# Clear app caches. On the test TV this recovered 5 GB.
adb shell pm trim-caches 8G
```

The cache clear is the single biggest measurable win in this whole guide, and it takes ten seconds.

---

## Undoing all of it

```bash
./scripts/undo.sh 192.168.1.2
```

Re-enables every package in `disabled.txt`, restores the animation scales, and reboots. Because nothing was uninstalled, this is complete.

**Next: [install Lumen →](03-launcher.md)**
