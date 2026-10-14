MODPATH="${0%/*}"
. $MODPATH/common_func.sh

# Magisk Recovery Mode
resetprop_if_match ro.boot.mode recovery unknown
resetprop_if_match ro.bootmode recovery unknown
resetprop_if_match vendor.boot.mode recovery unknown

# SELinux
resetprop_if_diff ro.boot.selinux enforcing
if ! $SKIPDELPROP; then
    delprop_if_exist ro.build.selinux
fi
if [ "$(toybox cat /sys/fs/selinux/enforce 2>/dev/null)" = "0" ]; then
    chmod 640 /sys/fs/selinux/enforce
    chmod 440 /sys/fs/selinux/policy
fi

# Start Tricky Store daemon in background
(
cd "$MODPATH"
while true; do
  ./daemon
  if [ $? -ne 0 ]; then
    exit 1
  fi
  sleep 2
done
) &

# Conditional late sensitive properties
{
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 1
done

# SafetyNet/Play Integrity + OEM
resetprop_if_diff ro.secureboot.lockstate locked
resetprop_if_diff ro.boot.flash.locked 1
resetprop_if_diff ro.boot.realme.lockstate 1
resetprop_if_diff ro.boot.vbmeta.device_state locked
resetprop_if_diff vendor.boot.verifiedbootstate green
resetprop_if_diff ro.boot.verifiedbootstate green
resetprop_if_diff ro.boot.veritymode enforcing
resetprop_if_diff vendor.boot.vbmeta.device_state locked

# Other
resetprop_if_diff sys.oem_unlock_allowed 0

# Compact property area
resetprop --compact >/dev/null 2>&1 || true

}&
