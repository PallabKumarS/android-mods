package com.n3th3r.autobrightnessguard;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class BrightnessHookInit implements IXposedHookLoadPackage {
    static final String TAG = "[AutoBrightnessGuard] ";
    private static XSharedPreferences sPrefs;

    static synchronized XSharedPreferences getPrefs() {
        if (sPrefs == null) {
            sPrefs = new XSharedPreferences(PrefKeys.PKG, PrefKeys.PREF_NAME);
            sPrefs.makeWorldReadable();
        } else {
            sPrefs.reload();
        }
        return sPrefs;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> abcClass = XposedHelpers.findClass(
                "com.android.server.display.AutomaticBrightnessController",
                lpparam.classLoader
            );
            SensorFilterHook.install(abcClass);
            NightRunawayHook.install(abcClass);
            XposedBridge.log(TAG + "Installed AutomaticBrightnessController hooks in system_server");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook AutomaticBrightnessController: " + t.getMessage());
        }
    }
}
