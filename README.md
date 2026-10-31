# Android System Modifications & Tweaks Suite

A collection of systemless modifications, KernelSU / Magisk modules, and LSPosed hooks tailored for Xiaomi devices (HyperOS / MIUI) and AOSP Custom ROMs (including Project Infinity-X, Axion, Evolution X, and LineageOS).

---

## Mod Modules Overview

| Component | Target System | Type | Primary Purpose |
| :--- | :--- | :--- | :--- |
| **`IntuneBypass.apk`** | All Rooted Android (10–17) | Standalone LSPosed / Vector Module | **Microsoft Intune & MAM Compliance Bypass** (Dynamic runtime bypass for Company Portal, Teams, Outlook, and Authenticator without modifying APKs). |
| **`PasskeyEnabler.apk`** | HyperOS 1.0–3.0 & Android 14–17 | Standalone LSPosed / Vector Module + Boot Service | **HyperOS Passkey & Third-Party Credential Provider Enabler** (Unlocks Bitwarden, 1Password, Proton Pass as system passkey providers, unmasks stripped AOSP Credential Provider settings, and forces Chrome 3rd-party passkey routing). |
| **`PlayIntegrity-TrickyStore-AIO.zip`** | HyperOS / MIUI & Clean AOSP | KernelSU / Magisk Module | **All-in-One Play Integrity Suite** (Combines Play Integrity Fork v18 and Tricky Store v1.4.1 with pre-installed Strong Keybox for 3-tier pass). |
| **`TrickyStore-StrongIntegrity.zip`** | Custom ROMs (Infinity-X, Evolution X) | KernelSU / Magisk Module | **Plug & Play Tricky Store** (Official Tricky Store v1.4.1 pre-configured with Strong Keybox and auto-detected security patch). |
| **`AodDimmer.apk`** | HyperOS & AOSP 14–17 | Standalone LSPosed Module | **Intelligent Brightness & Display Stabilizer** (AOD nits shaping, daytime sensor anti-flicker smoothing, and night white-bg anti-runaway cap). |
| **`aosp-no-keyboard-chin.zip`** | AOSP / Custom ROMs (Android 14–17) | KernelSU / Magisk Module & ADB | **Removes Keyboard Chin & Hide Button** (eliminates bottom gap and keyboard dismiss arrow). |
| **`hyperos-aon-aod.zip`** | HyperOS 1.0–3.0 / MIUI 14 | KernelSU / Magisk Module | **Unlocks true Always-On Display** (removes the 10-second limitation systemlessly in Settings). |
| **`aod-power-optimizer.zip`** | All Rooted Android (10–17) | KernelSU / APatch / Magisk Module with WebUI | **AOD & Deep Sleep Power Optimizer** (Reduces AOD battery drain from ~10%/hr to ~2.5%/hr via low-bit mode, proximity screen-off, dynamic refresh scaling, and Qualcomm Wi-Fi sleep abort fixes). |

---

## Component 1: `IntuneBypass.apk` (Microsoft Intune & MAM Compliance Bypass)

### What It Does
Enables Microsoft Intune Company Portal and Intune-protected work apps (Microsoft Teams, Microsoft Outlook, Microsoft Authenticator, Edge, OneDrive, Office) to pass App Protection Policy (MAM) and Device Health compliance checks on rooted Android devices **without touching or modifying the target APKs**.

