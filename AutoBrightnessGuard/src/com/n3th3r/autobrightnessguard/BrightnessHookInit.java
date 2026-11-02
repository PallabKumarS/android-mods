package com.n3th3r.autobrightnessguard;

import android.os.Build;
import android.os.SystemClock;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class BrightnessHookInit implements IXposedHookLoadPackage {
    static final String TAG = "[AutoBrightnessGuard] ";
    private static final long PREF_RELOAD_INTERVAL_MS = 1500L;

    private static XSharedPreferences sPrefs;
    private static long sLastReloadUptimeMs = 0L;

    static synchronized XSharedPreferences getPrefs() {
        long now = SystemClock.uptimeMillis();
        if (sPrefs == null) {
            sPrefs = new XSharedPreferences(PrefKeys.PKG, PrefKeys.PREF_NAME);
            sPrefs.makeWorldReadable();
            sLastReloadUptimeMs = now;
        } else if ((now - sLastReloadUptimeMs) >= PREF_RELOAD_INTERVAL_MS) {
            sPrefs.reload();
            sLastReloadUptimeMs = now;
        }
        return sPrefs;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (!"android".equals(pkg) && !"system".equals(pkg)) {
            return;
        }

        try {
            Class<?> abcClass = XposedHelpers.findClass(
                "com.android.server.display.AutomaticBrightnessController",
                lpparam.classLoader
            );
            SensorFilterHook.install(abcClass);
            NightRunawayHook.install(abcClass);
            XposedBridge.log(
                TAG + "Installed v2.1.0 AutomaticBrightnessController hooks (SDK " + Build.VERSION.SDK_INT + ")"
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook AutomaticBrightnessController: " + t.getMessage());
        }
    }
}
