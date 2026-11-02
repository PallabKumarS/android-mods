#!/system/bin/sh
exec </dev/null >/dev/null 2>&1
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"
PID_FILE="$MODDIR/daemon.pid"
SNAPSHOT_FILE="$MODDIR/.awake_radio_snapshot"
SCOPE_FLAG="$MODDIR/.doze_scope_active"

MI_DOZE_NODE="/sys/class/mi_display/disp-DSI-0/doze_brightness"

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$OLD_PID" ] && [ "$OLD_PID" != "$$" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        pkill -P "$OLD_PID" 2>/dev/null
        kill "$OLD_PID" 2>/dev/null
        sleep 1
    fi
fi
echo "$$" > "$PID_FILE"

load_config() {
    HW_AOD_LBM=1
    AOD_RAIL_MODE=lbm
    AOD_BRIGHTNESS_OVERRIDE=0
    AOD_CUSTOM_BRIGHTNESS=1
    DEEP_DOZE_OPT=1
    WIFI_SCAN_THROTTLE=1
    BLE_SCAN_THROTTLE=1
    if [ -f "$CONFIG_FILE" ]; then
        . "$CONFIG_FILE"
    fi
}

resolve_target_rail() {
    case "$AOD_RAIL_MODE" in
        lbm) echo "2" ;;
        hbm) echo "1" ;;
        *)
            EFF_BRT="$(settings get secure doze_brightness 2>/dev/null)"
            case "$EFF_BRT" in
                ''|*[!0-9]*) EFF_BRT=5 ;;
            esac
            if [ "$EFF_BRT" -le 15 ]; then
                echo "2"
            else
                echo "1"
            fi
            ;;
    esac
}

apply_doze_panel_rail() {
    load_config
    if [ "$HW_AOD_LBM" != "1" ] || [ ! -w "$MI_DOZE_NODE" ]; then
        return
    fi
    TARGET_RAIL="$(resolve_target_rail)"
    CUR_RAIL="$(cat "$MI_DOZE_NODE" 2>/dev/null)"
    if [ "$CUR_RAIL" != "$TARGET_RAIL" ]; then
        echo "$TARGET_RAIL" > "$MI_DOZE_NODE" 2>/dev/null
    fi
}

handle_kernel_doze_rail_event() {
    EVENT_LINE="$1"
    load_config
    if [ "$HW_AOD_LBM" != "1" ] || [ ! -w "$MI_DOZE_NODE" ]; then
        return
    fi
    case "$AOD_RAIL_MODE" in
        lbm)
            case "$EVENT_LINE" in
                *"doze_brightness = 1"*)
                    echo "2" > "$MI_DOZE_NODE" 2>/dev/null
                    ;;
            esac
            ;;
        hbm)
            case "$EVENT_LINE" in
                *"doze_brightness = 2"*)
                    echo "1" > "$MI_DOZE_NODE" 2>/dev/null
                    ;;
            esac
            ;;
    esac
}

restore_global_key() {
    KEY="$1"
    VAL="$2"
    if [ -z "$VAL" ] || [ "$VAL" = "null" ]; then
        settings delete global "$KEY" 2>/dev/null
    else
        settings put global "$KEY" "$VAL" 2>/dev/null
    fi
}

sanitize_val() {
    printf '%s' "$1" | tr -d "'\r\n"
}

enter_doze_scoped_optimizations() {
    load_config
    if [ ! -f "$SCOPE_FLAG" ]; then
        cat << EOF > "$SNAPSHOT_FILE"
ORIG_WIFI_SCAN='$(sanitize_val "$(settings get global wifi_scan_always_enabled 2>/dev/null)")'
ORIG_WIFI_WAKE='$(sanitize_val "$(settings get global wifi_wakeup_enabled 2>/dev/null)")'
ORIG_BLE_ALWAYS='$(sanitize_val "$(settings get global ble_scan_always_enabled 2>/dev/null)")'
ORIG_BLE_LP_INT='$(sanitize_val "$(settings get global ble_scan_low_power_interval_ms 2>/dev/null)")'
ORIG_BLE_LP_WIN='$(sanitize_val "$(settings get global ble_scan_low_power_window_ms 2>/dev/null)")'
ORIG_BLE_BAL_INT='$(sanitize_val "$(settings get global ble_scan_balanced_interval_ms 2>/dev/null)")'
ORIG_BLE_BAL_WIN='$(sanitize_val "$(settings get global ble_scan_balanced_window_ms 2>/dev/null)")'
ORIG_IDLE_CONST='$(sanitize_val "$(settings get global device_idle_constants 2>/dev/null)")'
EOF
        touch "$SCOPE_FLAG"
    fi

    if [ "$WIFI_SCAN_THROTTLE" = "1" ]; then
        settings put global wifi_scan_always_enabled 0 2>/dev/null
        settings put global wifi_wakeup_enabled 0 2>/dev/null
    fi

    if [ "$BLE_SCAN_THROTTLE" = "1" ]; then
        settings put global ble_scan_always_enabled 0 2>/dev/null
        settings put global ble_scan_low_power_interval_ms 10240 2>/dev/null
        settings put global ble_scan_low_power_window_ms 512 2>/dev/null
        settings put global ble_scan_balanced_interval_ms 4096 2>/dev/null
        settings put global ble_scan_balanced_window_ms 512 2>/dev/null
    fi

    if [ "$DEEP_DOZE_OPT" = "1" ]; then
        settings put global device_idle_constants "light_after_inactive_to=60000,inactive_to=60000,sensing_to=0,locating_to=0,idle_after_inactive_to=0,idle_pending_to=60000" 2>/dev/null
    fi
}

