#!/system/bin/sh
MODDIR="${0%/*}"
CONFIG_FILE="$MODDIR/config.sh"

echo "=== AOD & Deep Sleep Power Optimizer ==="
echo ""

echo "Current System Status:"
echo "- ambient_low_bit_enabled: $(settings get global ambient_low_bit_enabled 2>/dev/null)"
echo "- doze_proximity_sensor_enabled: $(settings get secure doze_proximity_sensor_enabled 2>/dev/null)"
echo "- min_refresh_rate: $(settings get system min_refresh_rate 2>/dev/null)"
echo "- wifi_scan_always_enabled: $(settings get global wifi_scan_always_enabled 2>/dev/null)"
echo "- ble_scan_always_enabled: $(settings get global ble_scan_always_enabled 2>/dev/null)"
echo ""

echo "Re-applying configured optimizations..."
sh "$MODDIR/service.sh"
echo "Optimizations successfully applied."
