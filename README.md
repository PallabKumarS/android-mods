# Android System Modifications & Tweaks Suite

A collection of systemless modifications, KernelSU / Magisk modules, and LSPosed hooks tailored for Xiaomi devices (HyperOS / MIUI) and AOSP Custom ROMs (including Project Infinity-X and Axion).

---

## Mod Modules Overview

| Component | Target System | Type | Primary Purpose |
| :--- | :--- | :--- | :--- |
| **`aosp-no-keyboard-chin.zip`** | AOSP / Custom ROMs (Android 14–17) | KernelSU / Magisk Module & ADB | **Removes Keyboard Chin & Hide Button** (eliminates bottom gap and keyboard dismiss arrow). |
| **`hyperos-aon-aod.zip`** | HyperOS 1.0–3.0 / MIUI 14 | KernelSU / Magisk Module | **Unlocks true Always-On Display** (removes the 10-second limitation systemlessly in Settings). |
| **`AodDimmer.apk`** | HyperOS 1.0–3.0 / MIUI 14 | Standalone LSPosed Module | **Intelligent Brightness & Dimmer Control** (custom dual-curve Min/Max nits, sensitivity threshold, and sensor throttling). |

---

## ⌨️ Component 1: `aosp-no-keyboard-chin.zip` (AOSP Keyboard Chin & Down Arrow Remover)

### The Problem in AOSP & Custom ROMs
On modern AOSP and custom ROMs (such as Project Infinity-X, Axion, LineageOS, and Pixel-based ROMs) with gesture navigation enabled:
1. When the soft keyboard (Gboard, etc.) appears, Android reserves an extra bottom navigation spacer (chin) beneath the keyboard.
2. This chin displays a down-arrow (keyboard dismiss button) on the left and an input method switcher icon on the right.
3. This creates unnecessary dead space, increasing the keyboard height and making typing less ergonomic on tall modern aspect ratios.
4. Many custom ROM builds contain the underlying framework code but omit the toggle from the Settings UI.

### How It Works Internally
The bottom keyboard spacer is controlled at the OS level by:
```text
Settings.Secure.navbar_ime_space
```
* **`navbar_ime_space = 1` (Default):** Adds the bottom padding inset with the dismiss arrow and IME switcher button.
* **`navbar_ime_space = 0` (Disabled):** Completely collapses the IME navigation inset. The keyboard sits flush against the bottom edge of the display, and the dropdown arrow is eliminated.

`SystemUI` and `Launcher3QuickStep` register a `ContentObserver` on this URI, updating the layout dynamically without requiring a system restart.

### Quick ADB Method (No Root Required)
To apply immediately via ADB:
```bash
# Disable keyboard chin & hide arrow
adb shell settings put secure navbar_ime_space 0

# Restore default chin & arrow (if needed)
adb shell settings put secure navbar_ime_space 1
```

### KernelSU / Magisk Module Installation (Persistent)
The module ensures the setting is automatically re-enforced on every boot (even after ROM updates, settings resets, or cache wipes):
1. Flash **`aosp-no-keyboard-chin.zip`** in KernelSU Next, KernelSU, Magisk, or APatch.
2. The included `service.sh` enforces `navbar_ime_space=0` upon `sys.boot_completed=1`.
3. An `action.sh` script is provided to allow toggling the chin directly from the KernelSU Manager action button.

---

## 📦 Component 2: `hyperos-aon-aod.zip` (HyperOS AOD Feature-Gate Unlocker)

### What It Does
This flashable module removes Xiaomi's artificial 10-second limitation systemlessly, unlocking the native **"Always"** and **"Scheduled"** display items in **Settings > Always-on display & Lock screen**.

### How It Works Internally
Xiaomi controls AOD feature gates in `/product/etc/device_features/<device_codename>.xml` via the flag:
```xml
<bool name="is_only_support_keycode_goto">true</bool>
```
When `is_only_support_keycode_goto` is set to `true`, HyperOS forces the 10-second restriction.

1. **Dynamic Codename Detection:** Reads `ro.product.device` at boot (e.g., `garnet` for POCO X6 5G) without hardcoding device XMLs.
2. **Systemless Modification:** Creates a patched copy in memory with `is_only_support_keycode_goto=false`.
3. **SELinux & EROFS Compliant:** Restores exact SELinux contexts (`chcon --reference`) and executes an early `mount -o bind` during `post-fs-data.sh` (powered by MetaModule).
4. **`skip_mount` Integration:** Bypasses rigid partition overlays, ensuring 100% compatibility with read-only EROFS and dynamic partitions.