### How It Works Internally
- **Multi-ClassLoader Dynamic Hooking:** Intercepts `BaseDexClassLoader` construction and `ContextWrapper.createPackageContext` so hooks attach automatically to both the host application's secondary dynamic DEX loaders and the remote `com.microsoft.windowsintune.companyportal` MAM plugin context—without disturbing `@CallerSensitive` `Class.forName` stack frames.
- **MAM IPC & Policy Endpoint Overrides:** Hooks `DeviceOriginContentProvider.call` (`Origin = 0`), `InstrumentationCheck.isStepDebugged`, `AbstractAppPolicyEndpoint`, `MDMAppPolicyEndpoint`, `AppPolicyEndpointProxy`, and `AppPolicyEndpointWrapper` with exact Dalvik branch polarities (`checkOrigin = false`, `getDeviceOrigin = 0`, `shouldPerformInHouseOriginCheck = false`, `shouldPerformSafetyNetCheck = false`, `getIsDeviceRooted = false`, `isDeviceRooted = false`, `getOfflineRootDefenseLevel = 0`).
- **Compliance Verdict & UI Gate:** Overrides `PolicyChecker`, `ComplianceVerdictManagerImpl` (`ComplianceVerdict.COMPLIANT`), and `ConditionalLaunchGetAccessFragment` (`ConditionalLaunchAction.NONE` + `ComplianceStatus.COMPLIANCE_SUCCESS` + `areAllPolicyBucketsCompliant = true`).
- **DexGuard RASP & Xposed Detector Neutralization:** Neutralizes in-house origin bitmask aggregators (`o.accessgetEditLockp`, `o.safeValue`, `o.UiExecutor`, `o.putSync`, `o.pushTrace`), Xposed stack/class detectors (`o.TransportBackend`, `o.JsonIOException`), and DexGuard RASP callbacks (`o.FirebaseNetworkException`).

### What You Need (Prerequisites)
- **Root Manager:** KernelSU / KernelSU Next, Magisk, or APatch (keep **Unmount modules** enabled for target Microsoft apps in KernelSU Manager).
- **Xposed Framework:** **Vector (LSPosed)** or **LSPosed** (Zygisk).
- **HMA (Hide My Applist) is NOT required:** Because `IntuneBypass.apk` intercepts the origin checks, RASP callbacks, and policy verdicts directly in memory, you do not need HMA for Teams, Outlook, or Company Portal.
- **Play Integrity (Optional but Recommended):** Keep `PlayIntegrity-TrickyStore-AIO.zip` (or `TrickyStore-StrongIntegrity.zip`) active if your organization also requires full MDM Device Enrollment via Google Play Services.

### Installation & Scope Configuration

#### Method A: Via LSPosed / Vector Manager UI
1. Download and install **`IntuneBypass.apk`**.
2. Open **Vector / LSPosed Manager** -> **Modules** -> enable **Intune & MAM Compliance Bypass**.
3. Check the following apps in the module scope:
   - **Intune Company Portal** (`com.microsoft.windowsintune.companyportal`) — **Required** (hosts the MAM policy engine)
   - **Microsoft Teams** (`com.microsoft.teams`)
   - **Microsoft Outlook** (`com.microsoft.office.outlook`)
   - **Microsoft Authenticator** (`com.azure.authenticator`)
   - *(Plus any other Intune-protected Microsoft work app you use)*
4. Force-stop **Company Portal** and **Teams** (do **not** clear app data), then open **Teams**.
5. On the **Get Access** screen, tap **CONTINUE** to proceed directly into your workspace.

#### Method B: Via ADB / Root CLI (Vector v2+)
```bash
# Install module
adb install -r IntuneBypass.apk

# Enable module and configure scope via Vector CLI
adb shell "su -c '/data/adb/lspd/cli modules enable com.n3th3r.intunebypass && /data/adb/lspd/cli scope set com.n3th3r.intunebypass com.microsoft.windowsintune.companyportal/0 com.microsoft.teams/0 com.microsoft.office.outlook/0 com.azure.authenticator/0 && am force-stop com.microsoft.windowsintune.companyportal && am force-stop com.microsoft.teams'"
```

---

## Component 2: `PlayIntegrity-TrickyStore-AIO.zip` (All-in-One Play Integrity Suite)

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

## Component 3: `TrickyStore-StrongIntegrity.zip` (Plug & Play Keystore Spoofer)

### Target Use Case
Feature-rich custom ROMs (such as Project Infinity-X, Evolution X, and CrDroid) already have Play Integrity Fork / property spoofing built into the system framework to pass Basic and Device integrity. Flashing a second Zygisk spoofer is redundant.

