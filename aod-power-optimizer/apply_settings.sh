#!/system/bin/sh
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"

HW_AOD_LBM=1
AOD_RAIL_MODE=lbm
AOD_BRIGHTNESS_OVERRIDE=0
AOD_CUSTOM_BRIGHTNESS=1
LOW_BIT=1
POCKET_MODE_60HZ=1
SUPPRESS_AOD_PULSE_WAKE=1
SUPPRESS_TILT_WAKE=1
SUPPRESS_SINGLE_TAP_WAKE=1

if [ -f "$CONFIG_FILE" ]; then
    . "$CONFIG_FILE"
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

cleanup_legacy_radio_and_pocket() {
    if [ -f "$MODDIR/.awake_radio_snapshot" ]; then
        . "$MODDIR/.awake_radio_snapshot"
        restore_global_key wifi_scan_always_enabled "$ORIG_WIFI_SCAN"
        restore_global_key wifi_wakeup_enabled "$ORIG_WIFI_WAKE"
        restore_global_key ble_scan_always_enabled "$ORIG_BLE_ALWAYS"
        restore_global_key ble_scan_low_power_interval_ms "$ORIG_BLE_LP_INT"
        restore_global_key ble_scan_low_power_window_ms "$ORIG_BLE_LP_WIN"
        restore_global_key ble_scan_balanced_interval_ms "$ORIG_BLE_BAL_INT"
        restore_global_key ble_scan_balanced_window_ms "$ORIG_BLE_BAL_WIN"
        restore_global_key device_idle_constants "$ORIG_IDLE_CONST"
        rm -f "$MODDIR/.awake_radio_snapshot" "$MODDIR/.doze_scope_active"
    fi

    CUR_IDLE="$(settings get global device_idle_constants 2>/dev/null)"
    case "$CUR_IDLE" in
        *light_after_inactive_to=60000*)
            settings delete global device_idle_constants 2>/dev/null
            ;;
    esac

    # Disable buggy proximity sensor doze shutoff that caused black-screen deadlocks
    settings put secure doze_proximity_sensor_enabled 0 2>/dev/null

    if [ -f "$MODDIR/.pocket_60hz_active" ]; then
        if [ -f "$MODDIR/.pocket_fps_snapshot" ]; then
            . "$MODDIR/.pocket_fps_snapshot"
            [ -n "$ORIG_PEAK_FPS" ] && [ "$ORIG_PEAK_FPS" != "null" ] && \
                settings put system peak_refresh_rate "$ORIG_PEAK_FPS" 2>/dev/null
            [ -n "$ORIG_MIN_FPS" ] && [ "$ORIG_MIN_FPS" != "null" ] && \
                settings put system min_refresh_rate "$ORIG_MIN_FPS" 2>/dev/null
        else
            settings put system peak_refresh_rate 120.0 2>/dev/null
            settings put system min_refresh_rate 120.0 2>/dev/null
        fi
        rm -f "$MODDIR/.pocket_60hz_active"
    fi

    touch "$MODDIR/.v22_migrated"
}

ensure_awake_performance_untouched() {
    CPU_MAX_NODE="/sys/kernel/msm_performance/parameters/cpu_max_freq"
    GPU_MAX_NODE="/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
    GPU_AVAIL_NODE="/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies"

    if [ -w "$CPU_MAX_NODE" ]; then
        CUR_CPU="$(cat "$CPU_MAX_NODE" 2>/dev/null)"
        case "$CUR_CPU" in
            *1190400*|*1324800*)
                echo "0:9999999 1:9999999 2:9999999 3:9999999 4:9999999 5:9999999 6:9999999 7:9999999" > "$CPU_MAX_NODE" 2>/dev/null
                ;;
        esac
    fi

    if [ -r "$GPU_AVAIL_NODE" ] && [ -w "$GPU_MAX_NODE" ]; then
        MAX_GPU="$(awk '{print $1}' "$GPU_AVAIL_NODE" 2>/dev/null)"
        CUR_GPU="$(cat "$GPU_MAX_NODE" 2>/dev/null)"
        if [ -n "$MAX_GPU" ] && [ "$CUR_GPU" = "500000000" ]; then
            echo "$MAX_GPU" > "$GPU_MAX_NODE" 2>/dev/null
        fi
    fi

    EXTREME_FPS="$(settings get system extreme_refresh_rate 2>/dev/null)"
    if [ "$EXTREME_FPS" = "1" ]; then
        PEAK_FPS="$(settings get system peak_refresh_rate 2>/dev/null)"
        case "$PEAK_FPS" in
            ''|null|60.0) PEAK_FPS="120.0" ;;
        esac
        settings put system min_refresh_rate "$PEAK_FPS" 2>/dev/null
        settings put system peak_refresh_rate "$PEAK_FPS" 2>/dev/null
    fi

    settings delete global device_idle_constants 2>/dev/null
    settings delete global ble_scan_low_power_interval_ms 2>/dev/null
    settings delete global ble_scan_low_power_window_ms 2>/dev/null
    settings delete global ble_scan_balanced_interval_ms 2>/dev/null
    settings delete global ble_scan_balanced_window_ms 2>/dev/null
    settings delete global ble_scan_always_enabled 2>/dev/null
    settings put global settings_enable_monitor_phantom_procs true 2>/dev/null
    device_config delete activity_manager max_phantom_processes >/dev/null 2>&1
}

