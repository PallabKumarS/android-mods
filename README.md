# HyperOS / MIUI Always-On Display (AOD) Dimmer & Customization Suite

A comprehensive modification and LSPosed module designed to optimize and enhance the **Always-On Display (AOD)** experience on Xiaomi, Redmi, and POCO devices running **HyperOS 1.0 / 2.0 / 3.0** and **MIUI 14** (Android 14, 15, and 16).

---

## 🌟 Features

- 🌙 **Custom Post-Delay Dimming:** Override the stock fixed dimming level to make your AOD as dim and subtle as you want in dark environments.
- 🎛️ **Dual-Curve Nits Control:**
  - **Min Dim Brightness (Dark Room):** Target steady-state luminance when surroundings are dark (*Default: 2.0 nits, Stock: 5.0 nits*).
  - **Max Ambient Brightness (Lit Room):** Upper threshold cap for indoor room lighting so AOD never blinds you (*Default: 30 nits, Stock: 60 nits*).
  - **Outdoor Scalability:** Retains automatic scalability under direct sunlight so the clock remains clearly legible.
- ⚡ **Sensor Event Throttling:** Filters redundant sensor polling interrupts to eliminate brightness jitter and save battery during screen-off sleep.
- 🔄 **Live Configuration:** Real-time settings updates via the companion app UI without requiring device reboots.

---

## 📱 Compatibility & Supported Devices

- **OS / ROMs:**
  - Xiaomi HyperOS 3.0 (Android 16)
  - Xiaomi HyperOS 2.0 (Android 15)
  - Xiaomi HyperOS 1.0 (Android 14)
  - MIUI 14 (Android 13 / 14)
- **Tested Hardware:**
  - POCO X6 5G / Redmi Note 13 Pro 5G (`garnet` / `garnetp_in`)
  - Compatible with AMOLED/OLED Xiaomi devices featuring Always-On Display.

---

## 📦 Downloads & Components

| Asset | Description | Type |
| :--- | :--- | :--- |
| **`AodDimmer.apk`** | LSPosed module with in-app dual-curve sliders & sensor throttle. | APK (LSPosed Module) |
| **`hyperos-aon-aod.zip`** | Flashable systemless module for Always-On Display & AON enhancements. | KernelSU / Magisk Module |

---

## 🚀 Installation Guide

### Prerequisites
1. Device rooted with **KernelSU**, **KernelSU-Next**, **APatch**, or **Magisk**.
2. **LSPosed (Zygisk release)** installed and working.

### Step 1: Install the LSPosed Module
1. Download **`AodDimmer.apk`** from the [Releases](releases) section.
2. Install the APK on your device.
3. Open **LSPosed Manager** $\to$ go to **Modules** $\to$ tap **AOD Dimmer**.
4. Toggle the module **ON** and ensure the **System Framework (`android`)** scope is checked.
5. Perform a **Soft Reboot / Quick Reboot**.

### Step 2: Configure Your Dimming Preferences
1. Open the **AOD Dimmer** app from your app drawer.
2. Adjust your desired parameters:
   - **Min Dim Brightness:** Set between `0.5 nits` and `10.0 nits` (*Recommended: `1.5 - 2.5 nits`*).
   - **Max Ambient Brightness:** Set between `10 nits` and `100 nits` (*Recommended: `25 - 40 nits`*).
   - **Sensor Update Throttle:** Set debounce window between `0.5s` and `5.0s` (*Recommended: `2.0s`*).
3. Changes apply immediately to new AOD sessions!

---

## 🛠️ How It Works Internally

In HyperOS / MIUI, the framework service `com.android.server.display.DozeAutoBrightnessController` (inside `/system_ext/framework/miui-services.jar`) actively listens to ambient light sensor interrupts and continuously recalculates AOD brightness using hardware splines.

1. **Hardware Curve Mapping:** Instead of letting the sensor loop force the display back to stock 60 nits in lit rooms, `AodDimmer` dynamically maps the panel's hardware splines between your custom **Min Nits** and **Max Nits**.
2. **Interrupt Debouncing:** `AodDimmer` suppresses sub-20% lux fluctuations within your configured throttle window, preventing unnecessary wakeups of `DisplayPowerController` and saving battery.

---

## 📄 License & Credits

- Developed for the Xiaomi / HyperOS modding community.
- Built using Xposed API v82.
