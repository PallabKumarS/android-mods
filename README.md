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
| **`AutoBrightnessGuard.apk`** | HyperOS & AOSP 14–17 | Standalone LSPosed / Vector Module | **Auto-Brightness Anti-Runaway & Sensor Stabilizer** (Fixes night white-screen brightness runaway via HLG perceptual clamping and suppresses daytime light-sensor flicker; strictly non-AOD). |
| **`aosp-no-keyboard-chin.zip`** | AOSP / Custom ROMs (Android 14–17) | KernelSU / Magisk Module & ADB | **Removes Keyboard Chin & Hide Button** (eliminates bottom gap and keyboard dismiss arrow). |
| **`hyperos-aon-aod.zip`** | HyperOS 1.0–3.0 / MIUI 14 | KernelSU / Magisk Module | **Unlocks true Always-On Display** (removes the 10-second limitation systemlessly in Settings). |
| **`aod-power-optimizer.zip`** | All Rooted Android (10–17) | KernelSU / APatch / Magisk Module with WebUI | **AOD Power Optimizer & Dimmer (v2.0.0)** (Unified AOD battery optimizer & dimmer: cuts 10h AOD drain to <=25% via AMOLED LP2/LBM DDIC rail switching, Doze-only CPU/GPU clamping, BLE/Wi-Fi wakeup throttling preserving TWS earbud auto-connect, and optional AOD brightness override respecting ROM 1% default). |

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

## Component 4: `AutoBrightnessGuard.apk` (Auto-Brightness Anti-Runaway & Sensor Stabilizer)

### Purpose & Scope Separation
`AutoBrightnessGuard.apk` (`com.n3th3r.autobrightnessguard`) is a dedicated, lightweight (~38 KB, zero AndroidX bloat) LSPosed / Vector module focused **exclusively on interactive screen auto-brightness bugs** in custom ROMs. All Always-On Display (AOD) dimming and power controls have been unified into **`aod-power-optimizer.zip` v2.0.0**.

### Key Features
- **Night White-Background Anti-Runaway Cap (HLG Perceptual Clamping):**
  - When reading white web pages, comics, or documents in a dark room (`mAmbientLux <= threshold`, default `25 lux`), light from the OLED panel reflects off the user's face/hands back into the top ambient light sensor (`tcs3701`), creating a positive feedback loop that ramps screen brightness toward 100%.
  - Hooks `AutomaticBrightnessController.updateAutoBrightness(boolean, boolean)` and converts the configured perceptual slider cap (default `25%`, range `10%–50%`) through Android's Hybrid Log-Gamma (HLG) curve against `getNormalMaxBrightness()` (`hbmMax`), clamping `mScreenAutoBrightness` and `mRawScreenAutoBrightness` to true dark-room nits so self-reflection runaway cannot ignite.
- **Daytime Jitter Debounce & Asymmetric EMA Lux Smoothing:**
  - Hooks `AutomaticBrightnessController.handleLightSensorEvent(long, float)` to suppress rapid minor lux fluctuations (`diffRatio < threshold` within the configurable debounce window, default `25%` / `2.0s`).
  - Applies an asymmetric Exponential Moving Average filter (`0.20` upward / `0.40` downward) for gradual transitions while automatically resetting stale smoothing state after `> 15s` screen-off gaps so wake-up brightness response is instantaneous.
- **Live Configuration Without Reboot:**
  - Uses `xposedminversion = 93` (`MODE_WORLD_READABLE` IPC `XSharedPreferences`) so slider and toggle adjustments apply live in `system_server`.

### Installation & Scope Configuration

#### Method A: Via Vector / LSPosed Manager UI
1. Install **`AutoBrightnessGuard.apk`**.
2. Open **Vector / LSPosed Manager** -> **Modules** -> enable **Auto-Brightness Guard** with **System Framework (`android`)** selected in scope.
3. Reboot once (required whenever hooking `system_server` for the first time), then adjust thresholds live anytime in the **Auto-Brightness Guard** app.

