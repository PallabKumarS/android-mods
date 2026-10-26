package com.n3th3r.passkeyenabler;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;

public class ChromeHooks {

    private static final String TAG = "[PasskeyEnabler:Chrome] ";
    private static final String TARGET_PREF = "Chrome.AutofillThirdPartyMode.State";
    public static final String DEFAULT_PROVIDER = "com.x8bit.bitwarden";

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
                            String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                            if (!"com.google.android.gms".equals(activePkg)) {
                                param.setResult(Boolean.TRUE);
                            }
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked SharedPreferencesImpl.getBoolean for " + TARGET_PREF);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking Chrome SharedPreferences: " + t.getMessage());
        }
    }

    private static String getSystemProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Throwable t) {
            return def;
        }
    }
}