### What It Does
- Bundles official Tricky Store v1.4.1 with the verified Strong Integrity Keybox.
- The installer automatically creates `/data/adb/tricky_store/`, writes `keybox.xml`, configures `target.txt`, sets correct `0644` permissions, and auto-detects `ro.build.version.security_patch` to write `security_patch.txt`.
- Syncs a backup to `/sdcard/InfinityResources/keybox.xml`.
- Zero manual file management required across clean ROM flashes.

---

## Component 4: `AodDimmer.apk` (AOD Dimmer & Display Auto-Brightness Stabilizer)

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

## Component 5: `aosp-no-keyboard-chin.zip` (AOSP Keyboard Chin & Down Arrow Remover)

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

## Component 6: `hyperos-aon-aod.zip` (HyperOS AOD Feature-Gate Unlocker)

### What It Does
Systemlessly removes Xiaomi's 10-second AOD restriction on AMOLED devices running HyperOS 1.0–3.0 / MIUI 14.

### Internal Mechanism
Patches `/product/etc/device_features/<device_codename>.xml` systemlessly to set `is_only_support_keycode_goto=false`. Compatible with read-only EROFS partitions when paired with MetaModule.

---

## Component 7: `PasskeyEnabler.apk` (HyperOS Passkey & Third-Party Credential Provider Enabler)

### The Problem on HyperOS & MIUI
On Xiaomi devices running HyperOS (1.0–3.0) or MIUI:
1. **Google Lock-in:** Xiaomi hardcoded Google Password Manager as the sole passkey provider in framework overlays (`config_primaryCredentialProviderService`) and `CredentialManagerService`.
2. **Stripped Settings UI:** Xiaomi stripped `<action android:name="android.settings.CREDENTIAL_PROVIDER" />` from `Settings.apk`. As a result, users cannot switch passkey providers via the Settings UI, and any third-party app (such as Bitwarden or 1Password) attempting to open the system passkey picker crashes immediately with `ActivityNotFoundException`.
3. **Xiaomi Credential Manager Suppression:** In `MiuiCredentialManager.apk`, app-preferred defaults (which browsers set to Google) override third-party credential providers during passkey registration.
4. **Chrome FIDO Hijacking:** Google Chrome sets `Chrome.AutofillThirdPartyMode.State = false`, forcing Chrome to bypass Android's CredentialManager entirely and route WebAuthn directly to Google Play Services.

### How It Works Internally
- **SystemServer Framework Hooks (`services.jar`):**
  - Hooks `CredentialManagerService.getPrimaryProvidersForUserId` and `CredentialManagerServiceStub.getEnabledProvidersForUser` to prioritize Bitwarden (`com.x8bit.bitwarden`) and third-party managers as primary providers.
  - Hooks `ActivityTaskManagerService` to intercept `android.settings.CREDENTIAL_PROVIDER` intents and redirect them directly to `com.android.settings.applications.credentials.CredentialsPickerActivity` with `:settings:show_fragment = com.android.settings.applications.credentials.DefaultCombinedPicker`.
- **Credential Manager UI Hooks (`MiuiCredentialManager.apk`):**
  - Hooks `CreateFlowUtils$Companion.toCreateCredentialUiState` to enforce Bitwarden as `appPreferredDefaultProviderId` and register Bitwarden in `userSetDefaultProviderIds`.
  - Hooks `toActiveEntry` to guarantee that the single-provider confirmation sheet selects Bitwarden as the `activeEntry`, suppressing browser preference for Google.
  - Hooks `RequestDisplayInfo.getAppPreferredDefaultProviderId` to neutralize third-party apps requesting Google as the preferred default.
- **Settings Activity Unmasking (`Settings.apk`):**
  - Hooks `CredentialsPickerActivity.onCreate` to guarantee the AOSP Default Combined Picker fragment is displayed, allowing manual provider selection.