#### Method B: Via ADB / Root CLI (Vector v2+)
```bash
adb push AutoBrightnessGuard.apk /data/local/tmp/
adb shell "su -c 'pm install -r /data/local/tmp/AutoBrightnessGuard.apk && /data/adb/lspd/cli modules enable com.n3th3r.autobrightnessguard && /data/adb/lspd/cli scope set com.n3th3r.autobrightnessguard android/0 && rm -f /data/local/tmp/AutoBrightnessGuard.apk'"
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

## Component 8: `aod-power-optimizer.zip` (Unified AOD Power Optimizer & Dimmer v2.0.0 with WebUI)

### The Problem on AOSP & Custom ROMs
On Xiaomi / Qualcomm AMOLED devices (such as POCO X6 5G / Redmi Note 13 Pro 5G `garnet` with Video-Mode DSI panels `mdss_dsi_n16_36_0d_0a_dsc_vid`), migrating from stock Xiaomi HyperOS to AOSP custom ROMs (Project Infinity-X, Evolution X, LineageOS, CrDroid) causes heavy Always-On Display (AOD) battery drain (**45%–100% over 10 hours**, vs **<= 25% over 10 hours** on stock HyperOS).

### Root Causes Diagnosed & Fixed in v2.0.0
1. **AMOLED DDIC Stuck in High-Voltage `LP1` Rail (`~75 mA` excess continuous draw):** When entering `screenState=DOZE`, kernel `msm_drm` executes `dsi_panel_set_lp1` (`doze_brightness = 0`, 60-nit high-voltage DDIC charge pump) and custom ROMs lack Xiaomi's userspace service to switch `/sys/class/mi_display/disp-DSI-0/doze_brightness` to `2` (`DOZE_BRIGHTNESS_LBM` / `LP2` low-voltage DDIC rail). Live PMIC fuel-gauge benchmarking proved switching `0 -> 2` immediately drops Doze discharge current by **~27% (~75 mA / ~750 mAh over 10 hours)**.
2. **PowerHAL Left in `INTERACTIVE=true` During All 10 Hours of AOD:** Because Video-Mode DSI panels keep 1 SDE pipe scanning at 60 Hz (`config_powerDecoupleInteractiveModeFromDisplay = false`), Cortex-A78 big cores (`2.40 GHz`) and Adreno GPU (`940 MHz`) boost on every 60-second `TIME_TICK` clock update and Wi-Fi packet wakeup.
3. **Wi-Fi Wakeup Storms, Aggressive Custom-ROM BLE Duty Cycles, & Deep Doze Sensor Wakes:** Custom ROMs leave `wifi_wakeup_enabled = 1` and boost background BLE scan windows (`1400/140 ms` and `730/183 ms`, up to 25% radio duty cycle hit by GMS `NearbySharing`), plus wake accelerometer and GPS for 15 seconds on every Deep Doze maintenance window.
4. **Unified AOD Brightness Control (Respects System `1%` Default):** Merges AOD dimming into the same flashable module while **respecting the ROM's native `Settings.Secure.doze_brightness` (e.g. `1%`) by default** (`AOD_BRIGHTNESS_OVERRIDE=0`), with an optional WebUI override slider (`1%–50%`) that cleanly restores the original system value when toggled off.

### How It Works Internally
- **Zero-Polling Event-Driven Doze Daemon (`doze_daemon.sh`):** Passively blocks on `logcat -b system -T 1 -s PowerManagerService:I DreamManagerService:D LocalDisplayAdapter:I` (0% CPU and zero timers during sleep):
  - On `Dozing...` + `screenState=DOZE` + `SF.setDisplayPowerMode`: switches `/sys/class/mi_display/disp-DSI-0/doze_brightness` to `2` (`LP2 / LBM` in Auto mode when brightness `<= 15%`, or `1` `HBM` when `> 15%`) and applies `garnet`'s `LOW_POWER` SoC caps (`cpu0..3: 1.32 GHz`, `cpu4..7: 1.19 GHz`, `core_ctl/min_cpus: 0`, `kgsl: 500 MHz`).
  - On `Waking up`: immediately restores full Awake CPU (`0:9999999 .. 7:9999999`) and GPU (`940 MHz`) frequencies and resets `doze_brightness` to `0`.
- **Earbud-Safe Idle BLE Scan Throttling:** Keeps Bluetooth **ON** and Classic `SCAN_MODE_CONNECTABLE` completely untouched so TWS earbuds auto-connect immediately on case open, while tuning background BLE scan intervals to `10240 / 512 ms` (5% duty) and `4096 / 512 ms` (12.5% duty) to cut idle BLE drain by ~75%.
- **Fast Sensorless Deep Doze & Wi-Fi Sleep Policy:** Configures `device_idle_constants` (`inactive_to=60000,sensing_to=0,locating_to=0`), disables background Wi-Fi wakeup/scanning, enables 16-bit `ambient_low_bit_enabled`, enables `doze_proximity_sensor_enabled`, and sets `min_refresh_rate = 60.0`.

### Interactive KernelSU / APatch WebUI
Accessible directly inside **KernelSU**, **KernelSU Next**, **APatch**, or **MMRL Manager**:
- **Live Hardware Monitor:** Real-time battery/temperature, power state, active AMOLED DDIC rail (`LP2 / LBM (2)` vs `Normal (0)`), Doze daemon PID status, effective AOD brightness (`System` vs `Custom`), and refresh rate.
- **AOD Dimmer & Hardware Rail Controls:** Toggle `LP2 / LBM` rail switching, select DDIC rail target (`Auto`, `Force LBM`, `Force HBM`), optionally override system AOD brightness (`1%–50%`, off by default), and configure Low-Bit Mode, Proximity Pocket Shutoff, and Minimum Refresh Rate.
- **SoC, Radio & Deep Sleep Controls:** Individual toggles for Doze-Only CPU/GPU Clamp, Fast Sensorless Deep Doze, Wi-Fi Wakeup Suppression, Earbud-Safe BLE Duty Throttling, Optional Lift/Tilt Wake Suppression, and Phantom Process Killer Disabler.

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
| **`AutoBrightnessGuard.apk`** | HyperOS & AOSP 14–17 | Standalone LSPosed / Vector module for night white-screen anti-runaway cap & daytime sensor anti-flicker. | [Download APK](https://github.com/PallabKumarS/android-mods/releases/latest/download/AutoBrightnessGuard.apk) |
| **`aosp-no-keyboard-chin.zip`** | AOSP 14–17 | KernelSU / Magisk module to remove the keyboard chin spacer and dismiss button. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aosp-no-keyboard-chin.zip) |
| **`hyperos-aon-aod.zip`** | HyperOS 1/2/3 | KernelSU / Magisk module to unlock permanent Always-On Display. | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/hyperos-aon-aod.zip) |
| **`aod-power-optimizer.zip`** | All Rooted Android (10–17) | Unified AOD Power Optimizer & Dimmer v2.0.0 with WebUI (AMOLED LP2/LBM rail, Doze CPU/GPU clamp, & optional AOD brightness control). | [Download ZIP](https://github.com/PallabKumarS/android-mods/releases/latest/download/aod-power-optimizer.zip) |

---

## License & Credits

- Developed for the Android and Xiaomi modding community.
- Built using Xposed / LSPosed API, Android Settings Provider API, KernelSU systemless architecture, and Tricky Store / Play Integrity Fork frameworks.