apply_aod_display_settings() {
    if [ "$LOW_BIT" = "1" ]; then
        settings put global ambient_low_bit_enabled 1 2>/dev/null
        settings put global ambient_low_bit_enabled_dev 1 2>/dev/null
    else
        settings put global ambient_low_bit_enabled 0 2>/dev/null
        settings put global ambient_low_bit_enabled_dev 0 2>/dev/null
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

save_and_set_setting() {
    NAMESPACE="$1"
    KEY="$2"
    TARGET_VAL="$3"
    FILE_PATH="$4"
    if [ ! -f "$FILE_PATH" ]; then
        VAL="$(settings get "$NAMESPACE" "$KEY" 2>/dev/null)"
        [ -n "$VAL" ] && [ "$VAL" != "null" ] && echo "$VAL" > "$FILE_PATH"
    fi
    settings put "$NAMESPACE" "$KEY" "$TARGET_VAL" 2>/dev/null
}

restore_setting_from_file() {
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

apply_pulse_wake_suppression() {
    PULSE_FLAG="$MODDIR/.pulse_was_suppressed"
    if [ "$SUPPRESS_AOD_PULSE_WAKE" = "1" ]; then
        save_and_set_setting secure doze_enabled 0 "$MODDIR/.orig_doze_enabled"
        save_and_set_setting secure ambient_pulse_enabled 0 "$MODDIR/.orig_ambient_pulse"
        touch "$PULSE_FLAG"
    elif [ -f "$PULSE_FLAG" ]; then
        restore_setting_from_file secure doze_enabled "$MODDIR/.orig_doze_enabled"
        restore_setting_from_file secure ambient_pulse_enabled "$MODDIR/.orig_ambient_pulse"
        rm -f "$PULSE_FLAG"
    fi
}

apply_tilt_wake_suppression() {
    TILT_FLAG="$MODDIR/.tilt_was_suppressed"
    if [ "$SUPPRESS_TILT_WAKE" = "1" ]; then
        save_and_set_setting secure wake_gesture_enabled 0 "$MODDIR/.orig_wake_gesture"
        save_and_set_setting global ambient_tilt_to_wake 0 "$MODDIR/.orig_tilt_to_wake"
        save_and_set_setting secure doze_pulse_on_pick_up 0 "$MODDIR/.orig_doze_pulse_on_pick_up"
        save_and_set_setting secure doze_quick_pickup_gesture 0 "$MODDIR/.orig_doze_quick_pickup_gesture"
        save_and_set_setting secure doze_wake_display_gesture 0 "$MODDIR/.orig_doze_wake_display_gesture"
        save_and_set_setting secure doze_wake_screen_gesture 0 "$MODDIR/.orig_doze_wake_screen_gesture"
        touch "$TILT_FLAG"
    elif [ -f "$TILT_FLAG" ]; then
        restore_setting_from_file secure wake_gesture_enabled "$MODDIR/.orig_wake_gesture"
        restore_setting_from_file global ambient_tilt_to_wake "$MODDIR/.orig_tilt_to_wake"
        restore_setting_from_file secure doze_pulse_on_pick_up "$MODDIR/.orig_doze_pulse_on_pick_up"
        restore_setting_from_file secure doze_quick_pickup_gesture "$MODDIR/.orig_doze_quick_pickup_gesture"
        restore_setting_from_file secure doze_wake_display_gesture "$MODDIR/.orig_doze_wake_display_gesture"
        restore_setting_from_file secure doze_wake_screen_gesture "$MODDIR/.orig_doze_wake_screen_gesture"
        rm -f "$TILT_FLAG"
    fi
}

apply_single_tap_wake_suppression() {
    SINGLE_TAP_FLAG="$MODDIR/.single_tap_was_suppressed"
    if [ "$SUPPRESS_SINGLE_TAP_WAKE" = "1" ]; then
        save_and_set_setting secure doze_tap_gesture 0 "$MODDIR/.orig_doze_tap_gesture"
        save_and_set_setting secure doze_pulse_on_single_tap 0 "$MODDIR/.orig_doze_pulse_on_single_tap"
        save_and_set_setting global ambient_touch_to_wake 0 "$MODDIR/.orig_ambient_touch_to_wake"
        touch "$SINGLE_TAP_FLAG"
    elif [ -f "$SINGLE_TAP_FLAG" ]; then
        restore_setting_from_file secure doze_tap_gesture "$MODDIR/.orig_doze_tap_gesture"
        restore_setting_from_file secure doze_pulse_on_single_tap "$MODDIR/.orig_doze_pulse_on_single_tap"
        restore_setting_from_file global ambient_touch_to_wake "$MODDIR/.orig_ambient_touch_to_wake"
        rm -f "$SINGLE_TAP_FLAG"
    fi
}

cleanup_legacy_radio_and_pocket
ensure_awake_performance_untouched
apply_aod_display_settings
apply_aod_brightness_override
apply_pulse_wake_suppression
apply_tilt_wake_suppression
apply_single_tap_wake_suppression
