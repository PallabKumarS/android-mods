# Magisk & KernelSU Modules

Pre-configured, zero-setup flashable modules for passing Google Play Integrity and Hardware Keystore Attestation.

## 1. TrickyStore-StrongIntegrity (`tricky-store-aio`)
- **File:** `TrickyStore-StrongIntegrity.zip`
- **Purpose:** Standalone Tricky Store v1.4.1 pre-configured with the verified Strong Integrity Keybox.
- **Features:**
  - Pre-bundles working ECDSA/RSA keybox certificate chain.
  - Automatically deploys `/data/adb/tricky_store/keybox.xml` with `0644` permissions.
  - Automatically configures `/data/adb/tricky_store/target.txt` (`com.google.android.gms`, `com.android.vending`).
  - Auto-detects device security patch date (`ro.build.version.security_patch`) and generates `security_patch.txt`.
  - Syncs backup to `/sdcard/InfinityResources/keybox.xml`.
- **Best For:** Custom ROMs (Infinity-X, Evolution X, CrDroid, Matrixx) that already feature built-in Play Integrity Fork prop spoofing.

## 2. PlayIntegrity-TrickyStore-AIO (`playintegrity-trickystore-aio`)
- **File:** `PlayIntegrity-TrickyStore-AIO.zip`
- **Purpose:** Complete all-in-one integrity suite combining **Play Integrity Fork v18** and **Tricky Store v1.4.1**.
- **Features:**
  - Passes all 3 tiers: `MEETS_BASIC_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`, and `MEETS_STRONG_INTEGRITY`.
  - Integrates Zygisk property spoofer for Google Play Services.
  - Hides bootloader unlock state and sensitive flags (`ro.boot.verifiedbootstate green`, `ro.secureboot.lockstate locked`).
  - Runs Tricky Store keystore injector daemon in parallel.
  - Completely self-installing: flash once in KernelSU / Magisk / APatch and reboot.
- **Best For:** HyperOS, MIUI, and clean AOSP / LineageOS ROMs without built-in fingerprint spoofing.
