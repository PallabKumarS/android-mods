package com.n3th3r.passkeyenabler;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SettingsHooks {

    private static final String TAG = "[PasskeyEnabler:Settings] ";

    public static void hook(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        try {
            Class<?> pickerActivity = XposedHelpers.findClass(
                "com.android.settings.applications.credentials.CredentialsPickerActivity",
                cl
            );

            XposedHelpers.findAndHookMethod(
                pickerActivity,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        Intent intent = activity.getIntent();
                        if (intent != null && !intent.hasExtra(":settings:show_fragment")) {
                            intent.putExtra(
                                ":settings:show_fragment",
                                "com.android.settings.applications.credentials.DefaultCombinedPicker"
                            );
                            intent.putExtra("isWorkProfile", false);
                            intent.putExtra("isPrivateSpace", false);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked CredentialsPickerActivity.onCreate");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking CredentialsPickerActivity: " + t.getMessage());
        }
    }
}
