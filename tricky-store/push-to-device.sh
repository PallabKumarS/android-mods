#!/bin/sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================================"
echo "  Tricky Store Keybox Deployer (HyperOS / AOSP / Infinity)"
echo "========================================================"

adb devices || exit 1

echo ""
echo "[1] Deploy to Standard Tricky Store Root Module (/data/adb/tricky_store/)"
echo "[2] Deploy to Custom ROM Infinity-X (/sdcard/InfinityResources/)"
echo "[3] Deploy Both"
echo ""
printf "Select option [1-3]: "
read -r CHOICE

case "$CHOICE" in
    1)
        echo "Pushing keybox.xml and target.txt to /data/adb/tricky_store/..."
        adb push "$SCRIPT_DIR/keybox.xml" /data/local/tmp/keybox.xml
        adb push "$SCRIPT_DIR/target.txt" /data/local/tmp/target.txt
        adb shell "su -c 'mkdir -p /data/adb/tricky_store && cp /data/local/tmp/keybox.xml /data/adb/tricky_store/keybox.xml && cp /data/local/tmp/target.txt /data/adb/tricky_store/target.txt && chmod 644 /data/adb/tricky_store/* && rm -f /data/local/tmp/keybox.xml /data/local/tmp/target.txt'"
        ;;
    2)
        echo "Pushing keybox.xml to /sdcard/InfinityResources/..."
        adb shell "mkdir -p /sdcard/InfinityResources"
        adb push "$SCRIPT_DIR/keybox.xml" /sdcard/InfinityResources/keybox.xml
        ;;
    3)
        echo "Pushing to /data/adb/tricky_store/..."
        adb push "$SCRIPT_DIR/keybox.xml" /data/local/tmp/keybox.xml
        adb push "$SCRIPT_DIR/target.txt" /data/local/tmp/target.txt
        adb shell "su -c 'mkdir -p /data/adb/tricky_store && cp /data/local/tmp/keybox.xml /data/adb/tricky_store/keybox.xml && cp /data/local/tmp/target.txt /data/adb/tricky_store/target.txt && chmod 644 /data/adb/tricky_store/* && rm -f /data/local/tmp/keybox.xml /data/local/tmp/target.txt'"
        echo "Pushing to /sdcard/InfinityResources/..."
        adb shell "mkdir -p /sdcard/InfinityResources"
        adb push "$SCRIPT_DIR/keybox.xml" /sdcard/InfinityResources/keybox.xml
        ;;
    *)
        echo "Invalid selection."
        exit 1
        ;;
esac

echo "Deployment finished."
