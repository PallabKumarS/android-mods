package com.n3th3r.intunebypass;

import android.os.Build;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;

public final class IntuneBypassInit implements IXposedHookLoadPackage {
    private static final String TAG = "IntuneBypass";

    public static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) {
            return;
        }
        sanitizeBuildTags();
        hookFileExists();
        ClassLoaderHooker.installGlobalHooks(lpparam.packageName);
        ClassLoaderHooker.hookClassLoader(lpparam.classLoader, lpparam.packageName);
    }

    private static void sanitizeBuildTags() {
        try {
            if (Build.TAGS != null && Build.TAGS.contains("test-keys")) {
                XposedHelpers.setStaticObjectField(Build.class, "TAGS", "release-keys");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookFileExists() {
        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    File file = (File) param.thisObject;
                    if (file == null) {
                        return;
                    }
                    String path = file.getPath();
                    if (isBlockedPath(path)) {
                        param.setResult(Boolean.FALSE);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static boolean isBlockedPath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith("/su")
                || lower.endsWith("/magisk")
                || lower.endsWith("/ksud")
                || lower.endsWith("/busybox")
                || lower.contains("superuser.apk")
                || lower.contains("xposedbridge.jar")
                || lower.contains("lsposed");
    }
}
