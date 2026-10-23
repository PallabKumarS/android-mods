# shellcheck disable=SC2034
SONAME=tricky_store
SUPPORTED_ABIS="arm64 x64 arm"
MIN_SDK=29

if [ "$BOOTMODE" ] && [ "$KSU" ]; then
  ui_print "- Installing via KernelSU"
elif [ "$BOOTMODE" ] && [ "$MAGISK_VER_CODE" ]; then
  ui_print "- Installing via Magisk"
elif [ "$BOOTMODE" ] && [ "$APATCH" ]; then
  ui_print "- Installing via APatch"
else
  ui_print "- Installing module"
fi

VERSION=$(grep_prop version "${TMPDIR}/module.prop")
ui_print "- Setting up $VERSION"

support=false
for abi in $SUPPORTED_ABIS; do
  if [ "$ARCH" = "$abi" ]; then
    support=true
  fi
done

if [ "$support" = "false" ]; then
  abort "! Unsupported platform: $ARCH"
else
  ui_print "- Platform: $ARCH"
fi

if [ "$API" -lt $MIN_SDK ]; then
  abort "! Minimal supported SDK is $MIN_SDK (current: $API)"
fi

# 1. PIF Setup & Fresh Canary Fingerprint
ui_print "- Configuring Play Integrity Fork (Basic & Device Integrity)"
if [ -d "$MODPATH/zygisk" ]; then
    . $MODPATH/common_func.sh
    . $MODPATH/common_setup.sh
fi

rm -f /data/data/com.google.android.gms/cache/pif.prop /data/data/com.google.android.gms/pif.prop \
    /data/data/com.google.android.gms/cache/pif.json /data/data/com.google.android.gms/pif.json 2>/dev/null || true

# Deploy fresh Pixel Canary fingerprint to all relevant paths
for pif_dir in "/data/adb" "$MODPATH" "/data/adb/modules/playintegrityfix" "/data/adb/modules/playintegrity_trickystore_aio"; do
  mkdir -p "$pif_dir" 2>/dev/null || true
  if [ -f "$MODPATH/custom.pif.prop" ]; then
    cp -f "$MODPATH/custom.pif.prop" "$pif_dir/custom.pif.prop" 2>/dev/null || true
    cp -f "$MODPATH/custom.pif.prop" "$pif_dir/pif.prop" 2>/dev/null || true
  fi
  if [ -f "$MODPATH/custom.pif.json" ]; then
    cp -f "$MODPATH/custom.pif.json" "$pif_dir/custom.pif.json" 2>/dev/null || true
    cp -f "$MODPATH/custom.pif.json" "$pif_dir/pif.json" 2>/dev/null || true
  fi
done

# 2. Tricky Store Engine Setup
ui_print "- Configuring Tricky Store Engine (Strong Integrity)"
chmod 755 "$MODPATH/daemon" 2>/dev/null || true
if [ -f "$MODPATH/machikado.$ARCH" ]; then
  cp -f "$MODPATH/machikado.$ARCH" "$MODPATH/machikado" 2>/dev/null || true
fi

# Link/copy architecture library
if [ -f "$MODPATH/lib/$ARCH/lib$SONAME.so" ]; then
  cp -f "$MODPATH/lib/$ARCH/lib$SONAME.so" "$MODPATH/lib$SONAME.so" 2>/dev/null || true
fi

# 3. Permissions
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/daemon" 0 0 0755
[ -f "$MODPATH/machikado" ] && set_perm "$MODPATH/machikado" 0 0 0755

# 4. Tricky Store Data Deployment
CONFIG_DIR=/data/adb/tricky_store
mkdir -p "$CONFIG_DIR"

ui_print "- Deploying verified Strong Integrity Keybox"
if [ -f "$MODPATH/keybox.xml" ]; then
  cp -f "$MODPATH/keybox.xml" "$CONFIG_DIR/keybox.xml"
  chmod 0644 "$CONFIG_DIR/keybox.xml"
fi

ui_print "- Deploying optimal target configuration"
if [ -f "$MODPATH/target.txt" ]; then
  cp -f "$MODPATH/target.txt" "$CONFIG_DIR/target.txt"
  chmod 0644 "$CONFIG_DIR/target.txt"
fi

PIF_PATCH=""
if [ -f "$MODPATH/custom.pif.prop" ]; then
  PIF_PATCH=$(grep "^SECURITY_PATCH=" "$MODPATH/custom.pif.prop" | head -n 1 | cut -d= -f2 | tr -d '\r\n ')
fi
PATCH_DATE=$(getprop ro.build.version.security_patch)

if [ -n "$PIF_PATCH" ]; then
  ui_print "- Synchronizing Security Patch with PIF: $PIF_PATCH"
  echo "$PIF_PATCH" > "$CONFIG_DIR/security_patch.txt"
  chmod 0644 "$CONFIG_DIR/security_patch.txt"
elif [ -n "$PATCH_DATE" ]; then
  ui_print "- Auto-detected Security Patch: $PATCH_DATE"
  echo "$PATCH_DATE" > "$CONFIG_DIR/security_patch.txt"
  chmod 0644 "$CONFIG_DIR/security_patch.txt"
fi

rm -rf /data/data/com.google.android.gms/app_dg_cache* 2>/dev/null || true
rm -rf /data/user_de/0/com.google.android.gms/app_dg_cache* 2>/dev/null || true

if [ -d "/sdcard/InfinityResources" ]; then
  cp -f "$CONFIG_DIR/keybox.xml" "/sdcard/InfinityResources/keybox.xml" 2>/dev/null || true
fi

for pid in $(pidof com.google.android.gms.unstable com.android.vending 2>/dev/null); do
  kill -9 "$pid" 2>/dev/null || true
done

ui_print "- All-in-One Play Integrity Suite installed successfully"
ui_print "- Device & Strong Integrity active upon reboot"
