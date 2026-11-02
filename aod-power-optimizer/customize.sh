SKIPUNZIP=0

ui_print "************************************************"
ui_print "  AOD Power Optimizer & Dimmer v2.2.1           "
ui_print "  Strictly AOD-Only Architecture (Zero UI Lag)  "
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

ui_print "- Initializing strictly AOD-only configuration..."
HW_AOD_LBM=1
AOD_RAIL_MODE=lbm
AOD_BRIGHTNESS_OVERRIDE=0
AOD_CUSTOM_BRIGHTNESS=1
LOW_BIT=1
POCKET_MODE_60HZ=1
SUPPRESS_AOD_PULSE_WAKE=1
SUPPRESS_TILT_WAKE=1
SUPPRESS_SINGLE_TAP_WAKE=1

EXISTING_MOD="/data/adb/modules/aod-power-optimizer"
if [ -f "$EXISTING_MOD/config.sh" ]; then
    . "$EXISTING_MOD/config.sh"
    if [ ! -f "$EXISTING_MOD/.v22_migrated" ]; then
        POCKET_MODE_60HZ=1
        SUPPRESS_SINGLE_TAP_WAKE=1
    fi
fi

for STATE_FILE in \
    .orig_doze_brightness .override_was_active \
    .orig_doze_enabled .orig_ambient_pulse .pulse_was_suppressed \
    .orig_wake_gesture .orig_tilt_to_wake \
    .orig_doze_pulse_on_pick_up .orig_doze_quick_pickup_gesture \
    .orig_doze_wake_display_gesture .orig_doze_wake_screen_gesture \
    .tilt_was_suppressed \
    .orig_doze_tap_gesture .orig_doze_pulse_on_single_tap .orig_ambient_touch_to_wake \
    .single_tap_was_suppressed \
    .pocket_fps_snapshot .pocket_60hz_active \
    .awake_radio_snapshot .doze_scope_active; do
    if [ -f "$EXISTING_MOD/$STATE_FILE" ]; then
        cp -f "$EXISTING_MOD/$STATE_FILE" "$MODPATH/$STATE_FILE"
    fi
done

CONFIG_FILE="$MODPATH/config.sh"
cat << EOF > "$CONFIG_FILE"
HW_AOD_LBM=$HW_AOD_LBM
AOD_RAIL_MODE=$AOD_RAIL_MODE
AOD_BRIGHTNESS_OVERRIDE=$AOD_BRIGHTNESS_OVERRIDE
AOD_CUSTOM_BRIGHTNESS=$AOD_CUSTOM_BRIGHTNESS
LOW_BIT=$LOW_BIT
POCKET_MODE_60HZ=$POCKET_MODE_60HZ
SUPPRESS_AOD_PULSE_WAKE=$SUPPRESS_AOD_PULSE_WAKE
SUPPRESS_TILT_WAKE=$SUPPRESS_TILT_WAKE
SUPPRESS_SINGLE_TAP_WAKE=$SUPPRESS_SINGLE_TAP_WAKE
EOF
set_perm "$CONFIG_FILE" 0 0 0644

ui_print "- Cleaning legacy radio overrides and applying AOD-only settings..."
sh "$MODPATH/apply_settings.sh"

ui_print "- Installation complete. Open WebUI in KernelSU to configure."
