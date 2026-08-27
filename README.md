# HyperOS / MIUI Always-On Display (AOD) Dimmer & Customization Suite

A lightweight, powerful LSPosed module designed to optimize and enhance the **Always-On Display (AOD)** experience on Xiaomi, Redmi, and POCO devices running **HyperOS 1.0 / 2.0 / 3.0** and **MIUI 14** (Android 14, 15, and 16).

---

## 🌟 Features

- 🌙 **Custom Post-Delay Dimming:** Override the stock fixed dimming level to make your AOD as dim and subtle as you want in dark environments.
- 🎛️ **Dual-Curve Nits Control:**
  - **Min Dim Brightness (Dark Room):** Target steady-state luminance when surroundings are dark (*Default: 2.0 nits, Stock: 5.0 nits*).
  - **Max Ambient Brightness (Lit Room):** Upper threshold cap for indoor room lighting so AOD never blasts at stock 60 nits (*Default: 30 nits, Stock: 60 nits*).
  - **Outdoor Scalability:** Retains automatic scalability under direct sunlight so the clock remains clearly legible.
- 🎚️ **Lux Change Sensitivity Threshold:** Choose the exact percentage shift in ambient light (`5%` to `50%`) required to trigger a brightness change.
- ⚡ **Sensor Event Throttling:** Configurable debounce timer (`0.5s` to `5.0s`) to filter redundant sensor polling interrupts, eliminate brightness jitter, and save battery.
- 🔄 **Live In-App Configuration:** Adjust settings in real-time via the companion app UI without requiring device reboots.

---

## 📱 Compatibility & Supported Devices

- **OS / ROMs:**
  - Xiaomi HyperOS 3.0 (Android 16)
  - Xiaomi HyperOS 2.0 (Android 15)
  - Xiaomi HyperOS 1.0 (Android 14)
  - MIUI 14 (Android 13 / 14)
- **Tested Hardware:**
  - POCO X6 5G / Redmi Note 13 Pro 5G (`garnet` / `garnetp_in`)
  - Compatible with all AMOLED/OLED Xiaomi devices featuring Always-On Display.

---

## 📦 Downloads

| Asset | Description | Type |
| :--- | :--- | :--- |
| **[`AodDimmer.apk`](https://github.com/PallabKumarS/hyperos-aod-dimmer/releases/latest/download/AodDimmer.apk)** | Standalone LSPosed module with companion UI (Recommended). | APK |
| **[`hyperos-aon-aod.zip`](https://github.com/PallabKumarS/hyperos-aod-dimmer/releases/latest/download/hyperos-aon-aod.zip)** | Flashable KernelSU / Magisk module for HyperOS Always-On Display & AON enhancements. | KSU / Magisk ZIP |

---

## 🚀 Installation & Setup

### Prerequisites
1. Device rooted with **KernelSU**, **KernelSU-Next**, **APatch**, or **Magisk**.
2. **LSPosed (Zygisk release)** installed and active.

### Instructions
1. Download and install **`AodDimmer.apk`** from [Releases](https://github.com/PallabKumarS/hyperos-aod-dimmer/releases/latest).
2. Open **LSPosed Manager** $\to$ **Modules** $\to$ select **AOD Dimmer**.
3. Toggle the module **ON** and verify the **System Framework (`android`)** scope is enabled.
4. Perform a **Soft Reboot / Quick Reboot**.
5. Open the **AOD Dimmer** app to customize your preferred **Min Nits**, **Max Nits**, **Lux Sensitivity Threshold**, and **Sensor Throttle**.

---

## 🛠️ How It Works Internally

In HyperOS / MIUI, the system server class `com.android.server.display.DozeAutoBrightnessController` listens to ambient light sensor interrupts and continuously recalculates AOD brightness using hardware splines.

1. **Hardware Curve Remapping:** Instead of letting the sensor loop force the display back to 60 nits in lit rooms, `AodDimmer` dynamically maps the panel's hardware splines between your custom **Min Nits** and **Max Nits**.
2. **Interrupt Debouncing:** `AodDimmer` suppresses sub-threshold lux fluctuations within your configured throttle window, preventing unnecessary wakeups of `DisplayPowerController` and reducing screen-off battery consumption.

---

## 📄 License & Credits

- Developed for the Xiaomi / HyperOS modding community.
- Built with Xposed API v82.
