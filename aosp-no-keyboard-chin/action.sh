#!/system/bin/sh
# KernelSU action script to toggle keyboard chin on/off

CURRENT="$(settings get secure navbar_ime_space)"

if [ "$CURRENT" = "0" ]; then
    settings put secure navbar_ime_space 1
    echo "Keyboard chin restored (navbar_ime_space=1)"
else
    settings put secure navbar_ime_space 0
    echo "Keyboard chin removed (navbar_ime_space=0)"
fi