- **Chrome Third-Party Passkey Mode Hook (`com.android.chrome`):**
  - Hooks `SharedPreferencesImpl.getBoolean("Chrome.AutofillThirdPartyMode.State")` to return `true`, forcing Chrome to delegate passkeys to Android CredentialManager.
- **Persistent Boot Service:**
  - Installs a boot service to `/data/adb/service.d/passkey_enabler.sh` that restores `Settings.Secure.credential_service_primary`, `credential_service`, and `autofill_service` on cold boot.

### Installation & Scope Configuration

#### Method A: Via LSPosed / Vector Manager UI
1. Install **`PasskeyEnabler.apk`**.
2. Open **Vector / LSPosed Manager** -> **Modules** -> enable **HyperOS Passkey Enabler**.
3. Check the following 4 targets in the module scope:
   - **System Framework** (`android`)
   - **Settings** (`com.android.settings`)
   - **Google Chrome** (`com.android.chrome`)
   - **Credential Manager** (`com.android.credentialmanager`)
4. Open the **Passkey Enabler** companion app and tap **Apply Bitwarden Settings**.

#### Method B: Via ADB / Root CLI
```bash
# Install module
adb install -r PasskeyEnabler.apk

# Enable module and set 4-app scope in Vector CLI
adb shell "su -c '/data/adb/lspd/cli modules enable com.n3th3r.passkeyenabler && /data/adb/lspd/cli scope set com.n3th3r.passkeyenabler android com.android.settings com.android.chrome com.android.credentialmanager'"

# Install persistent boot service
adb push PasskeyEnabler/passkey_enabler.sh /data/local/tmp/
adb shell "su -c 'cp /data/local/tmp/passkey_enabler.sh /data/adb/service.d/passkey_enabler.sh && chmod 755 /data/adb/service.d/passkey_enabler.sh && sh /data/adb/service.d/passkey_enabler.sh'"
```

---

## Component 8: `aod-power-optimizer.zip` (AOD & Deep Sleep Power Optimizer with KernelSU WebUI)

### The Problem on AOSP & Custom ROMs
Users migrating from stock manufacturer firmware (such as Xiaomi HyperOS) to AOSP or custom ROMs (Project Infinity-X, Evolution X, LineageOS, CrDroid) often observe dramatic Always-On Display (AOD) and standby battery drain—frequently losing **8% to 12% per hour** while idle on AOD, compared to **2% to 3% per hour** on stock ROMs.

### Root Causes Diagnosed
1. **Unconstrained Framebuffer Power in Doze:** AOSP defaults to full 24-bit RGB rendering during Ambient Display. The display controller and RAM memory bus stay in high-power state to refresh pixels that only display monochromatic clock and notification text.
2. **Missing Proximity Screen-Off (Pocket Mode):** Stock ROMs aggressively kill the display when the proximity sensor detects a pocket or face-down placement on a desk (`doze_proximity_sensor_enabled = 0` by default on many custom ROMs).
3. **Rigid Minimum Refresh Rate:** Custom ROM display HALs frequently pin `min_refresh_rate` to 120Hz or 144Hz even during Doze/idle states, forcing panel hardware to run at maximum refresh rate while displaying static AOD elements.
4. **Qualcomm Wi-Fi Driver Sleep Aborts:** The Qualcomm Wi-Fi driver (`22800000.qcom,icnss`) frequently wakes the application processor during deep sleep to conduct background network location scans (`wifi_scan_always_enabled = 1`), aborting kernel suspend attempts with `EBUSY -16` (over 1,000 aborts observed in testing).

