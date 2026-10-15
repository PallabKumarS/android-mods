# Android System Modifications & Tweaks Suite

A collection of systemless modifications, KernelSU / Magisk modules, and LSPosed hooks tailored for Xiaomi devices (HyperOS / MIUI) and AOSP Custom ROMs (including Project Infinity-X, Axion, Evolution X, and LineageOS).

---

## Mod Modules Overview

| Component | Target System | Type | Primary Purpose |
| :--- | :--- | :--- | :--- |
| **`PlayIntegrity-TrickyStore-AIO.zip`** | HyperOS / MIUI & Clean AOSP | KernelSU / Magisk Module | **All-in-One Play Integrity Suite** (Combines Play Integrity Fork v18 and Tricky Store v1.4.1 with pre-installed Strong Keybox for 3-tier pass). |
| **`TrickyStore-StrongIntegrity.zip`** | Custom ROMs (Infinity-X, Evolution X) | KernelSU / Magisk Module | **Plug & Play Tricky Store** (Official Tricky Store v1.4.1 pre-configured with Strong Keybox and auto-detected security patch). |
| **`AodDimmer.apk`** | HyperOS & AOSP 14–17 | Standalone LSPosed Module | **Intelligent Brightness & Display Stabilizer** (AOD nits shaping, daytime sensor anti-flicker smoothing, and night white-bg anti-runaway cap). |
| **`aosp-no-keyboard-chin.zip`** | AOSP / Custom ROMs (Android 14–17) | KernelSU / Magisk Module & ADB | **Removes Keyboard Chin & Hide Button** (eliminates bottom gap and keyboard dismiss arrow). |
| **`hyperos-aon-aod.zip`** | HyperOS 1.0–3.0 / MIUI 14 | KernelSU / Magisk Module | **Unlocks true Always-On Display** (removes the 10-second limitation systemlessly in Settings). |

---

## Component 1: `PlayIntegrity-TrickyStore-AIO.zip` (All-in-One Play Integrity Suite)

### The Problem on Unlocked Bootloaders & Stock/Clean ROMs
On modern Android (especially HyperOS or clean AOSP without native spoofing), an unlocked bootloader causes Google Play Integrity to fail:
1. `MEETS_BASIC_INTEGRITY`: Fails due to exposed bootloader unlock flags.
2. `MEETS_DEVICE_INTEGRITY`: Fails because the ROM build fingerprint is uncertified or custom.
3. `MEETS_STRONG_INTEGRITY`: Fails because the hardware TEE / Keymint attestation reports an unlocked bootloader.

### How It Works Internally
This unified module combines two independent engines into a single flashable package:
- **Play Integrity Fork (v18):** Runs via Zygisk to hook `com.google.android.gms.unstable`, injecting certified build properties and masking sensitive bootloader properties (`ro.boot.verifiedbootstate green`, `ro.boot.flash.locked 1`, `ro.secureboot.lockstate locked`).
- **Tricky Store Engine (v1.4.1):** Operates via a userspace daemon injecting `libtricky_store.so` into the `keystore2` and `keymint` services to sign hardware attestation requests with a valid Google-certified attestation keybox.
- **Embedded Strong Keybox:** Pre-loaded with a verified Keybox certificate chain (`t.me/keyboxstrong @evokerr`), pre-configured `target.txt` (`com.google.android.gms`, `com.android.vending`), and auto-detected security patch level.

### Installation
1. Flash **`PlayIntegrity-TrickyStore-AIO.zip`** in KernelSU Next, Magisk, or APatch.
2. Reboot your device.
3. Verify all three integrity levels pass in Play Integrity Checker or TB Checker.

---

## Component 2: `TrickyStore-StrongIntegrity.zip` (Plug & Play Keystore Spoofer)

### Target Use Case
Feature-rich custom ROMs (such as Project Infinity-X, Evolution X, and CrDroid) already have Play Integrity Fork / property spoofing built into the system framework to pass Basic and Device integrity. Flashing a second Zygisk spoofer is redundant.

### What It Does
- Bundles official Tricky Store v1.4.1 with the verified Strong Integrity Keybox.
- The installer automatically creates `/data/adb/tricky_store/`, writes `keybox.xml`, configures `target.txt`, sets correct `0644` permissions, and auto-detects `ro.build.version.security_patch` to write `security_patch.txt`.
- Syncs a backup to `/sdcard/InfinityResources/keybox.xml`.
- Zero manual file management required across clean ROM flashes.

---

## Component 3: `AodDimmer.apk` (AOD Dimmer & Display Auto-Brightness Stabilizer)

