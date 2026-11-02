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
| **`AutoBrightnessGuard.apk`** | HyperOS & AOSP 14–17 | Standalone LSPosed / Vector Module | **Auto-Brightness Anti-Runaway & Sensor Stabilizer (v2.1.0)** (Android 16 & 17 `BrightnessRangeController` HLG perceptual night clamping before hysteresis, temporary manual QS slider override, and non-starving ring-buffer lux deadband; strictly non-AOD). |
| **`aosp-no-keyboard-chin.zip`** | AOSP / Custom ROMs (Android 14–17) | KernelSU / Magisk Module & ADB | **Removes Keyboard Chin & Hide Button** (eliminates bottom gap and keyboard dismiss arrow). |
| **`hyperos-aon-aod.zip`** | HyperOS 1.0–3.0 / MIUI 14 | KernelSU / Magisk Module | **Unlocks true Always-On Display** (removes the 10-second limitation systemlessly in Settings). |
| **`aod-power-optimizer.zip`** | All Rooted Android (10–17) | KernelSU / APatch / Magisk Module with WebUI | **AOD Power Optimizer & Dimmer (v2.2.0)** (Strictly AOD-only battery optimizer, AMOLED LP2/LBM hardware rail lock blocking sensor-notifier 60-nit daylight HBM drain, AOSP Pocket Mode 60Hz controller, and AOD wake guard with WebUI. Zero network, radio, or Awake 120Hz impact). |

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

## Component 4: `AutoBrightnessGuard.apk` (Auto-Brightness Anti-Runaway & Sensor Stabilizer v2.1.0)

### Purpose & Scope Separation
`AutoBrightnessGuard.apk` (`com.n3th3r.autobrightnessguard`) is a dedicated, ultra-lightweight (~30 KB, zero AndroidX bloat, `minSdkVersion=29`, `targetSdkVersion=36`) LSPosed / Vector module focused **exclusively on interactive screen auto-brightness bugs** in Android 14–17 custom ROMs. All Always-On Display (AOD) dimming and power controls live separately in **`aod-power-optimizer.zip` v2.1.0**.

### Key Features (Updated for Android 16 & 17)
- **Pre-Hysteresis Night White-Screen Anti-Runaway Cap (`clampScreenBrightness` + `getAutomaticScreenBrightness`):**
  - On Android 17 (SDK 37), `AutomaticBrightnessController.updateAutoBrightness` computes hysteresis thresholds (`mScreenBrighteningThreshold` / `mScreenDarkeningThreshold`) and invokes `mCallbacks.updateBrightness()` *before* returning. `AutoBrightnessGuard` v2.1.0 hooks `clampScreenBrightness(float)` directly inside the calculation pipeline (plus `getAutomaticScreenBrightness` as belt-and-suspenders) so hysteresis thresholds and `DisplayPowerController` updates always see the capped dark-room value immediately.
  - Resolves `[brightnessMin, normalBrightnessMax]` via Android 17's `BrightnessRangeController.getCurrentBrightnessMin()` / `getNormalBrightnessMax()` (`0.4995103f` normal max vs `1.0f` outdoor HBM), with fallback to `BrightnessMappingStrategy.getNormalMaxBrightness()` on Android 15/16, ensuring `10%` in the app maps to an exact **`10.0%`** on the Quick Settings HLG brightness slider.
  - Applies `+20%` (minimum `+5 lux`) exit hysteresis so OLED white-page self-reflection near the lux threshold cannot oscillate out of the night cap.
- **Temporary Manual Quick Settings Slider Override:**
  - Hooks `setScreenBrightnessByUser(float, float)` and `setLightSensorEnabled(boolean)`: dragging the Quick Settings brightness slider manually above the night cap is obeyed smoothly without snapping back, and automatically resets to protected mode as soon as the screen turns off.
