#!/system/bin/sh
exec </dev/null >/dev/null 2>&1
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"
PID_FILE="$MODDIR/daemon.pid"

POCKET_FLAG="$MODDIR/.pocket_60hz_active"
POCKET_SNAP="$MODDIR/.pocket_fps_snapshot"
POCKET_WATCH_PID_FILE="$MODDIR/.pocket_watcher.pid"
PRE_LOCK_SNAP="$MODDIR/.pre_lock_brightness"
MI_DOZE_NODE="/sys/class/mi_display/disp-DSI-0/doze_brightness"

for P in $(pgrep -f "$MODDIR/doze_daemon.sh" 2>/dev/null); do
    if [ "$P" != "$$" ]; then
        pkill -P "$P" 2>/dev/null
        kill -9 "$P" 2>/dev/null
    fi
done
echo "$$" > "$PID_FILE"

load_config() {
    HW_AOD_LBM=1
    AOD_RAIL_MODE=lbm
    AOD_BRIGHTNESS_OVERRIDE=0
    AOD_CUSTOM_BRIGHTNESS=1
    POCKET_MODE_60HZ=1
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
                *"doze_brightness = 1"*|*"doze_brightness_high"*)
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

enter_pocket_60hz() {
    if [ ! -f "$POCKET_FLAG" ]; then
        CUR_PEAK="$(settings get system peak_refresh_rate 2>/dev/null)"
        CUR_MIN="$(settings get system min_refresh_rate 2>/dev/null)"
        EXTREME="$(settings get system extreme_refresh_rate 2>/dev/null)"
        if [ "$EXTREME" = "1" ]; then
            [ "$CUR_PEAK" = "60.0" ] || [ -z "$CUR_PEAK" ] || [ "$CUR_PEAK" = "null" ] && CUR_PEAK="120.0"
            [ "$CUR_MIN" = "60.0" ] || [ -z "$CUR_MIN" ] || [ "$CUR_MIN" = "null" ] && CUR_MIN="120.0"
        fi
        cat << EOF > "$POCKET_SNAP"
ORIG_PEAK_FPS='$CUR_PEAK'
ORIG_MIN_FPS='$CUR_MIN'
EOF
        touch "$POCKET_FLAG"
        settings put system peak_refresh_rate 60.0 2>/dev/null
        settings put system min_refresh_rate 60.0 2>/dev/null
    fi
}

exit_pocket_60hz() {
    if [ -f "$POCKET_FLAG" ]; then
        if [ -f "$POCKET_SNAP" ]; then
            . "$POCKET_SNAP"
            if [ -n "$ORIG_PEAK_FPS" ] && [ "$ORIG_PEAK_FPS" != "null" ]; then
                settings put system peak_refresh_rate "$ORIG_PEAK_FPS" 2>/dev/null
            else
                settings put system peak_refresh_rate 120.0 2>/dev/null
            fi
            if [ -n "$ORIG_MIN_FPS" ] && [ "$ORIG_MIN_FPS" != "null" ]; then
                settings put system min_refresh_rate "$ORIG_MIN_FPS" 2>/dev/null
            else
                settings put system min_refresh_rate 120.0 2>/dev/null
            fi
        else
            settings put system peak_refresh_rate 120.0 2>/dev/null
            settings put system min_refresh_rate 120.0 2>/dev/null
        fi
        rm -f "$POCKET_FLAG"
    fi
}

stop_pocket_watcher() {
    if [ -f "$POCKET_WATCH_PID_FILE" ]; then
        WPID="$(cat "$POCKET_WATCH_PID_FILE" 2>/dev/null)"
        if [ -n "$WPID" ]; then
            kill "$WPID" 2>/dev/null
        fi
        rm -f "$POCKET_WATCH_PID_FILE"
    fi
    exit_pocket_60hz
}

start_pocket_watcher() {
    stop_pocket_watcher
    load_config
    [ "$POCKET_MODE_60HZ" != "1" ] && return
    [ "$(settings get system pocket_judge 2>/dev/null)" != "1" ] && return

    (
        COUNT=0
        while [ "$COUNT" -lt 240 ]; do
            COUNT=$((COUNT + 1))
            CALL_RES="$(service call pocket 7 2>/dev/null)"
            case "$CALL_RES" in
                *"00000001 "*)
                    enter_pocket_60hz
                    ;;
                *)
                    [ -f "$POCKET_FLAG" ] && exit_pocket_60hz
                    CHECK_TICK=$((COUNT % 4))
                    if [ "$CHECK_TICK" -eq 0 ]; then
                        DUMP="$(dumpsys pocket 2>/dev/null)"
                        case "$DUMP" in
                            *'"interactive":false'*|*'"proximityRegistered":false'*'"isDeviceInPocket":false'*)
                                exit_pocket_60hz
                                break
                                ;;
                        esac
                    fi
                    ;;
            esac
            sleep 0.25 2>/dev/null || sleep 1
        done
        exit_pocket_60hz
        rm -f "$POCKET_WATCH_PID_FILE"
    ) &
    echo "$!" > "$POCKET_WATCH_PID_FILE"
}

