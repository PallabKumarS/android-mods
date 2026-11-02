#!/system/bin/sh
MODDIR="${0%/*}"
MI_DOZE_NODE="/sys/class/mi_display/disp-DSI-0/doze_brightness"
PID_FILE="$MODDIR/daemon.pid"

echo "=== AOD Power Optimizer & Dimmer v2.1.0 (Strictly AOD-Only) ==="
echo ""

DAEMON_STATE="Stopped"
if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ] && kill -0 "$DPID" 2>/dev/null; then
        DAEMON_STATE="Running (PID $DPID)"
    fi
fi

SCOPE_STATE="Inactive (Awake / Stock Untouched)"
[ -f "$MODDIR/.doze_scope_active" ] && SCOPE_STATE="Active (Dozing)"

echo "Current Status:"
echo "- Doze Event Daemon: $DAEMON_STATE"
echo "- AOD Scoped Session: $SCOPE_STATE"
echo "- System AOD Brightness (Secure): $(settings get secure doze_brightness 2>/dev/null)%"
if [ -r "$MI_DOZE_NODE" ]; then
    echo "- AMOLED DDIC Doze Rail: $(cat "$MI_DOZE_NODE" 2>/dev/null) (0=Awake, 1=60-nit HBM, 2=4.5-nit LBM/LP2)"
fi
echo "- Ambient Low-Bit Mode: $(settings get global ambient_low_bit_enabled 2>/dev/null)"
echo "- Proximity Pocket Shutoff: $(settings get secure doze_proximity_sensor_enabled 2>/dev/null)"
echo "- AOD Notification Pulse Wake (doze_enabled): $(settings get secure doze_enabled 2>/dev/null)"
echo "- Lift/Tilt Wake (Global/Secure): $(settings get global ambient_tilt_to_wake 2>/dev/null) / $(settings get secure wake_gesture_enabled 2>/dev/null)"
echo "- Interactive Refresh Rate (Untouched): min=$(settings get system min_refresh_rate 2>/dev/null) peak=$(settings get system peak_refresh_rate 2>/dev/null)"
echo ""

echo "Re-applying AOD-only settings and restarting Doze daemon..."
sh "$MODDIR/apply_settings.sh"
nohup sh "$MODDIR/doze_daemon.sh" </dev/null >/dev/null 2>&1 &
echo "Done."