### Requirement: MetaModule for KernelSU
> [!IMPORTANT]
> **KernelSU users running HyperOS MUST install [MetaModule](https://github.com/KernelSU/MetaModule) (or an OverlayFS provider module).**
> 
> Modern Xiaomi devices running HyperOS format `/product` and `/system_ext` as read-only **EROFS partitions**. Standard KernelSU only manages `$MODPATH/system` by default and cannot modify `/product` directly. 
> 
> **MetaModule** provides the necessary OverlayFS and bind-mount environment during early boot (`post-fs-data.sh`), enabling `hyperos-aon-aod.zip` to successfully bind-mount the modified XML over `/product/etc/device_features/<device>.xml`.

### Installation
1. Ensure **MetaModule** is installed and active in **KernelSU** (or use Magisk / APatch).
2. Flash **`hyperos-aon-aod.zip`** in your root manager.
3. Reboot your device.
4. Go to **Settings > Always-on display & Lock screen > Display items** and select **"Always"** or **"Scheduled"**.

---

## 📦 Component 3: `AodDimmer.apk` (LSPosed Brightness Controller)

### What It Does
Once your AOD stays on permanently, `AodDimmer` ensures it stays at the perfect luminance level—subtle in the dark, capped in indoor light, legible under the sun, and battery-friendly.

### Key Features
- **Custom Post-Delay Dimming:** Control how dark the screen gets when AOD transitions into its steady state in dark environments.
- **Dual-Curve Nits Control:**
  - **Min Dim Brightness (Dark Room):** Set your preferred low-light level (*Default: 2.0 nits, Range: 0.5 – 10.0 nits, Stock was 5.0 nits*).
  - **Max Ambient Brightness (Lit Room):** Cap lit-room AOD brightness so HyperOS won't jump back to stock 60 nits (*Default: 30 nits, Range: 10 – 120 nits*).
  - **Outdoor Sunlight Scaling:** Retains automatic scalability under intense sunlight so clock and notifications stay legible.
- **Lux Change Sensitivity Threshold:** Choose the exact percentage shift in ambient light (`5%` to `50%`, default `20%`) required to trigger a brightness change. Prevents brightness jitter from minor shadows.
- **Sensor Event Throttling:** Configurable debounce timer (`0.5s` to `5.0s`, default `2.0s`) to suppress redundant sensor polling interrupts and save screen-off battery.
- **Live In-App Configuration:** Adjust settings on the fly from the companion app UI without requiring device reboots.

### How It Works Internally
`AodDimmer` hooks into `system_server` (`com.android.server.display.DozeAutoBrightnessController` inside `/system_ext/framework/miui-services.jar`):
1. **Dynamic Hardware Curve Remapping:** Intercepts `updateAutoBrightness()` and maps the panel's hardware calibration spline smoothly between your custom **Min Nits** and **Max Nits**.
2. **Interrupt Debouncing:** Suppresses redundant light sensor callbacks in `handleLightSensorEvent` when the lux change is below your configured sensitivity threshold.

### Installation
1. Install **`AodDimmer.apk`**.
2. Open **LSPosed / Vector Manager** $\to$ **Modules** $\to$ select **AOD Dimmer**.
3. Toggle the module **ON** and ensure the **System Framework (`android`)** scope is checked.
4. Perform a **Soft Reboot / Quick Reboot**.
5. Open the **AOD Dimmer** app to customize your sliders.

---

## 📱 Hardware & Compatibility Matrix

- **Verified Hardware:**
  - POCO X6 5G / Redmi Note 13 Pro 5G (`garnet` / `garnetp_in`)
  - Compatible with AMOLED/OLED Xiaomi, POCO, and Redmi devices.
- **Supported ROMs:**
  - AOSP 14 / 15 / 16 / 17 (Project Infinity-X, Axion, LineageOS, PixelOS)
  - Xiaomi HyperOS 1.0 / 2.0 / 3.0
  - MIUI 14

---

## 📥 Downloads

All binaries are available in the [Releases](https://github.com/PallabKumarS/android-mods/releases/latest) tab:

| File | Target ROM | Description | Download |
| :--- | :--- | :--- | :--- |
| **`aosp-no-keyboard-chin.zip`** | AOSP 14–17 | KernelSU / Magisk / APatch module to remove keyboard chin and dismiss button. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aosp-no-keyboard-chin.zip) |
| **`hyperos-aon-aod.zip`** | HyperOS 1/2/3 | KernelSU / Magisk / APatch module to unlock true Always-On Display. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/hyperos-aon-aod.zip) |
| **`AodDimmer.apk`** | HyperOS 1/2/3 | Standalone LSPosed module companion app for custom AOD brightness dimming. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/AodDimmer.apk) |

---

## 📄 License & Credits

- Developed for the Android & Xiaomi modding community.
- Built using Xposed API v82, Android Settings Provider API, and KernelSU systemless architecture.
