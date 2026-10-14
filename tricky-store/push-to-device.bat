@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   Tricky Store Keybox Deployer (HyperOS / AOSP / Infinity)
echo ========================================================

adb devices
if %errorlevel% neq 0 (
    echo Error: ADB not found or device not connected.
    exit /b 1
)

echo.
echo [1] Deploy to Standard Tricky Store Root Module (/data/adb/tricky_store/)
echo [2] Deploy to Custom ROM Infinity-X (/sdcard/InfinityResources/ + Settings)
echo [3] Deploy Both
echo.
set /p CHOICE="Select target [1-3]: "

if "%CHOICE%"=="1" goto ROOT_MODULE
if "%CHOICE%"=="2" goto INFINITY_ROM
if "%CHOICE%"=="3" goto BOTH
goto ROOT_MODULE

:ROOT_MODULE
echo.
echo Pushing keybox.xml and target.txt to /data/adb/tricky_store/...
adb push "%~dp0keybox.xml" /data/local/tmp/keybox.xml
adb push "%~dp0target.txt" /data/local/tmp/target.txt
adb shell "su -c 'mkdir -p /data/adb/tricky_store && cp /data/local/tmp/keybox.xml /data/adb/tricky_store/keybox.xml && cp /data/local/tmp/target.txt /data/adb/tricky_store/target.txt && chmod 644 /data/adb/tricky_store/* && rm -f /data/local/tmp/keybox.xml /data/local/tmp/target.txt'"
echo Done. Please restart Keystore or reboot device.
if "%CHOICE%"=="3" goto INFINITY_ROM
goto END

:INFINITY_ROM
echo.
echo Pushing keybox.xml to /sdcard/InfinityResources/...
adb shell "mkdir -p /sdcard/InfinityResources"
adb push "%~dp0keybox.xml" /sdcard/InfinityResources/keybox.xml
echo Keybox pushed to storage.
goto END

:BOTH
goto ROOT_MODULE

:END
echo.
echo Deployment finished successfully.
pause
