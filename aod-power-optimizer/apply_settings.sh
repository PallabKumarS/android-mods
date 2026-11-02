#!/system/bin/sh
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"

HW_AOD_LBM=1
AOD_RAIL_MODE=lbm
AOD_BRIGHTNESS_OVERRIDE=0
AOD_CUSTOM_BRIGHTNESS=1
LOW_BIT=1
PROXIMITY_POCKET=1
SUPPRESS_AOD_PULSE_WAKE=1
SUPPRESS_TILT_WAKE=1
DEEP_DOZE_OPT=1
WIFI_SCAN_THROTTLE=1
BLE_SCAN_THROTTLE=1

if [ -f "$CONFIG_FILE" ]; then
    . "$CONFIG_FILE"
fi

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

    if [ -f "$MODDIR/.v21_migrated" ]; then
        return
    fi

    EXTREME_FPS="$(settings get system extreme_refresh_rate 2>/dev/null)"
    if [ "$EXTREME_FPS" = "1" ]; then
        PEAK_FPS="$(settings get system peak_refresh_rate 2>/dev/null)"
        case "$PEAK_FPS" in
            ''|null) PEAK_FPS="120.0" ;;
        esac
        settings put system min_refresh_rate "$PEAK_FPS" 2>/dev/null
    else
        CUR_MIN_FPS="$(settings get system min_refresh_rate 2>/dev/null)"
        [ "$CUR_MIN_FPS" = "60.0" ] && settings delete system min_refresh_rate 2>/dev/null
    fi

    settings delete global device_idle_constants 2>/dev/null
    settings delete global ble_scan_low_power_interval_ms 2>/dev/null
    settings delete global ble_scan_low_power_window_ms 2>/dev/null
    settings delete global ble_scan_balanced_interval_ms 2>/dev/null
    settings delete global ble_scan_balanced_window_ms 2>/dev/null
    settings delete global ble_scan_always_enabled 2>/dev/null
    settings put global settings_enable_monitor_phantom_procs true 2>/dev/null
    device_config delete activity_manager max_phantom_processes >/dev/null 2>&1

    touch "$MODDIR/.v21_migrated"
}

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

apply_aod_wake_suppression() {
    PULSE_FLAG="$MODDIR/.pulse_was_suppressed"
    if [ "$SUPPRESS_AOD_PULSE_WAKE" = "1" ]; then
        if [ ! -f "$PULSE_FLAG" ]; then
            settings get secure doze_enabled 2>/dev/null > "$MODDIR/.orig_doze_enabled"
            settings get secure ambient_pulse_enabled 2>/dev/null > "$MODDIR/.orig_ambient_pulse"
            touch "$PULSE_FLAG"
        fi
        settings put secure doze_enabled 0 2>/dev/null
        settings put secure ambient_pulse_enabled 0 2>/dev/null
    elif [ -f "$PULSE_FLAG" ]; then
        ORIG_DOZE_EN="$(cat "$MODDIR/.orig_doze_enabled" 2>/dev/null)"
        ORIG_AMB_PULSE="$(cat "$MODDIR/.orig_ambient_pulse" 2>/dev/null)"
        [ -n "$ORIG_DOZE_EN" ] && [ "$ORIG_DOZE_EN" != "null" ] && settings put secure doze_enabled "$ORIG_DOZE_EN" 2>/dev/null
        [ -n "$ORIG_AMB_PULSE" ] && [ "$ORIG_AMB_PULSE" != "null" ] && settings put secure ambient_pulse_enabled "$ORIG_AMB_PULSE" 2>/dev/null
        rm -f "$PULSE_FLAG"
    fi

    TILT_FLAG="$MODDIR/.tilt_was_suppressed"
    if [ "$SUPPRESS_TILT_WAKE" = "1" ]; then
        if [ ! -f "$TILT_FLAG" ]; then
            settings get secure wake_gesture_enabled 2>/dev/null > "$MODDIR/.orig_wake_gesture"
            settings get global ambient_tilt_to_wake 2>/dev/null > "$MODDIR/.orig_tilt_to_wake"
            touch "$TILT_FLAG"
        fi
        settings put secure wake_gesture_enabled 0 2>/dev/null
        settings put global ambient_tilt_to_wake 0 2>/dev/null
    elif [ -f "$TILT_FLAG" ]; then
        ORIG_WAKE="$(cat "$MODDIR/.orig_wake_gesture" 2>/dev/null)"
        ORIG_TILT="$(cat "$MODDIR/.orig_tilt_to_wake" 2>/dev/null)"
        [ -n "$ORIG_WAKE" ] && [ "$ORIG_WAKE" != "null" ] && settings put secure wake_gesture_enabled "$ORIG_WAKE" 2>/dev/null
        [ -n "$ORIG_TILT" ] && [ "$ORIG_TILT" != "null" ] && settings put global ambient_tilt_to_wake "$ORIG_TILT" 2>/dev/null
        rm -f "$TILT_FLAG"
    fi
}

ensure_awake_performance_untouched
apply_aod_display_settings
apply_aod_brightness_override
apply_aod_wake_suppression
