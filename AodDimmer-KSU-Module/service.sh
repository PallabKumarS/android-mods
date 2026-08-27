#!/system/bin/sh
MODDIR=${0%/*}

# Wait until device has completely booted
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# Ensure doze auto-brightness and sensor features are active in MIUI/HyperOS
resetprop -n persist.doze.auto.brightness true
resetprop -n persist.doze.wakeup.sensor true

# Apply Extra Dim system settings for lower baseline minimum luminance
settings put secure reduce_bright_colors_activated 1
settings put secure reduce_bright_colors_level 60
