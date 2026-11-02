#!/system/bin/sh
MODDIR="${0%/*}"
PID_FILE="$MODDIR/daemon.pid"
SNAPSHOT_FILE="$MODDIR/.awake_radio_snapshot"

if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ]; then
        pkill -P "$DPID" 2>/dev/null
        kill "$DPID" 2>/dev/null
    fi
fi

[ -w "/sys/class/mi_display/disp-DSI-0/doze_brightness" ] && \
    echo "0" > /sys/class/mi_display/disp-DSI-0/doze_brightness 2>/dev/null

if [ -f "$MODDIR/.override_was_active" ] && [ -f "$MODDIR/.orig_doze_brightness" ]; then
    ORIG_BRT="$(cat "$MODDIR/.orig_doze_brightness" 2>/dev/null)"
    [ -n "$ORIG_BRT" ] && [ "$ORIG_BRT" != "null" ] && settings put secure doze_brightness "$ORIG_BRT" 2>/dev/null
fi

if [ -f "$MODDIR/.pulse_was_suppressed" ]; then
    ORIG_DOZE_EN="$(cat "$MODDIR/.orig_doze_enabled" 2>/dev/null)"
    ORIG_AMB_PULSE="$(cat "$MODDIR/.orig_ambient_pulse" 2>/dev/null)"
    [ -n "$ORIG_DOZE_EN" ] && [ "$ORIG_DOZE_EN" != "null" ] && settings put secure doze_enabled "$ORIG_DOZE_EN" 2>/dev/null
    [ -n "$ORIG_AMB_PULSE" ] && [ "$ORIG_AMB_PULSE" != "null" ] && settings put secure ambient_pulse_enabled "$ORIG_AMB_PULSE" 2>/dev/null
fi

if [ -f "$MODDIR/.tilt_was_suppressed" ]; then
    ORIG_WAKE="$(cat "$MODDIR/.orig_wake_gesture" 2>/dev/null)"
    ORIG_TILT="$(cat "$MODDIR/.orig_tilt_to_wake" 2>/dev/null)"
    [ -n "$ORIG_WAKE" ] && [ "$ORIG_WAKE" != "null" ] && settings put secure wake_gesture_enabled "$ORIG_WAKE" 2>/dev/null
    [ -n "$ORIG_TILT" ] && [ "$ORIG_TILT" != "null" ] && settings put global ambient_tilt_to_wake "$ORIG_TILT" 2>/dev/null
fi

restore_global_key() {
    KEY="$1"
    VAL="$2"
    if [ -z "$VAL" ] || [ "$VAL" = "null" ]; then
        settings delete global "$KEY" 2>/dev/null
    else
        settings put global "$KEY" "$VAL" 2>/dev/null
    fi
}

if [ -f "$SNAPSHOT_FILE" ]; then
    . "$SNAPSHOT_FILE"
    restore_global_key wifi_scan_always_enabled "$ORIG_WIFI_SCAN"
    restore_global_key wifi_wakeup_enabled "$ORIG_WIFI_WAKE"
    restore_global_key ble_scan_always_enabled "$ORIG_BLE_ALWAYS"
    restore_global_key ble_scan_low_power_interval_ms "$ORIG_BLE_LP_INT"
    restore_global_key ble_scan_low_power_window_ms "$ORIG_BLE_LP_WIN"
    restore_global_key ble_scan_balanced_interval_ms "$ORIG_BLE_BAL_INT"
    restore_global_key ble_scan_balanced_window_ms "$ORIG_BLE_BAL_WIN"
    restore_global_key device_idle_constants "$ORIG_IDLE_CONST"
fi
