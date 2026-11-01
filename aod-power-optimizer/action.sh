#!/system/bin/sh
MODDIR="${0%/*}"
MI_DOZE_NODE="/sys/class/mi_display/disp-DSI-0/doze_brightness"
PID_FILE="$MODDIR/daemon.pid"

echo "=== AOD Power Optimizer & Dimmer v2.0.0 ==="
echo ""

DAEMON_STATE="Stopped"
if [ -f "$PID_FILE" ]; then
    DPID="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$DPID" ] && kill -0 "$DPID" 2>/dev/null; then
        DAEMON_STATE="Running (PID $DPID)"
    fi
fi

echo "Current Status:"
echo "- Doze Event Daemon: $DAEMON_STATE"
echo "- System AOD Brightness (Secure): $(settings get secure doze_brightness 2>/dev/null)%"
if [ -r "$MI_DOZE_NODE" ]; then
    echo "- AMOLED DDIC Doze Rail: $(cat "$MI_DOZE_NODE" 2>/dev/null) (0=Off/LP1, 1=HBM, 2=LBM/LP2)"
fi
echo "- Ambient Low-Bit Mode: $(settings get global ambient_low_bit_enabled 2>/dev/null)"
echo "- Proximity Pocket Mode: $(settings get secure doze_proximity_sensor_enabled 2>/dev/null)"
echo "- Min Refresh Rate: $(settings get system min_refresh_rate 2>/dev/null)"
echo "- BLE Low-Power Interval: $(settings get global ble_scan_low_power_interval_ms 2>/dev/null) ms"
echo ""

echo "Re-applying settings and ensuring Doze daemon is active..."
sh "$MODDIR/apply_settings.sh"
nohup sh "$MODDIR/doze_daemon.sh" </dev/null >/dev/null 2>&1 &
echo "Done."