exit_doze_scoped_optimizations() {
    if [ ! -f "$SCOPE_FLAG" ]; then
        return
    fi
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
    rm -f "$SCOPE_FLAG"
}

restore_awake_panel_and_scope() {
    if [ -w "$MI_DOZE_NODE" ]; then
        CUR_RAIL="$(cat "$MI_DOZE_NODE" 2>/dev/null)"
        if [ -n "$CUR_RAIL" ] && [ "$CUR_RAIL" != "0" ]; then
            echo "0" > "$MI_DOZE_NODE" 2>/dev/null
        fi
    fi
    exit_doze_scoped_optimizations
}

cleanup_daemon() {
    pkill -P "$$" 2>/dev/null
    restore_awake_panel_and_scope
    rm -f "$PID_FILE"
    exit 0
}

trap cleanup_daemon INT TERM EXIT

IN_DOZE=0
SCREEN_DOZE=0

if dumpsys power 2>/dev/null | grep -q "mWakefulness=Dozing"; then
    IN_DOZE=1
    enter_doze_scoped_optimizations
    if dumpsys display 2>/dev/null | grep -qE "mScreenState=DOZE|Display State: DOZE"; then
        SCREEN_DOZE=1
        apply_doze_panel_rail
    fi
else
    exit_doze_scoped_optimizations
fi

# logcat -e filters on message body (not tag), so patterns match exact framework and msm_drm message bodies
LOG_REGEX='^Dozing\.\.\.|^Waking up from|Dream requested to start dozing:.*screenState=|dsi_display_set_power.*Power mode \(|SF\.setDisplayPowerMode|mi_disp_ioctl_set_doze_brightness'

while true; do
    logcat -b system,kernel -v brief -T 1 -e "$LOG_REGEX" 2>/dev/null | while IFS= read -r LINE; do
        case "$LINE" in
            *"PowerManagerService"*": Dozing..."*)
                IN_DOZE=1
                SCREEN_DOZE=0
                if [ ! -w "$MI_DOZE_NODE" ] && [ ! -f "$SCOPE_FLAG" ]; then
                    enter_doze_scoped_optimizations
                fi
                ;;
            *"PowerManagerService"*": Waking up"*)
                IN_DOZE=0
                SCREEN_DOZE=0
                if [ -w "$MI_DOZE_NODE" ]; then
                    echo "0" > "$MI_DOZE_NODE" 2>/dev/null
                fi
                exit_doze_scoped_optimizations
                ;;
            *"DreamManagerService"*"screenState=DOZE"*)
                IN_DOZE=1
                SCREEN_DOZE=1
                apply_doze_panel_rail
                ;;
            *"dsi_display_set_power"*"Power mode (Doze"*)
                IN_DOZE=1
                SCREEN_DOZE=1
                apply_doze_panel_rail
                if [ ! -f "$SCOPE_FLAG" ]; then
                    enter_doze_scoped_optimizations
                fi
                ;;
            *"dsi_display_set_power"*"Power mode (Off)"*)
                SCREEN_DOZE=0
                if [ "$IN_DOZE" = "1" ] && [ ! -f "$SCOPE_FLAG" ]; then
                    enter_doze_scoped_optimizations
                fi
                ;;
            *"DreamManagerService"*"screenState=ON"*|*"DreamManagerService"*"screenState=OFF"*|*"dsi_display_set_power"*"Power mode (On)"*)
                SCREEN_DOZE=0
                ;;
            *"LocalDisplayAdapter"*"SF.setDisplayPowerMode"*)
                if [ "$IN_DOZE" = "1" ]; then
                    if [ "$SCREEN_DOZE" = "1" ]; then
                        apply_doze_panel_rail
                    fi
                    if [ ! -f "$SCOPE_FLAG" ]; then
                        enter_doze_scoped_optimizations
                    fi
                fi
                ;;
            *"mi_disp_ioctl_set_doze_brightness"*)
                if [ "$IN_DOZE" = "1" ] && [ "$SCREEN_DOZE" = "1" ]; then
                    handle_kernel_doze_rail_event "$LINE"
                fi
                ;;
        esac
    done
    sleep 3
done