- **Ring-Buffer-Safe Daytime Jitter Deadband & Responsive EMA Smoothing:**
  - Never drops `handleLightSensorEvent(long, float)` timestamps (preventing Android 17 `AmbientLightRingBuffer.prune()` starvation) and bypasses filtering while `!mAmbientLuxValid` for 0 ms screen-wake initialization.
  - Stabilizes sub-threshold lux micro-jitter (`15%` / `0.8s` default) and applies responsive EMA smoothing (`0.45` upward / `0.55` downward).
- **Live Configuration Without Reboot:**
  - Throttled `XSharedPreferences` reload (`1.5s` interval) picks up slider and toggle changes live in `system_server` without filesystem `stat()` overhead on the sensor thread.

### Installation & Scope Configuration

#### Method A: Via Vector / LSPosed Manager UI
1. Install **`AutoBrightnessGuard.apk`**.
2. Open **Vector / LSPosed Manager** -> **Modules** -> enable **Auto-Brightness Guard** with **System Framework (`android`)** selected in scope.
3. Reboot once (required whenever hooking `system_server` for the first time), then adjust thresholds live anytime in the **Auto-Brightness Guard** app.

#### Method B: Via ADB / Root CLI (Vector v2+)
```bash
adb push AutoBrightnessGuard.apk /data/local/tmp/
adb shell "su -c 'pm install -r /data/local/tmp/AutoBrightnessGuard.apk && /data/adb/lspd/cli modules enable com.n3th3r.autobrightnessguard && /data/adb/lspd/cli scope set com.n3th3r.autobrightnessguard system/0 && rm -f /data/local/tmp/AutoBrightnessGuard.apk'"
```

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

## Component 8: `aod-power-optimizer.zip` (Unified AOD Power Optimizer & Dimmer v2.2.0 with WebUI)

### Strict AOD-Only Architecture (Zero Network, Radio, or Awake 120 Hz Impact)
Version `2.2.0` enforces a strict **AOD-only boundary**: while the phone is **Awake**, **zero** interactive settings, refresh rates (`min_refresh_rate` / `peak_refresh_rate` / `extreme_refresh_rate` 120 Hz Always), CPU frequencies, GPU frequencies, process limits, or network/radio settings are modified.

### Root Causes Diagnosed & Fixed in v2.2.0
1. **Eliminated Mobile Data Disconnects Outside (Zero Network/Radio/DeviceIdle Interference):**
   - In earlier builds, fast Deep Doze with `sensing_to=0` forced stationary deep doze while walking outside across cell towers, triggering Qualcomm RIL dormancy and LTE bearer drops (`LOST_CONNECTION` in `DataNetworkController`).
   - v2.2.0 completely deletes `DEEP_DOZE_OPT`, `WIFI_SCAN_THROTTLE`, and `BLE_SCAN_THROTTLE`. The module never touches Android RIL, Wi-Fi, Bluetooth, or `device_idle_constants`.
2. **AOSP Pocket Mode Screen 60 Hz (`POCKET_MODE_60HZ`):**
   - Replaced buggy `Settings.Secure.doze_proximity_sensor_enabled` (which deadlocked display wake on Xiaomi kernels) with dynamic 60 Hz throttling strictly while AOSP's native Pocket Mode lockscreen overlay (`com.android.server.policy.pocket.PocketLock`, queried via `IPocketService.isPocketLockVisible()`) is displayed.
   - Instantly restores your exact 120 Hz refresh rate (`min_refresh_rate=120.0`, `peak_refresh_rate=120.0`, `extreme_refresh_rate=1`) the instant Pocket Mode hides, unlocks, or sleeps. Zero CPU usage when unlocked or sleeping.
3. **Fixed Real-World Pocket AOD Battery Drain (Single-Tap Pocket Wakes & Full Infinity-X Lift Suppression):**
   - Battery stats revealed 100 pocket single-tap wakes (`27.5 wakes/hr`, `PULSING_SINGLE_TAP`) and uncaught Infinity-X pickup keys (`NODOZE pickup`).
   - v2.2.0 suppresses all 6 Project Infinity-X pickup keys (`doze_pulse_on_pick_up`, `doze_quick_pickup_gesture`, `doze_wake_display_gesture`, `doze_wake_screen_gesture`, `wake_gesture_enabled`, `ambient_tilt_to_wake`) and suppresses accidental single-tap pocket wakes (`doze_tap_gesture`, `doze_pulse_on_single_tap`, `ambient_touch_to_wake`).
   - **Double-Tap to Wake (`double_tap_to_wake=1`)**, **Screen-Off UDFPS fingerprint**, and **Power button wake** remain completely functional.
