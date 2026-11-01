#!/system/bin/sh
MODDIR="${0%/*}"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

sleep 4

sh "$MODDIR/apply_settings.sh"
nohup sh "$MODDIR/doze_daemon.sh" </dev/null >/dev/null 2>&1 &
