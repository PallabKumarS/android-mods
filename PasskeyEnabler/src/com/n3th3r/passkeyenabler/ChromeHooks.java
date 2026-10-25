package com.n3th3r.passkeyenabler;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class ChromeHooks {

    private static final String TAG = "[PasskeyEnabler:Chrome] ";
    private static final String TARGET_PREF = "Chrome.AutofillThirdPartyMode.State";

    public static void hook(LoadPackageParam lpparam) {
        try {
            Class<?> spImplClass = XposedHelpers.findClass(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                spImplClass,
                "getBoolean",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (TARGET_PREF.equals(key)) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked SharedPreferencesImpl.getBoolean for " + TARGET_PREF);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking Chrome SharedPreferences: " + t.getMessage());
        }
    }
}
