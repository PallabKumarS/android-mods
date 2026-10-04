#!/system/bin/sh
# AOSP No Keyboard Chin v1.0
# Enforces navbar_ime_space=0 on boot to eliminate keyboard bottom chin and hide arrow.

MODDIR="${0%/*}"

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 1
done

settings put secure navbar_ime_space 0
