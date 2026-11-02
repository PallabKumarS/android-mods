#!/system/bin/sh
MODDIR="${0%/*}"
MI_DOZE_NODE="/sys/class/mi_display/disp-DSI-0/doze_brightness"
PID_FILE="$MODDIR/daemon.pid"

echo "=== AOD Power Optimizer & Dimmer v2.2.1 (Strictly AOD-Only) ==="
echo ""

DAEMON_STATE="Stopped"
if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ] && kill -0 "$DPID" 2>/dev/null; then
        DAEMON_STATE="Running (PID $DPID)"
    fi
fi

POCKET_LOCK_STATE="Hidden (120 Hz)"
CALL_POCKET="$(service call pocket 7 2>/dev/null)"
case "$CALL_POCKET" in
    *"00000001 "*) POCKET_LOCK_STATE="Visible (60 Hz Enforced)" ;;
esac

echo "Current Status:"
echo "- Doze Event Daemon: $DAEMON_STATE"
echo "- System AOD Brightness: $(settings get secure doze_brightness 2>/dev/null)%"
if [ -r "$MI_DOZE_NODE" ]; then
    echo "- AMOLED DDIC Doze Rail: $(cat "$MI_DOZE_NODE" 2>/dev/null) (0=Awake, 1=60-nit HBM, 2=4.5-nit LBM/LP2)"
fi
echo "- Ambient Low-Bit Mode: $(settings get global ambient_low_bit_enabled 2>/dev/null)"
echo "- AOSP Pocket Judge: $(settings get system pocket_judge 2>/dev/null) (Overlay state: $POCKET_LOCK_STATE)"
echo "- AOD Pulse Wake Suppression (doze_enabled): $(settings get secure doze_enabled 2>/dev/null)"
echo "- Lift/Pickup Wake Suppression: $(settings get secure doze_pulse_on_pick_up 2>/dev/null)"
echo "- Single-Tap Wake Suppression: $(settings get secure doze_tap_gesture 2>/dev/null) (Double-Tap: $(settings get secure double_tap_to_wake 2>/dev/null))"
echo "- Interactive Refresh Rate: min=$(settings get system min_refresh_rate 2>/dev/null) peak=$(settings get system peak_refresh_rate 2>/dev/null)"
echo "- Pre-Lock Screen Brightness: $([ -f "$MODDIR/.pre_lock_brightness" ] && grep PRE_LOCK_BRT "$MODDIR/.pre_lock_brightness" 2>/dev/null | cut -d\' -f2 || echo "None (Preserving Current)")"
echo "- Network & Radio State: Untouched (device_idle_constants=$(settings get global device_idle_constants 2>/dev/null))"
echo ""

echo "Re-applying AOD-only settings and restarting Doze daemon..."
sh "$MODDIR/apply_settings.sh"
if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ] && kill -0 "$DPID" 2>/dev/null; then
        pkill -P "$DPID" 2>/dev/null
        kill "$DPID" 2>/dev/null
        sleep 1
    fi
fi
nohup sh "$MODDIR/doze_daemon.sh" </dev/null >/dev/null 2>&1 &
echo "Done."
