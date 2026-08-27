SKIPUNZIP=0

ui_print "************************************************"
ui_print "*  AOD Dimmer Meta Module (MIUI / HyperOS)      *"
ui_print "*    Target: /system_ext/framework/            *"
ui_print "************************************************"

ui_print "- Configuring Meta Module with skip_mount..."
touch "$MODPATH/skip_mount"

ui_print "- Setting permissions on scripts..."
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755

if [ -f "$MODPATH/system_ext/framework/miui-services.jar" ]; then
    ui_print "- Custom miui-services.jar detected for /system_ext bind-mount."
    set_perm "$MODPATH/system_ext/framework/miui-services.jar" 0 0 0644
fi

ui_print "- Meta Module setup complete."