### Key Features
- **AOSP 14–17 Display Auto-Brightness Stabilizer:**
  - **Daytime Anti-Flicker Smoothing:** Implements Exponential Moving Average (EMA) smoothing and upward rate-limiting on `AutomaticBrightnessController.handleLightSensorEvent` to eliminate sudden, distracting brightness jumps caused by shadows or sensor jitter.
  - **Night White-Background Anti-Runaway Cap:** When reading in bed or dark environments (ambient lux <= threshold), bright white backgrounds (e.g., web pages, comics, documents) reflect light back into the ambient light sensor, causing an escalating feedback loop that pushes brightness to maximum. The module detects dark ambient conditions and clamps maximum screen brightness to safe, comfortable nits.
- **Dual-Curve Always-On Display (AOD) Dimming:**
  - **Min Dim Brightness (Dark Room):** Set low-light level (*Default: 2.0 nits, Range: 0.5 – 10.0 nits*).
  - **Max Ambient Brightness (Lit Room):** Cap lit-room AOD brightness so the display won't jump to high nits (*Default: 30 nits, Range: 10 – 120 nits*).
  - **Outdoor Sunlight Scaling:** Scales smoothly under direct sun so notifications remain visible.
- **HyperOS & AOSP Architecture Support:**
  - AOSP 14–17 hooks into `com.android.server.display.AutomaticBrightnessController` and `DozeBrightnessStrategy` in `/system/framework/services.jar`.
  - HyperOS hooks into `com.android.server.display.DozeAutoBrightnessController` in `/system_ext/framework/miui-services.jar`.

### Installation
1. Install **`AodDimmer.apk`**.
2. In **LSPosed / Vector Manager**, enable the module and select the **System Framework (`android`)** scope.
3. Perform a **Soft Reboot** or full restart.
4. Launch the **AOD Dimmer** app to customize your brightness curves and anti-flicker parameters.

---

## Component 4: `aosp-no-keyboard-chin.zip` (AOSP Keyboard Chin & Down Arrow Remover)

### What It Does
Eliminates the dead navigation space and down-arrow spacer beneath soft keyboards on AOSP and custom ROMs with gesture navigation enabled.

### Internal Mechanism
Toggles `Settings.Secure.navbar_ime_space = 0`. The flashable module includes a boot-time service (`service.sh`) and an action button (`action.sh`) in KernelSU Manager.

### Quick ADB Method
```bash
# Disable keyboard chin & hide arrow
adb shell settings put secure navbar_ime_space 0

# Restore default chin & arrow
adb shell settings put secure navbar_ime_space 1
```

---

## Component 5: `hyperos-aon-aod.zip` (HyperOS AOD Feature-Gate Unlocker)

### What It Does
Systemlessly removes Xiaomi's 10-second AOD restriction on AMOLED devices running HyperOS 1.0–3.0 / MIUI 14.

### Internal Mechanism
Patches `/product/etc/device_features/<device_codename>.xml` systemlessly to set `is_only_support_keycode_goto=false`. Compatible with read-only EROFS partitions when paired with MetaModule.

---

## Hardware & Compatibility Matrix

- **Verified Device:** POCO X6 5G / Redmi Note 13 Pro 5G (`garnet` / `garnetp_in`)
- **Display Types:** AMOLED / OLED displays across Xiaomi, POCO, Redmi, Pixel, and OnePlus devices.
- **Supported Platforms:**
  - AOSP 14 / 15 / 16 / 17 (Project Infinity-X, Axion, LineageOS, Evolution X, PixelOS)
  - Xiaomi HyperOS 1.0 / 2.0 / 3.0
  - MIUI 14

---

## Downloads

All pre-built binaries and flashable packages are published under [Releases](https://github.com/PallabKumarS/android-mods/releases/latest):

| File | Target System | Description | Download |
| :--- | :--- | :--- | :--- |
| **`PlayIntegrity-TrickyStore-AIO.zip`** | HyperOS & Clean AOSP | All-in-one suite: Play Integrity Fork v18 + Tricky Store v1.4.1 + Strong Keybox. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/PlayIntegrity-TrickyStore-AIO.zip) |
| **`TrickyStore-StrongIntegrity.zip`** | Custom ROMs (Infinity-X, etc.) | Standalone Tricky Store v1.4.1 pre-loaded with Strong Keybox and auto security patch. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/TrickyStore-StrongIntegrity.zip) |
| **`AodDimmer.apk`** | HyperOS & AOSP 14–17 | LSPosed module companion app for AOD dimming and auto-brightness stabilizer. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/AodDimmer.apk) |
| **`aosp-no-keyboard-chin.zip`** | AOSP 14–17 | KernelSU / Magisk module to remove the keyboard chin spacer and dismiss button. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aosp-no-keyboard-chin.zip) |
| **`hyperos-aon-aod.zip`** | HyperOS 1/2/3 | KernelSU / Magisk module to unlock permanent Always-On Display. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/hyperos-aon-aod.zip) |

---

## License & Credits

- Developed for the Android and Xiaomi modding community.
- Built using Xposed / LSPosed API, Android Settings Provider API, KernelSU systemless architecture, and Tricky Store / Play Integrity Fork frameworks.
