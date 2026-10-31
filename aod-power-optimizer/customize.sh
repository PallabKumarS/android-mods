SKIPUNZIP=0

ui_print "********************************************"
ui_print "  AOD & Deep Sleep Power Optimizer v1.0.0   "
ui_print "  Author: PallabKumarS                      "
ui_print "********************************************"

ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" -x 'META-INF/*' -d "$MODPATH" >&2

ui_print "- Setting up permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
if [ -d "$MODPATH/webroot" ]; then
    set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
fi

ui_print "- Initializing default power configuration..."
CONFIG_FILE="$MODPATH/config.sh"
if [ ! -f "$CONFIG_FILE" ]; then
    cat << 'EOF' > "$CONFIG_FILE"
# AOD & Deep Sleep Power Optimizer Configuration
LOW_BIT=1
PROXIMITY_POCKET=1
MIN_REFRESH_RATE=60.0
WIFI_SCAN_THROTTLE=1
BLE_SCAN_THROTTLE=1
PHANTOM_KILLER_DISABLE=1
EOF
    set_perm "$CONFIG_FILE" 0 0 0644
fi

ui_print "- Applying immediate optimizations..."
settings put global ambient_low_bit_enabled 1 2>/dev/null
settings put global ambient_low_bit_enabled_dev 1 2>/dev/null
settings put secure doze_proximity_sensor_enabled 1 2>/dev/null
settings put system min_refresh_rate 60.0 2>/dev/null
settings put global wifi_scan_always_enabled 0 2>/dev/null
settings put global ble_scan_always_enabled 0 2>/dev/null
device_config put activity_manager max_phantom_processes 2147483647 2>/dev/null
settings put global settings_enable_monitor_phantom_procs false 2>/dev/null

ui_print "- Installation complete! Open WebUI in KernelSU to configure."
