#!/system/bin/sh
MODDIR="${0%/*}"
PID_FILE="$MODDIR/daemon.pid"

if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ]; then
        pkill -P "$DPID" 2>/dev/null
        kill "$DPID" 2>/dev/null
    fi
fi

[ -w "/sys/kernel/msm_performance/parameters/cpu_max_freq" ] && \
    echo "0:9999999 1:9999999 2:9999999 3:9999999 4:9999999 5:9999999 6:9999999 7:9999999" > /sys/kernel/msm_performance/parameters/cpu_max_freq 2>/dev/null

if [ -r "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies" ] && [ -w "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq" ]; then
    MAX_GPU="$(awk '{print $1}' /sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies 2>/dev/null)"
    [ -n "$MAX_GPU" ] && echo "$MAX_GPU" > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq 2>/dev/null
fi

[ -w "/sys/class/mi_display/disp-DSI-0/doze_brightness" ] && \
    echo "0" > /sys/class/mi_display/disp-DSI-0/doze_brightness 2>/dev/null

if [ -f "$MODDIR/.override_was_active" ] && [ -f "$MODDIR/.orig_doze_brightness" ]; then
    ORIG_BRT="$(cat "$MODDIR/.orig_doze_brightness" 2>/dev/null)"
    [ -n "$ORIG_BRT" ] && [ "$ORIG_BRT" != "null" ] && settings put secure doze_brightness "$ORIG_BRT" 2>/dev/null
fi

if [ -f "$MODDIR/.tilt_was_suppressed" ] && [ -f "$MODDIR/.orig_wake_gesture" ]; then
    ORIG_WAKE="$(cat "$MODDIR/.orig_wake_gesture" 2>/dev/null)"
    [ "$ORIG_WAKE" = "1" ] && settings put secure wake_gesture_enabled 1 2>/dev/null
fi

settings delete global device_idle_constants 2>/dev/null
settings delete global ble_scan_low_power_interval_ms 2>/dev/null
settings delete global ble_scan_low_power_window_ms 2>/dev/null
settings delete global ble_scan_balanced_interval_ms 2>/dev/null
settings delete global ble_scan_balanced_window_ms 2>/dev/null
settings delete system min_refresh_rate 2>/dev/null
