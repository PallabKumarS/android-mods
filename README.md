# HyperOS / MIUI Always-On Display (AOD) Suite

A complete, dual-component modification suite designed to unlock **true Always-On Display** and provide **intelligent, customizable brightness dimming** on Xiaomi, Redmi, and POCO devices running **HyperOS 1.0 / 2.0 / 3.0** and **MIUI 14** (Android 14, 15, and 16).

---

## 📖 The Problem on Xiaomi & HyperOS

Xiaomi devices face two major limitations with Always-On Display:

1. **The 10-Second AOD Timeout Lock:**
   On non-flagship devices (e.g., POCO X-series, Redmi Note-series), Xiaomi restricts the Always-On Display to *"For 10 seconds after tapping"*, hiding the *"Always"* and *"Scheduled"* options in Settings.
2. **Aggressive / Blinding AOD Brightness & Sensor Overrides:**
   Even when AOD is active, HyperOS uses an ambient light sensor loop that frequently pushes the screen back up to **60 nits** in ordinary room light, making it too bright in dim rooms and draining battery with continuous sensor polling.

This suite provides two specialized, complementary tools to solve both problems:

| Component | Type | Primary Purpose |
| :--- | :--- | :--- |
| **`hyperos-aon-aod.zip`** | KernelSU / Magisk Module | **Unlocks true Always-On Display** (removes the 10-second limitation systemlessly in Settings). |
| **`AodDimmer.apk`** | Standalone LSPosed Module | **Intelligent Brightness & Dimmer Control** (custom dual-curve Min/Max nits, sensitivity threshold, and sensor throttling). |

---

## ⚠️ Important Requirement: MetaModule for KernelSU

