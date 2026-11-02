#!/system/bin/sh
MODDIR="${0%/*}"
PID_FILE="$MODDIR/daemon.pid"
WATCH_PID_FILE="$MODDIR/pocket_watcher.pid"
SNAPSHOT_FILE="$MODDIR/.awake_radio_snapshot"

if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ]; then
        pkill -P "$DPID" 2>/dev/null
        kill "$DPID" 2>/dev/null
    fi
fi

if [ -f "$WATCH_PID_FILE" ]; then
    WPID="$(cat "$WATCH_PID_FILE" 2>/dev/null)"
    if [ -n "$WPID" ]; then
        kill "$WPID" 2>/dev/null
    fi
fi

if [ -f "$MODDIR/.pocket_fps_snapshot" ]; then
    . "$MODDIR/.pocket_fps_snapshot"
    [ -n "$ORIG_PEAK_FPS" ] && [ "$ORIG_PEAK_FPS" != "null" ] && \
        settings put system peak_refresh_rate "$ORIG_PEAK_FPS" 2>/dev/null
    [ -n "$ORIG_MIN_FPS" ] && [ "$ORIG_MIN_FPS" != "null" ] && \
        settings put system min_refresh_rate "$ORIG_MIN_FPS" 2>/dev/null
fi

[ -w "/sys/class/mi_display/disp-DSI-0/doze_brightness" ] && \
    echo "0" > /sys/class/mi_display/disp-DSI-0/doze_brightness 2>/dev/null

restore_setting_key() {
    NAMESPACE="$1"
    KEY="$2"
    FILE_PATH="$3"
    if [ -f "$FILE_PATH" ]; then
        VAL="$(cat "$FILE_PATH" 2>/dev/null)"
        if [ -n "$VAL" ] && [ "$VAL" != "null" ]; then
            settings put "$NAMESPACE" "$KEY" "$VAL" 2>/dev/null
        else
            settings delete "$NAMESPACE" "$KEY" 2>/dev/null
        fi
        rm -f "$FILE_PATH"
    fi
}

restore_setting_key secure doze_brightness "$MODDIR/.orig_doze_brightness"
restore_setting_key secure doze_enabled "$MODDIR/.orig_doze_enabled"
restore_setting_key secure ambient_pulse_enabled "$MODDIR/.orig_ambient_pulse"
restore_setting_key secure wake_gesture_enabled "$MODDIR/.orig_wake_gesture"
restore_setting_key global ambient_tilt_to_wake "$MODDIR/.orig_tilt_to_wake"
restore_setting_key secure doze_pulse_on_pick_up "$MODDIR/.orig_doze_pulse_on_pick_up"
restore_setting_key secure doze_quick_pickup_gesture "$MODDIR/.orig_doze_quick_pickup_gesture"
restore_setting_key secure doze_wake_display_gesture "$MODDIR/.orig_doze_wake_display_gesture"
restore_setting_key secure doze_wake_screen_gesture "$MODDIR/.orig_doze_wake_screen_gesture"
restore_setting_key secure doze_tap_gesture "$MODDIR/.orig_doze_tap_gesture"
restore_setting_key secure doze_pulse_on_single_tap "$MODDIR/.orig_doze_pulse_on_single_tap"
restore_setting_key global ambient_touch_to_wake "$MODDIR/.orig_ambient_touch_to_wake"

if [ -f "$SNAPSHOT_FILE" ]; then
    . "$SNAPSHOT_FILE"
    restore_setting_key global wifi_scan_always_enabled "$ORIG_WIFI_SCAN"
    restore_setting_key global wifi_wakeup_enabled "$ORIG_WIFI_WAKE"
    restore_setting_key global ble_scan_always_enabled "$ORIG_BLE_ALWAYS"
    restore_setting_key global ble_scan_low_power_interval_ms "$ORIG_BLE_LP_INT"
    restore_setting_key global ble_scan_low_power_window_ms "$ORIG_BLE_LP_WIN"
    restore_setting_key global ble_scan_balanced_interval_ms "$ORIG_BLE_BAL_INT"
    restore_setting_key global ble_scan_balanced_window_ms "$ORIG_BLE_BAL_WIN"
    restore_setting_key global device_idle_constants "$ORIG_IDLE_CONST"
    rm -f "$SNAPSHOT_FILE"
fi
