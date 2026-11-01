#!/system/bin/sh
exec </dev/null >/dev/null 2>&1
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"
PID_FILE="$MODDIR/daemon.pid"

MI_DOZE_NODE="/sys/class/mi_display/disp-DSI-0/doze_brightness"
BL_NODE="/sys/class/backlight/panel0-backlight/brightness"
CPU_MAX_NODE="/sys/kernel/msm_performance/parameters/cpu_max_freq"
CORE_CTL_NODE="/sys/devices/system/cpu/cpu4/core_ctl/min_cpus"
GPU_MAX_NODE="/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
GPU_AVAIL_NODE="/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies"

DOZE_CPU_CAP_VAL="0:1324800 1:1324800 2:1324800 3:1324800 4:1190400 5:1190400 6:1190400 7:1190400"
AWAKE_CPU_DEFAULT="0:9999999 1:9999999 2:9999999 3:9999999 4:9999999 5:9999999 6:9999999 7:9999999"
DOZE_GPU_CAP_VAL="500000000"

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$OLD_PID" ] && [ "$OLD_PID" != "$$" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        pkill -P "$OLD_PID" 2>/dev/null
        kill "$OLD_PID" 2>/dev/null
        sleep 1
    fi
fi
echo "$$" > "$PID_FILE"

AWAKE_CPU_VAL="$AWAKE_CPU_DEFAULT"
if [ -r "$CPU_MAX_NODE" ]; then
    CUR_CPU="$(cat "$CPU_MAX_NODE" 2>/dev/null)"
    case "$CUR_CPU" in
        *1190400*) AWAKE_CPU_VAL="$AWAKE_CPU_DEFAULT" ;;
        *[0-9]*) AWAKE_CPU_VAL="$CUR_CPU" ;;
    esac
fi

AWAKE_CORE_MIN="0"
if [ -r "$CORE_CTL_NODE" ]; then
    CUR_MIN="$(cat "$CORE_CTL_NODE" 2>/dev/null)"
    [ -n "$CUR_MIN" ] && AWAKE_CORE_MIN="$CUR_MIN"
fi

AWAKE_GPU_VAL="940000000"
if [ -r "$GPU_AVAIL_NODE" ]; then
    FIRST_FREQ="$(awk '{print $1}' "$GPU_AVAIL_NODE" 2>/dev/null)"
    [ -n "$FIRST_FREQ" ] && AWAKE_GPU_VAL="$FIRST_FREQ"
elif [ -r "$GPU_MAX_NODE" ]; then
    CUR_GPU="$(cat "$GPU_MAX_NODE" 2>/dev/null)"
    if [ -n "$CUR_GPU" ] && [ "$CUR_GPU" != "$DOZE_GPU_CAP_VAL" ]; then
        AWAKE_GPU_VAL="$CUR_GPU"
    fi
fi

load_config() {
    HW_AOD_LBM=1
    AOD_RAIL_MODE=auto
    AOD_BRIGHTNESS_OVERRIDE=0
    AOD_CUSTOM_BRIGHTNESS=1
    DOZE_CPU_CAP=1
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
                ''|*[!0-9]*) EFF_BRT=1 ;;
            esac
            if [ "$EFF_BRT" -le 15 ]; then
                echo "2"
            else
                echo "1"
            fi
            ;;
    esac
}

apply_doze_cpu_cap() {
    load_config
    if [ "$DOZE_CPU_CAP" != "1" ]; then
        return
    fi
    [ -w "$CPU_MAX_NODE" ] && echo "$DOZE_CPU_CAP_VAL" > "$CPU_MAX_NODE" 2>/dev/null
    [ -w "$CORE_CTL_NODE" ] && echo "0" > "$CORE_CTL_NODE" 2>/dev/null
    [ -w "$GPU_MAX_NODE" ] && echo "$DOZE_GPU_CAP_VAL" > "$GPU_MAX_NODE" 2>/dev/null
}

apply_doze_panel_rail() {
    load_config
    if [ "$HW_AOD_LBM" != "1" ] || [ ! -w "$MI_DOZE_NODE" ]; then
        return
    fi
    if [ -r "$BL_NODE" ]; then
        CUR_BL="$(cat "$BL_NODE" 2>/dev/null)"
        if [ "$CUR_BL" = "0" ]; then
            return
        fi
    fi
    TARGET_RAIL="$(resolve_target_rail)"
    CUR_RAIL="$(cat "$MI_DOZE_NODE" 2>/dev/null)"
    if [ "$CUR_RAIL" != "$TARGET_RAIL" ]; then
        echo "$TARGET_RAIL" > "$MI_DOZE_NODE" 2>/dev/null
    fi
}

restore_awake_state() {
    [ -w "$CPU_MAX_NODE" ] && echo "$AWAKE_CPU_VAL" > "$CPU_MAX_NODE" 2>/dev/null
    [ -w "$CORE_CTL_NODE" ] && echo "$AWAKE_CORE_MIN" > "$CORE_CTL_NODE" 2>/dev/null
    [ -w "$GPU_MAX_NODE" ] && echo "$AWAKE_GPU_VAL" > "$GPU_MAX_NODE" 2>/dev/null
    if [ -w "$MI_DOZE_NODE" ]; then
        CUR_RAIL="$(cat "$MI_DOZE_NODE" 2>/dev/null)"
        if [ -n "$CUR_RAIL" ] && [ "$CUR_RAIL" != "0" ]; then
            echo "0" > "$MI_DOZE_NODE" 2>/dev/null
        fi
    fi
}

cleanup_daemon() {
    pkill -P "$$" 2>/dev/null
    restore_awake_state
    rm -f "$PID_FILE"
    exit 0
}

trap cleanup_daemon INT TERM EXIT

IN_DOZE=0
SCREEN_DOZE=0

if dumpsys power 2>/dev/null | grep -q "mWakefulness=Dozing"; then
    IN_DOZE=1
    apply_doze_cpu_cap
    if dumpsys display 2>/dev/null | grep -qE "mScreenState=DOZE|Display State: DOZE"; then
        SCREEN_DOZE=1
        apply_doze_panel_rail
    fi
fi

while true; do
    logcat -b system -v brief -T 1 -s PowerManagerService:I DreamManagerService:D LocalDisplayAdapter:I 2>/dev/null | while IFS= read -r LINE; do
        case "$LINE" in
            *"PowerManagerService"*": Dozing..."*)
                IN_DOZE=1
                SCREEN_DOZE=0
                apply_doze_cpu_cap
                ;;
            *"PowerManagerService"*": Waking up"*)
                IN_DOZE=0
                SCREEN_DOZE=0
                restore_awake_state
                ;;
            *"DreamManagerService"*"screenState=DOZE"*)
                PREV_SCREEN_DOZE="$SCREEN_DOZE"
                IN_DOZE=1
                SCREEN_DOZE=1
                if [ "$PREV_SCREEN_DOZE" = "1" ]; then
                    apply_doze_panel_rail
                fi
                ;;
            *"DreamManagerService"*"screenState=ON"*)
                SCREEN_DOZE=0
                ;;
            *"LocalDisplayAdapter"*"SF.setDisplayPowerMode"*)
                if [ "$IN_DOZE" = "1" ] && [ "$SCREEN_DOZE" = "1" ]; then
                    apply_doze_panel_rail
                fi
                ;;
        esac
    done
    sleep 3
done