> [!IMPORTANT]
> **KernelSU users MUST install [MetaModule](https://github.com/KernelSU/MetaModule) (or an OverlayFS provider module).**
> 
> **Why is MetaModule required?**
> Modern Xiaomi devices running HyperOS format `/product` and `/system_ext` as read-only **EROFS partitions**. Standard KernelSU only manages `$MODPATH/system` by default and cannot modify `/product` directly. 
> 
> **MetaModule** provides the necessary OverlayFS and bind-mount environment during early boot (`post-fs-data.sh`), enabling `hyperos-aon-aod.zip` to successfully bind-mount the modified XML over `/product/etc/device_features/<device>.xml`. Without MetaModule, the feature-gate override will not take effect on KernelSU.

---

## 📦 Component 1: `hyperos-aon-aod.zip` (Feature-Gate Unlocker)

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

### Installation
1. Ensure **MetaModule** is installed and active in **KernelSU** (or use Magisk / APatch).
2. Flash **`hyperos-aon-aod.zip`** in your root manager.
3. Reboot your device.
4. Go to **Settings > Always-on display & Lock screen > Display items** and select **"Always"** or **"Scheduled"**.

---

## 📦 Component 2: `AodDimmer.apk` (LSPosed Brightness Controller)

### What It Does
Once your AOD stays on permanently, `AodDimmer` ensures it stays at the perfect luminance level—subtle in the dark, capped in indoor light, legible under the sun, and battery-friendly.

### Key Features
- 🌙 **Custom Post-Delay Dimming:** Control how dark the screen gets when AOD transitions into its steady state in dark environments.
- 🎛️ **Dual-Curve Nits Control:**
  - **Min Dim Brightness (Dark Room):** Set your preferred low-light level (*Default: 2.0 nits, Range: 0.5 – 10.0 nits, Stock was 5.0 nits*).
  - **Max Ambient Brightness (Lit Room):** Cap lit-room AOD brightness so HyperOS won't jump back to stock 60 nits (*Default: 30 nits, Range: 10 – 120 nits*).
  - **Outdoor Sunlight Scaling:** Retains automatic scalability under intense sunlight so clock and notifications stay legible.
- 🎚️ **Lux Change Sensitivity Threshold:** Choose the exact percentage shift in ambient light (`5%` to `50%`, default `20%`) required to trigger a brightness change. Prevents brightness jitter from minor shadows.
- ⚡ **Sensor Event Throttling:** Configurable debounce timer (`0.5s` to `5.0s`, default `2.0s`) to suppress redundant sensor polling interrupts and save screen-off battery.
- 🔄 **Live In-App Configuration:** Adjust settings on the fly from the companion app UI without requiring device reboots.

### How It Works Internally
`AodDimmer` hooks into `system_server` (`com.android.server.display.DozeAutoBrightnessController` inside `/system_ext/framework/miui-services.jar`):
1. **Dynamic Hardware Curve Remapping:** Intercepts `updateAutoBrightness()` and maps the panel's hardware calibration spline smoothly between your custom **Min Nits** and **Max Nits**.
2. **Interrupt Debouncing:** Suppresses redundant light sensor callbacks in `handleLightSensorEvent` when the lux change is below your configured sensitivity threshold.

### Installation
1. Install **`AodDimmer.apk`**.
2. Open **LSPosed Manager** $\to$ **Modules** $\to$ select **AOD Dimmer**.
3. Toggle the module **ON** and ensure the **System Framework (`android`)** scope is checked.
4. Perform a **Soft Reboot / Quick Reboot**.
5. Open the **AOD Dimmer** app to customize your sliders.

---

## 📱 Compatibility Matrix

- **Supported OS / ROMs:**
  - Xiaomi HyperOS 3.0 (Android 16)
  - Xiaomi HyperOS 2.0 (Android 15)
  - Xiaomi HyperOS 1.0 (Android 14)
  - MIUI 14 (Android 13 / 14)
- **Verified Hardware:**
  - POCO X6 5G / Redmi Note 13 Pro 5G (`garnet` / `garnetp_in`)
  - Compatible with all AMOLED/OLED Xiaomi, POCO, and Redmi devices.

---

## 📥 Downloads

All binaries are available in the [Releases](https://github.com/PallabKumarS/hyperos-aod-dimmer/releases/latest) tab:

| File | Description | Download |
| :--- | :--- | :--- |
| **`AodDimmer.apk`** | LSPosed module app with companion UI. | [Download APK](https://github.com/PallabKumarS/hyperos-aod-dimmer/releases/latest/download/AodDimmer.apk) |
| **`hyperos-aon-aod.zip`** | KernelSU / Magisk / APatch module to unlock true Always-On Display. | [Download ZIP](https://github.com/PallabKumarS/hyperos-aod-dimmer/releases/latest/download/hyperos-aon-aod.zip) |

---

## 🚀 Complete Step-by-Step Setup Guide

For the best Always-On Display experience:

```mermaid
graph LR
    A["1. Install MetaModule in KernelSU"] --> B["2. Flash hyperos-aon-aod.zip & Reboot"]
    B --> C["3. Enable 'Always' in Display Settings"]
    C --> D["4. Install AodDimmer.apk"]
    D --> E["5. Enable in LSPosed (android scope) & Soft Reboot"]
    E --> F["6. Customize Sliders in AOD Dimmer App"]
```

1. **Install MetaModule:** In KernelSU, flash **MetaModule** (OverlayFS provider) and reboot.
2. **Unlock True AOD:** Flash **`hyperos-aon-aod.zip`** in KernelSU / Magisk and reboot.
3. **Enable Always-On Mode:** Go to **Settings > Always-on display & Lock screen > Display items** and choose **"Always"**.
4. **Install Dimmer Hook:** Install **`AodDimmer.apk`**.
5. **Activate LSPosed:** Open **LSPosed**, activate **AOD Dimmer** with the **System Framework (`android`)** scope, and soft reboot.
6. **Tune Preferences:** Open the **AOD Dimmer** app and set your desired **Min Nits** (e.g. 2.0 nits), **Max Nits** (e.g. 30 nits), **Lux Threshold** (e.g. 20%), and **Sensor Throttle** (e.g. 2.0s).

---

## 📄 License & Credits

- Developed for the Xiaomi / HyperOS modding community.
- Built using Xposed API v82 and KernelSU MetaModule bind-mount architecture.