snapshot_pre_lock_brightness() {
    [ -f "$PRE_LOCK_SNAP" ] && return
    CUR_BRT="$(settings get system screen_brightness 2>/dev/null)"
    CUR_ADJ="$(settings get system screen_auto_brightness_adj 2>/dev/null)"
    CUR_FLOAT="$(settings get system screen_brightness_float 2>/dev/null)"
    case "$CUR_BRT" in
        ''|null|*[!0-9]*) return ;;
    esac
    if [ "$CUR_BRT" -gt 5 ]; then
        cat << EOF > "$PRE_LOCK_SNAP"
PRE_LOCK_BRT='$CUR_BRT'
PRE_LOCK_ADJ='$CUR_ADJ'
PRE_LOCK_FLOAT='$CUR_FLOAT'
EOF
    fi
}

restore_pre_lock_brightness() {
    if [ -f "$PRE_LOCK_SNAP" ]; then
        (
            sleep 0.35 2>/dev/null || sleep 1
            if [ -f "$PRE_LOCK_SNAP" ]; then
                . "$PRE_LOCK_SNAP"
                WAKE_BRT="$(settings get system screen_brightness 2>/dev/null)"
                case "$WAKE_BRT" in
                    ''|null|*[!0-9]*) WAKE_BRT=1 ;;
                esac
                if [ "$WAKE_BRT" -le 15 ] && [ -n "$PRE_LOCK_BRT" ] && [ "$PRE_LOCK_BRT" -gt "$WAKE_BRT" ]; then
                    settings put system screen_brightness "$PRE_LOCK_BRT" 2>/dev/null
                    if [ -n "$PRE_LOCK_ADJ" ] && [ "$PRE_LOCK_ADJ" != "null" ]; then
                        settings put system screen_auto_brightness_adj "$PRE_LOCK_ADJ" 2>/dev/null
                    fi
                    if [ -n "$PRE_LOCK_FLOAT" ] && [ "$PRE_LOCK_FLOAT" != "null" ]; then
                        settings put system screen_brightness_float "$PRE_LOCK_FLOAT" 2>/dev/null
                    fi
                fi
                rm -f "$PRE_LOCK_SNAP"
            fi
        ) &
    fi
}

cleanup_daemon() {
    pkill -P "$$" 2>/dev/null
    stop_pocket_watcher
    if [ "$IN_DOZE" = "0" ] && [ -w "$MI_DOZE_NODE" ]; then
        CUR_RAIL="$(cat "$MI_DOZE_NODE" 2>/dev/null)"
        if [ -n "$CUR_RAIL" ] && [ "$CUR_RAIL" != "0" ]; then
            echo "0" > "$MI_DOZE_NODE" 2>/dev/null
        fi
    fi
    rm -f "$PID_FILE"
    exit 0
}

trap cleanup_daemon INT TERM EXIT

IN_DOZE=0
PANEL_OFF=0

if dumpsys power 2>/dev/null | grep -q "mWakefulness=Dozing"; then
    IN_DOZE=1
    apply_doze_panel_rail
else
    start_pocket_watcher
fi

LOG_REGEX='^Dozing\.\.\.|^Waking up from|^Going to sleep|Dream requested to start dozing:.*screenState=|dsi_display_set_power.*Power mode \(|SF\.setDisplayPowerMode|mi_disp_ioctl_set_doze_brightness|doze_brightness_high'

while true; do
    logcat -b system,kernel -v brief -T 1 -e "$LOG_REGEX" 2>/dev/null | while IFS= read -r LINE; do
        case "$LINE" in
            *"PowerManagerService"*": Going to sleep"*|*"PowerManagerService"*": Dozing..."*)
                snapshot_pre_lock_brightness
                stop_pocket_watcher
                IN_DOZE=1
                PANEL_OFF=0
                ;;
            *"PowerManagerService"*": Waking up"*)
                IN_DOZE=0
                PANEL_OFF=0
                if [ -w "$MI_DOZE_NODE" ]; then
                    echo "0" > "$MI_DOZE_NODE" 2>/dev/null
                fi
                restore_pre_lock_brightness
                start_pocket_watcher
                ;;
            *"DreamManagerService"*"screenState=DOZE"*|*"dsi_display_set_power"*"Power mode (Doze"*)
                IN_DOZE=1
                PANEL_OFF=0
                apply_doze_panel_rail
                ;;
            *"dsi_display_set_power"*"Power mode (Off)"*)
                PANEL_OFF=1
                ;;
            *"dsi_display_set_power"*"Power mode (On)"*)
                PANEL_OFF=0
                if [ "$IN_DOZE" = "0" ] && [ -w "$MI_DOZE_NODE" ]; then
                    echo "0" > "$MI_DOZE_NODE" 2>/dev/null
                fi
                ;;
            *"LocalDisplayAdapter"*"SF.setDisplayPowerMode"*)
                if [ "$IN_DOZE" = "1" ] && [ "$PANEL_OFF" = "0" ]; then
                    apply_doze_panel_rail
                fi
                ;;
            *"mi_disp_ioctl_set_doze_brightness"*|*"doze_brightness_high"*)
                if [ "$IN_DOZE" = "1" ] && [ "$PANEL_OFF" = "0" ]; then
                    handle_kernel_doze_rail_event "$LINE"
                fi
                ;;
        esac
    done
    sleep 3
done