4. **Tightened Kernel AMOLED LP2 / LBM Rail Lock:**
   - On Xiaomi/Qualcomm `garnet` custom ROMs, `sensor-notifier` listens to `xiaomi.sensor.aod` (`0x0100050c`) and issues direct C++ `ioctl(/dev/mi_display/disp_feature)` calls (`mi_disp_ioctl_set_doze_brightness = 1` -> MIPI `0x51` `bl_lvl = 252`, 60 nits) continuously from sunrise through afternoon.
   - `doze_daemon.sh` monitors kernel + system events and immediately clamps `sensor-notifier` HBM overrides back to `2` (`bl_lvl = 20` LP2 rail, 4.5 nits) whenever `IN_DOZE=1`.
5. **Respects System AOD Brightness by Default:**
   - Leaves ROM `Settings.Secure.doze_brightness` (e.g. `1%` or `5%`) untouched by default (`AOD_BRIGHTNESS_OVERRIDE=0`), with an optional `1%–50%` override slider in WebUI.

### Interactive KernelSU / APatch WebUI
Accessible directly inside **KernelSU**, **KernelSU Next**, **APatch**, or **MMRL Manager**:
- **Live Hardware Monitor:** Real-time battery/temperature, power state (`Awake` vs `Dozing`), active AMOLED DDIC rail (`LP2 / LBM (2)` vs `Awake (0)`), Doze daemon PID, effective AOD brightness (`System` vs `Custom`), and active display refresh rate.
- **AOD Panel Rail & Brightness:** Toggle `LP2 / LBM` hardware rail, choose `Lock LBM / LP2 (4.5 nits)`, `Auto (Allow Outdoor HBM)`, or `Force HBM (60 nits)`, optionally override system AOD brightness (`1%–50%`), and toggle Ambient Low-Bit Mode.
- **AOD Wake Guard & Pocket Refresh Controller:** Toggle AOSP Pocket Mode 60 Hz, Keep Notifications Inside AOD, Suppress Lift / Table-Vibration Wake, and Suppress Single-Tap Pocket Wake.

### Installation
1. Flash **`aod-power-optimizer.zip`** in KernelSU Next, KernelSU, APatch, or Magisk.
2. Reboot your device (or open **KernelSU Manager** -> **Modules** -> tap **AOD Power Optimizer & Dimmer** WebUI / Action button to apply live).

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
| **`AutoBrightnessGuard.apk`** | HyperOS & AOSP 14–17 | Standalone LSPosed / Vector module v2.1.0 for Android 16 & 17 night white-screen anti-runaway cap & sensor stabilizer. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/AutoBrightnessGuard.apk) |
| **`aosp-no-keyboard-chin.zip`** | AOSP 14–17 | KernelSU / Magisk module to remove the keyboard chin spacer and dismiss button. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aosp-no-keyboard-chin.zip) |
| **`hyperos-aon-aod.zip`** | HyperOS 1/2/3 | KernelSU / Magisk module to unlock permanent Always-On Display. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/hyperos-aon-aod.zip) |
| **`aod-power-optimizer.zip`** | All Rooted Android (10–17) | Strictly AOD-only Power Optimizer & Dimmer v2.2.0 with WebUI (zero network/120Hz impact, AMOLED LP2/LBM rail lock, AOSP Pocket Mode 60Hz, & AOD wake guard). | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aod-power-optimizer.zip) |

---

## License & Credits

- Developed for the Android and Xiaomi modding community.
- Built using Xposed / LSPosed API, Android Settings Provider API, KernelSU systemless architecture, and Tricky Store / Play Integrity Fork frameworks.
