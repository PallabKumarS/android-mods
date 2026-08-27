#!/system/bin/sh
MODDIR=${0%/*}

# Bind mount for /system_ext/framework/miui-services.jar
TARGET="/system_ext/framework/miui-services.jar"
SOURCE="$MODDIR/system_ext/framework/miui-services.jar"

if [ -f "$SOURCE" ] && [ -f "$TARGET" ]; then
    # Match SELinux context
    chcon --reference="$TARGET" "$SOURCE" 2>/dev/null || chcon u:object_r:system_file:s0 "$SOURCE" 2>/dev/null
    
    # Perform bind mount over /system_ext
    mount -o bind "$SOURCE" "$TARGET"
fi