### How It Works Internally
- **Ambient Low-Bit Mode:** Enables `ambient_low_bit_enabled = 1` and `ambient_low_bit_enabled_dev = 1`, switching the AOD framebuffer to 16-bit color depth and cutting display memory bus bandwidth by over 50%.
- **Hardware Proximity Pocket Detection:** Enforces `doze_proximity_sensor_enabled = 1` to immediately shut down the AMOLED panel when the sensor is covered.
- **Dynamic Refresh Rate Scaling:** Sets `min_refresh_rate = 60.0`, allowing modern LTPO/high-refresh AMOLED panels to scale down from 120Hz/144Hz to their lowest hardware refresh floor during idle and AOD.
- **Kernel Suspend Stabilization:** Throttles `wifi_scan_always_enabled = 0` and `ble_scan_always_enabled = 0`, eliminating spurious RF chipset wakes and allowing the SoC to enter and remain in deep low-power sleep (C-states).
- **Phantom Process Protection:** Disables Android's aggressive background killer (`settings_enable_monitor_phantom_procs = false`) to ensure systemless services and background synchronization operate reliably.

### Interactive KernelSU / APatch WebUI
The module embeds a full Material You dark-themed WebUI accessible directly inside **KernelSU**, **KernelSU Next**, **APatch**, or **MMRL Manager**:
- **Real-Time Diagnostics:** Live readings of battery percentage, cell temperature, active display refresh rate, and power manager wakefulness state.
- **Granular Controls:** Individual toggles for Ambient Low-Bit, Proximity Pocket Mode, Minimum Refresh Rate (60Hz, 90Hz, 120Hz, or Default), Wi-Fi Scan Throttling, and Bluetooth Scan Throttling.
- **One-Tap Actions:** Instant "Save & Apply Now" and "Reset to Defaults" buttons that modify runtime settings and persist preferences to `/data/adb/modules/aod-power-optimizer/config.sh`.
- **KernelSU Action Script:** Tapping "ACTION" in KernelSU Manager immediately verifies and re-applies all active optimizations.

### Installation
1. Flash **`aod-power-optimizer.zip`** in KernelSU Next, KernelSU, APatch, or Magisk.
2. Reboot your device (or open **KernelSU Manager** -> **Modules** -> tap the **AOD & Deep Sleep Power Optimizer** card to launch the **WebUI** immediately).
3. Customize your preferred power profile and tap **Save & Apply Now**.

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
| **`IntuneBypass.apk`** | All Rooted Android (10–17) | Standalone LSPosed / Vector module to bypass Intune MAM & Company Portal root/RASP detection. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/IntuneBypass.apk) |
| **`PasskeyEnabler.apk`** | HyperOS 1/2/3 & Android 14–17 | Standalone LSPosed / Vector module to enable third-party passkeys (Bitwarden) on HyperOS. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/PasskeyEnabler.apk) |
| **`PlayIntegrity-TrickyStore-AIO.zip`** | HyperOS & Clean AOSP | All-in-one suite: Play Integrity Fork v18 + Tricky Store v1.4.1 + Strong Keybox. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/PlayIntegrity-TrickyStore-AIO.zip) |
| **`TrickyStore-StrongIntegrity.zip`** | Custom ROMs (Infinity-X, etc.) | Standalone Tricky Store v1.4.1 pre-loaded with Strong Keybox and auto security patch. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/TrickyStore-StrongIntegrity.zip) |
| **`AodDimmer.apk`** | HyperOS & AOSP 14–17 | LSPosed module companion app for AOD dimming and auto-brightness stabilizer. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/AodDimmer.apk) |
| **`aosp-no-keyboard-chin.zip`** | AOSP 14–17 | KernelSU / Magisk module to remove the keyboard chin spacer and dismiss button. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aosp-no-keyboard-chin.zip) |
| **`hyperos-aon-aod.zip`** | HyperOS 1/2/3 | KernelSU / Magisk module to unlock permanent Always-On Display. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/hyperos-aon-aod.zip) |
| **`aod-power-optimizer.zip`** | All Rooted Android (10–17) | KernelSU / APatch / Magisk module with WebUI to reduce AOD and idle battery drain. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aod-power-optimizer.zip) |

---

## License & Credits

- Developed for the Android and Xiaomi modding community.
- Built using Xposed / LSPosed API, Android Settings Provider API, KernelSU systemless architecture, and Tricky Store / Play Integrity Fork frameworks.
