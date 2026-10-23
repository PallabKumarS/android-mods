# shellcheck disable=SC2034
SKIPUNZIP=1

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
ui_print "- Installing $SONAME $VERSION"

support=false
for abi in $SUPPORTED_ABIS; do
  if [ "$ARCH" = "$abi" ]; then
    support=true
  fi
done

if [ "$support" = "false" ]; then
  abort "! Unsupported platform: $ARCH"
else
  ui_print "- Device platform: $ARCH"
fi

if [ "$API" -lt $MIN_SDK ]; then
  abort "! Minimal supported SDK is $MIN_SDK (current: $API)"
fi

ui_print "- Extracting module engine"
unzip -o "$ZIPFILE" 'module.prop' -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" 'post-fs-data.sh' -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" 'service.sh' -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" 'service.apk' -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" 'sepolicy.rule' -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" 'daemon' -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" 'mazoku' -d "$MODPATH" >/dev/null 2>&1
chmod 755 "$MODPATH/daemon"

ui_print "- Extracting $ARCH libraries"
unzip -o -j "$ZIPFILE" "lib/$ARCH/lib$SONAME.so" -d "$MODPATH" >/dev/null 2>&1
unzip -o -j "$ZIPFILE" "machikado.$ARCH" -d "$MODPATH" >/dev/null 2>&1
mv "$MODPATH/machikado.$ARCH" "$MODPATH/machikado" 2>/dev/null || true

ui_print "- Setting binary permissions"
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/daemon" 0 0 0755

CONFIG_DIR=/data/adb/tricky_store
mkdir -p "$CONFIG_DIR"

ui_print "- Deploying verified Strong Integrity Keybox"
unzip -o "$ZIPFILE" 'keybox.xml' -d "$CONFIG_DIR" >/dev/null 2>&1
chmod 0644 "$CONFIG_DIR/keybox.xml"

ui_print "- Deploying optimal target configuration"
unzip -o "$ZIPFILE" 'target.txt' -d "$CONFIG_DIR" >/dev/null 2>&1
chmod 0644 "$CONFIG_DIR/target.txt"

PIF_PATCH=""
if [ -f "/data/adb/pif.prop" ]; then
  PIF_PATCH=$(grep "^SECURITY_PATCH=" "/data/adb/pif.prop" 2>/dev/null | head -n 1 | cut -d= -f2 | tr -d '\r\n ')
elif [ -f "/data/adb/modules/playintegrityfix/custom.pif.prop" ]; then
  PIF_PATCH=$(grep "^SECURITY_PATCH=" "/data/adb/modules/playintegrityfix/custom.pif.prop" 2>/dev/null | head -n 1 | cut -d= -f2 | tr -d '\r\n ')
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

ui_print "- Tricky Store fully configured for Strong Integrity"
