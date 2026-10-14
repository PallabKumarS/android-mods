# Tricky Store Keybox & Spoofing Backup

Extracted from Project Infinity-X ROM (POCO X6 5G / `garnet`). Verified passing Strong Integrity (`MEETS_STRONG_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`, `MEETS_BASIC_INTEGRITY`).

## Keybox Specification

- **Source:** Infinity-X Attestation Spoofing (`t.me/keyboxstrong @evokerr`)
- **Key Algorithms:** ECDSA (`secp256r1`) & RSA (`2048-bit`)
- **Certificate Chain Length:** 3 certificates
- **MD5 Hash:** `82d9ca07c90c170eedde30e83cc55270`
- **File Size:** 13,280 bytes

---

## Deployment Methods

### 1. Standard Root Module (HyperOS, AOSP, LineageOS, Evolution X)
When using the standalone **Tricky Store** module on KernelSU, Magisk, or APatch:

1. Flash the **Tricky Store** zip in your root manager (e.g., `Tricky-Store-v1.4.1-245-72b2e84-release.zip`).
2. Copy `keybox.xml` and `target.txt` to:
   ```bash
   /data/adb/tricky_store/keybox.xml
   /data/adb/tricky_store/target.txt
   ```
3. Set file permissions:
   ```bash
   chmod 644 /data/adb/tricky_store/*
   ```
4. Restart Keystore and Keymint services (or reboot device):
   ```bash
   kill -9 $(pidof android.hardware.security.keymint-service)
   kill -9 $(pidof android.hardware.security.keystore-service)
   ```

### 2. Custom ROMs with Built-in Tricky Store (Infinity-X, etc.)
If your ROM supports built-in Keybox spoofing:
- **Storage Path:** Copy `keybox.xml` to `/sdcard/InfinityResources/keybox.xml`.
- **System Setting:** Alternatively, inject via Android Secure Settings:
  ```bash
  adb shell settings put secure spoof_trickystore_keybox "<base64_string>"
  ```

### 3. Automated ADB Deployment
Run the included helper script while your device is connected via USB debugging:
- **Windows:** Run `push-to-device.bat`
- **Linux/macOS:** Run `sh push-to-device.sh`

---

## Target Configuration (`target.txt`)

By default, Keystore spoofing is applied to:
```text
com.google.android.gms
com.android.vending
```
To spoof specific banking or integrity-checking applications, append their package names to `target.txt`.
