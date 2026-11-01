SKIPUNZIP=0

ui_print "************************************************"
ui_print "  AOD Power Optimizer & Dimmer v2.0.0           "
ui_print "  Author: PallabKumarS                          "
ui_print "************************************************"

ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" -x 'META-INF/*' -d "$MODPATH" >&2

ui_print "- Setting permissions..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/apply_settings.sh" 0 0 0755
set_perm "$MODPATH/doze_daemon.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
if [ -d "$MODPATH/webroot" ]; then
    set_perm_recursive "$MODPATH/webroot" 0 0 0755 0644
fi

ui_print "- Initializing configuration (respecting system AOD brightness by default)..."
HW_AOD_LBM=1
AOD_RAIL_MODE=auto
AOD_BRIGHTNESS_OVERRIDE=0
AOD_CUSTOM_BRIGHTNESS=1
DOZE_CPU_CAP=1
LOW_BIT=1
PROXIMITY_POCKET=1
MIN_REFRESH_RATE=60.0
DEEP_DOZE_OPT=1
WIFI_SCAN_THROTTLE=1
BLE_SCAN_THROTTLE=1
SUPPRESS_TILT_WAKE=0
PHANTOM_KILLER_DISABLE=1

EXISTING_MOD="/data/adb/modules/aod-power-optimizer"
if [ -f "$EXISTING_MOD/config.sh" ]; then
    . "$EXISTING_MOD/config.sh"
fi
if [ -f "$EXISTING_MOD/.orig_doze_brightness" ]; then
    cp -f "$EXISTING_MOD/.orig_doze_brightness" "$MODPATH/.orig_doze_brightness"
fi
if [ -f "$EXISTING_MOD/.override_was_active" ]; then
    cp -f "$EXISTING_MOD/.override_was_active" "$MODPATH/.override_was_active"
fi

CONFIG_FILE="$MODPATH/config.sh"
cat << EOF > "$CONFIG_FILE"
HW_AOD_LBM=$HW_AOD_LBM
AOD_RAIL_MODE=$AOD_RAIL_MODE
AOD_BRIGHTNESS_OVERRIDE=$AOD_BRIGHTNESS_OVERRIDE
AOD_CUSTOM_BRIGHTNESS=$AOD_CUSTOM_BRIGHTNESS
DOZE_CPU_CAP=$DOZE_CPU_CAP
LOW_BIT=$LOW_BIT
PROXIMITY_POCKET=$PROXIMITY_POCKET
MIN_REFRESH_RATE=$MIN_REFRESH_RATE
DEEP_DOZE_OPT=$DEEP_DOZE_OPT
WIFI_SCAN_THROTTLE=$WIFI_SCAN_THROTTLE
BLE_SCAN_THROTTLE=$BLE_SCAN_THROTTLE
SUPPRESS_TILT_WAKE=$SUPPRESS_TILT_WAKE
PHANTOM_KILLER_DISABLE=$PHANTOM_KILLER_DISABLE
EOF
set_perm "$CONFIG_FILE" 0 0 0644

ui_print "- Applying non-intrusive power settings..."
sh "$MODPATH/apply_settings.sh"

ui_print "- Installation complete. Open WebUI in KernelSU to configure."
