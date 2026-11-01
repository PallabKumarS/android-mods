#!/system/bin/sh
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"

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

if [ -f "$CONFIG_FILE" ]; then
    . "$CONFIG_FILE"
fi

apply_aod_display_settings() {
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
}

apply_aod_brightness_override() {
    CUR_BRT="$(settings get secure doze_brightness 2>/dev/null)"
    ORIG_FILE="$MODDIR/.orig_doze_brightness"
    FLAG_FILE="$MODDIR/.override_was_active"

    if [ "$AOD_BRIGHTNESS_OVERRIDE" = "1" ]; then
        if [ ! -f "$FLAG_FILE" ]; then
            if [ -n "$CUR_BRT" ] && [ "$CUR_BRT" != "null" ]; then
                echo "$CUR_BRT" > "$ORIG_FILE"
            fi
            touch "$FLAG_FILE"
        fi
        TARGET_BRT="${AOD_CUSTOM_BRIGHTNESS:-1}"
        settings put secure doze_brightness "$TARGET_BRT" 2>/dev/null
        return
    fi

    if [ -f "$FLAG_FILE" ]; then
        if [ -f "$ORIG_FILE" ]; then
            SAVED_BRT="$(cat "$ORIG_FILE" 2>/dev/null)"
            if [ -n "$SAVED_BRT" ] && [ "$SAVED_BRT" != "null" ]; then
                settings put secure doze_brightness "$SAVED_BRT" 2>/dev/null
            fi
        fi
        rm -f "$FLAG_FILE"
    elif [ -n "$CUR_BRT" ] && [ "$CUR_BRT" != "null" ]; then
        echo "$CUR_BRT" > "$ORIG_FILE"
    fi
}

apply_radio_and_doze_settings() {
    if [ "$WIFI_SCAN_THROTTLE" = "1" ]; then
        settings put global wifi_scan_always_enabled 0 2>/dev/null
        settings put global wifi_wakeup_enabled 0 2>/dev/null
        settings put global network_recommendations_enabled 0 2>/dev/null
        settings put global wifi_sleep_policy 2 2>/dev/null
        cmd wifi force-low-latency-mode disabled >/dev/null 2>&1
    else
        settings put global wifi_scan_always_enabled 1 2>/dev/null
    fi

    # Preserves Bluetooth ON and Classic connectable scan so TWS earbuds auto-connect immediately
    if [ "$BLE_SCAN_THROTTLE" = "1" ]; then
        settings put global ble_scan_always_enabled 0 2>/dev/null
        settings put global ble_scan_low_power_interval_ms 10240 2>/dev/null
        settings put global ble_scan_low_power_window_ms 512 2>/dev/null
        settings put global ble_scan_balanced_interval_ms 4096 2>/dev/null
        settings put global ble_scan_balanced_window_ms 512 2>/dev/null
    else
        settings delete global ble_scan_low_power_interval_ms 2>/dev/null
        settings delete global ble_scan_low_power_window_ms 2>/dev/null
        settings delete global ble_scan_balanced_interval_ms 2>/dev/null
        settings delete global ble_scan_balanced_window_ms 2>/dev/null
    fi

    if [ "$DEEP_DOZE_OPT" = "1" ]; then
        settings put global device_idle_constants "light_after_inactive_to=60000,inactive_to=60000,sensing_to=0,locating_to=0,idle_after_inactive_to=0,idle_pending_to=60000" 2>/dev/null
    else
        settings delete global device_idle_constants 2>/dev/null
    fi
}

apply_gesture_and_process_settings() {
    TILT_FLAG="$MODDIR/.tilt_was_suppressed"
    if [ "$SUPPRESS_TILT_WAKE" = "1" ]; then
        if [ ! -f "$TILT_FLAG" ]; then
            settings get secure wake_gesture_enabled 2>/dev/null > "$MODDIR/.orig_wake_gesture"
            touch "$TILT_FLAG"
        fi
        settings put secure wake_gesture_enabled 0 2>/dev/null
        settings put secure ambient_tilt_to_wake 0 2>/dev/null
    elif [ -f "$TILT_FLAG" ]; then
        ORIG_WAKE="$(cat "$MODDIR/.orig_wake_gesture" 2>/dev/null)"
        if [ "$ORIG_WAKE" = "1" ]; then
            settings put secure wake_gesture_enabled 1 2>/dev/null
        fi
        rm -f "$TILT_FLAG"
    fi

    if [ "$PHANTOM_KILLER_DISABLE" = "1" ]; then
        device_config put activity_manager max_phantom_processes 2147483647 >/dev/null 2>&1
        settings put global settings_enable_monitor_phantom_procs false 2>/dev/null
    else
        device_config put activity_manager max_phantom_processes 32 >/dev/null 2>&1
        settings put global settings_enable_monitor_phantom_procs true 2>/dev/null
    fi
}

apply_aod_display_settings
apply_aod_brightness_override
apply_radio_and_doze_settings
apply_gesture_and_process_settings
