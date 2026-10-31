#!/system/bin/sh
MODDIR="${0%/*}"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 5

CONFIG_FILE="$MODDIR/config.sh"
if [ -f "$CONFIG_FILE" ]; then
    . "$CONFIG_FILE"
else
    LOW_BIT=1
    PROXIMITY_POCKET=1
    MIN_REFRESH_RATE=60.0
    WIFI_SCAN_THROTTLE=1
    BLE_SCAN_THROTTLE=1
    PHANTOM_KILLER_DISABLE=1
fi

if [ "$LOW_BIT" = "1" ]; then
    settings put global ambient_low_bit_enabled 1 2>/dev/null
    settings put global ambient_low_bit_enabled_dev 1 2>/dev/null
else
    settings put global ambient_low_bit_enabled 0 2>/dev/null
    settings put global ambient_low_bit_enabled_dev 0 2>/dev/null
fi

if [ "$PROXIMITY_POCKET" = "1" ]; then
    settings put secure doze_proximity_sensor_enabled 1 2>/dev/null
else
    settings put secure doze_proximity_sensor_enabled 0 2>/dev/null
fi

if [ -n "$MIN_REFRESH_RATE" ] && [ "$MIN_REFRESH_RATE" != "0" ]; then
    settings put system min_refresh_rate "$MIN_REFRESH_RATE" 2>/dev/null
else
    settings delete system min_refresh_rate 2>/dev/null
fi

if [ "$WIFI_SCAN_THROTTLE" = "1" ]; then
    settings put global wifi_scan_always_enabled 0 2>/dev/null
else
    settings put global wifi_scan_always_enabled 1 2>/dev/null
fi

if [ "$BLE_SCAN_THROTTLE" = "1" ]; then
    settings put global ble_scan_always_enabled 0 2>/dev/null
else
    settings put global ble_scan_always_enabled 1 2>/dev/null
fi

if [ "$PHANTOM_KILLER_DISABLE" = "1" ]; then
    device_config put activity_manager max_phantom_processes 2147483647 2>/dev/null
    settings put global settings_enable_monitor_phantom_procs false 2>/dev/null
fi
