# 1. Connect your laptop to your TV

Everything else in this project runs through one channel: a tool called **ADB** talking to your television over your home Wi-Fi. Set that up once and the rest is copy-and-paste.

Budget about fifteen minutes the first time. You will not need a cable, a developer account, or an app.

![How the connection works](../art/connection.svg)

---

## Before you start

- A television running **Android TV or Google TV**. If your remote has a Google Assistant button and the TV has a Play Store, that's it.
- A **laptop** — macOS, Windows or Linux, all three are covered below.
- Both on the **same Wi-Fi network**. Not a guest network. 2.4 GHz and 5 GHz bands of the same router are fine.

You do **not** need to root the TV, unlock anything, or void a warranty. Nothing here does that.

---

## Step 1 — Put both devices on the same Wi-Fi

**On the TV:** Settings → **Network & Internet**. Check which network it's joined to and write the name down.

**On the laptop:** click the Wi-Fi icon and confirm you're on that same name.

> **The single most common failure.** Many routers publish a separate "guest" network, and many publish `MyWiFi` and `MyWiFi-5G` as two entries. Devices on a guest network usually cannot see each other at all. If your TV is on the guest network, move it.

## Step 2 — Find your TV's IP address

**On the TV:** Settings → **Network & Internet** → tap your connected network → **Status** (some TVs call it *Network status* or *Advanced*).

You want the line marked **IP address**. It looks like `192.168.1.2` or `10.0.0.14`.

Write it down. Everywhere below that says `<TV-IP>`, put that number.

> If the number starts with `169.254`, the TV hasn't actually got onto the network. Reconnect it to Wi-Fi and look again.

## Step 3 — Turn on Developer options

This is hidden by design, and the way you reveal it is genuinely silly.

1. On the TV: Settings → **System** → **About**
2. Scroll to **Build** (sometimes *Build number*)
3. **Press OK on it seven times.** Keep pressing. A little counter appears saying "You are now 4 steps away from being a developer."
4. It ends with **"You are now a developer!"**

Some TCL and Hisense sets put this under Settings → **Device Preferences** → **About** instead. Same trick, seven presses.

## Step 4 — Turn on debugging

Go back one level. There is now a **Developer options** entry that wasn't there before.

Inside it, turn **on**:

- **USB debugging**
- **Network debugging** or **Wireless debugging**, if your TV lists one — the name varies by brand

That's all you need. Leave everything else in that menu alone; it's full of things that will make your TV behave strangely.

> **Is it safe to leave on?** Debugging lets any device on your home network ask your TV to run commands, but only after you approve that specific device by hand. If you share your Wi-Fi widely, turn it back off when you're done — the switch is in the same place.

## Step 5 — Install ADB on your laptop

Pick your system.

### macOS

If you have [Homebrew](https://brew.sh):

```bash
brew install android-platform-tools
```

If you don't, or you'd rather not install a package manager:

```bash
cd ~/Downloads
curl -O https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
unzip platform-tools-latest-darwin.zip
cd platform-tools
./adb version
```

With that second route, every `adb` command below has to be run from inside that `platform-tools` folder, or written as `./adb` instead of `adb`.

### Windows

1. Download **[SDK Platform-Tools for Windows](https://developer.android.com/tools/releases/platform-tools)** from Google.
2. Unzip it somewhere you'll find again — `C:\platform-tools` is a good choice.
3. Open **PowerShell** and go there:

```powershell
cd C:\platform-tools
.\adb version
```

To use `adb` from any folder, add `C:\platform-tools` to your PATH: press Start, type "environment variables", open **Edit the system environment variables** → **Environment Variables** → select **Path** → **Edit** → **New** → paste the folder → OK. Close and reopen PowerShell.

### Linux

```bash
sudo apt install adb          # Debian, Ubuntu, Mint
sudo dnf install android-tools # Fedora
sudo pacman -S android-tools   # Arch
adb version
```

**Whichever route you took, `adb version` should print something like `Android Debug Bridge version 1.0.41`.** If it says "command not found", the install didn't finish or the PATH isn't set.

## Step 6 — Connect

```bash
adb connect <TV-IP>:5555
```

You want:

```
connected to 192.168.1.2:5555
```

**Now look at your television.** A box appears: *"Allow USB debugging from this computer?"* with a long fingerprint underneath.

Tick **"Always allow from this computer"**, then choose **OK**.

Confirm it took:

```bash
adb devices
```

```
List of devices attached
192.168.1.2:5555	device
```

**`device` is what you want.** If it says `unauthorized`, the dialog on the TV hasn't been accepted yet — go and accept it. If it says `offline`, run `adb disconnect` then connect again.

---

## If your TV is on Android 11 or newer and asks for a pairing code

Newer sets replace the simple connect with a pairing step, on a random port. You'll know because Developer options has a **Wireless debugging** screen with its own **Pair device with pairing code** option.

1. On the TV, open **Wireless debugging** → **Pair device with pairing code**.
2. It shows a **six-digit code** and an address like `192.168.1.2:41234`. That port is *not* 5555 and changes every time.
3. On the laptop:

```bash
adb pair 192.168.1.2:41234
```

4. It asks for the code. Type the six digits.
5. Then connect using the *other* address shown on the Wireless debugging main screen (a different port again):

```bash
adb connect 192.168.1.2:37000
```

You only pair once. After that, `adb connect` is enough.

---

## Check it really works

```bash
adb shell getprop ro.product.model
```

If your TV's model name comes back, you're connected and everything else in this repo will work.

**Next: [clear out the factory clutter →](02-debloat.md)**

---

## Troubleshooting

| What you see | What it means |
|---|---|
| `unable to connect ... Connection refused` | Debugging isn't on, or the TV isn't listening yet. Re-check Step 4, then reboot the TV and try again. |
| `unable to connect ... No route to host` | Wrong IP, or the two devices are on different networks. Re-check Steps 1 and 2. |
| `device unauthorized` | The dialog on the TV hasn't been accepted. Look at the screen. |
| `device offline` | `adb disconnect` then `adb connect <TV-IP>:5555` again. |
| Nothing at all, hangs forever | Your laptop is probably on a guest or VPN network. Turn off the VPN and rejoin the main Wi-Fi. |
| Worked yesterday, not today | Some TVs close the port after a reboot. Open Developer options once and it starts listening again. |

More in **[troubleshooting](05-troubleshooting.md)**.
