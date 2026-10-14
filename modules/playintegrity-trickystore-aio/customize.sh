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

# 1. PIF Zygisk Setup
ui_print "- Configuring Play Integrity Fork (Basic & Device Integrity)"
if [ -d "$MODPATH/zygisk" ]; then
    . $MODPATH/common_func.sh
    . $MODPATH/common_setup.sh
fi

rm -f /data/data/com.google.android.gms/cache/pif.prop /data/data/com.google.android.gms/pif.prop \
    /data/data/com.google.android.gms/cache/pif.json /data/data/com.google.android.gms/pif.json 2>/dev/null || true

# 2. Tricky Store Engine Setup
ui_print "- Configuring Tricky Store Engine (Strong Integrity)"
chmod 755 "$MODPATH/daemon" 2>/dev/null || true
if [ -f "$MODPATH/machikado.$ARCH" ]; then
  mv "$MODPATH/machikado.$ARCH" "$MODPATH/machikado" 2>/dev/null || true
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

PATCH_DATE=$(getprop ro.build.version.security_patch)
if [ -n "$PATCH_DATE" ]; then
  ui_print "- Auto-detected Security Patch: $PATCH_DATE"
  echo "$PATCH_DATE" > "$CONFIG_DIR/security_patch.txt"
  chmod 0644 "$CONFIG_DIR/security_patch.txt"
fi

if [ -d "/sdcard/InfinityResources" ]; then
  cp -f "$CONFIG_DIR/keybox.xml" "/sdcard/InfinityResources/keybox.xml" 2>/dev/null || true
fi

ui_print "- All-in-One Play Integrity Suite installed successfully"
ui_print "- Device & Strong Integrity active upon reboot"
