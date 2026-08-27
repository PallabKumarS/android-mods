package com.n3th3r.aoddimmer;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "[AodDimmer] ";
    private static final String PREF_NAME = "aod_dimmer_prefs";
    private static XSharedPreferences sPrefs;

    // Sensor throttle state
    private static long sLastSensorEventTime = 0;
    private static float sLastSensorLux = -1.0f;

    private static XSharedPreferences getPrefs() {
        if (sPrefs == null) {
            sPrefs = new XSharedPreferences("com.n3th3r.aoddimmer", PREF_NAME);
            sPrefs.makeWorldReadable();
        } else {
            sPrefs.reload();
        }
        return sPrefs;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + "Initializing AOD Dimmer dual-curve hooks in system_server...");

        // 1. Hook DozeAutoBrightnessController
        try {
            Class<?> dozeControllerClass = XposedHelpers.findClass(
                "com.android.server.display.DozeAutoBrightnessController",
                lpparam.classLoader
            );

            // A. Sensor Event Throttling (Save CPU wakeups & battery)
            XposedHelpers.findAndHookMethod(
                dozeControllerClass,
                "handleLightSensorEvent",
                long.class,
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true)) {
                            return;
                        }

                        long time = (long) param.args[0];
                        float lux = (float) param.args[1];
                        int throttleMs = prefs.getInt("sensor_throttle_ms", 2000);

                        // If within throttle window and lux hasn't changed significantly (>20%), suppress event
                        if (sLastSensorEventTime > 0 && (time - sLastSensorEventTime) < throttleMs) {
                            if (sLastSensorLux >= 0) {
                                float diffRatio = Math.abs(lux - sLastSensorLux) / Math.max(1.0f, sLastSensorLux);
                                if (diffRatio < 0.20f) {
                                    // Suppress redundant calculation cycle
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }

                        sLastSensorEventTime = time;
                        sLastSensorLux = lux;
                    }
                }
            );

            // B. Recalculate steady-state brightness according to User Min & Max Nits
            XposedHelpers.findAndHookMethod(
                dozeControllerClass,
                "updateAutoBrightness",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true)) {
                            return;
                        }

                        Object thisObj = param.thisObject;
                        float userMinNits = prefs.getFloat("min_nits_val", 2.0f);
                        float userMaxNits = (float) prefs.getInt("max_nits_val", 30);

                        Object ddc = XposedHelpers.getObjectField(thisObj, "mDisplayDeviceConfig");
                        if (ddc == null) {
                            return;
                        }

                        float currentBrightness = XposedHelpers.getFloatField(thisObj, "mCurrentBrightness");
                        if (Float.isNaN(currentBrightness) || currentBrightness <= 0f) {
                            return;
                        }

                        // Convert target nits to float brightness via panel hardware splines
                        float userMinBrightness = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", userMinNits);
                        float userMaxBrightness = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", userMaxNits);
                        float stockMinBrightness = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", 5.0f);
                        float stockMaxBrightness = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", 60.0f);

                        // Normalize current stock position between 5 nits and 60 nits
                        float t = 0f;
                        if (stockMaxBrightness > stockMinBrightness) {
                            t = (currentBrightness - stockMinBrightness) / (stockMaxBrightness - stockMinBrightness);
                        }

                        float targetBrightness;
                        if (t <= 1.0f) {
                            t = Math.max(0.0f, t);
                            // Smooth interpolation between user min and user max
                            targetBrightness = userMinBrightness + t * (userMaxBrightness - userMinBrightness);
                        } else {
                            // In high outdoor lux (>60 nits), scale proportionally above userMax
                            targetBrightness = userMaxBrightness * (currentBrightness / stockMaxBrightness);
                        }

                        targetBrightness = Math.max(0.0005f, Math.min(1.0f, targetBrightness));

                        // Apply to mCurrentBrightness and mScreenAutoBrightness
                        XposedHelpers.setFloatField(thisObj, "mCurrentBrightness", targetBrightness);

                        Object lock = XposedHelpers.getObjectField(thisObj, "brightnessLock");
                        if (lock != null) {
                            synchronized (lock) {
                                XposedHelpers.setFloatField(thisObj, "mScreenAutoBrightness", targetBrightness);
                            }
                        }

                        XposedBridge.log(TAG + "AOD Dual-Curve mapped: stock=" + currentBrightness 
                            + " -> user=" + targetBrightness 
                            + " [Min: " + userMinNits + " nits, Max: " + userMaxNits + " nits]");
                    }
                }
            );

            XposedBridge.log(TAG + "Successfully hooked DozeAutoBrightnessController!");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking DozeAutoBrightnessController: " + t.getMessage());
        }

        // 2. Hook DozeBrightnessStrategy (Global selector guard)
        try {
            Class<?> strategyClass = XposedHelpers.findClass(
                "com.android.server.display.brightness.strategy.DozeBrightnessStrategy",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                strategyClass,
                "updateBrightness",
                "com.android.server.display.brightness.StrategyExecutionRequest",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true)) {
                            return;
                        }

                        Object stateObj = param.getResult();
                        if (stateObj != null) {
                            float brightness = XposedHelpers.getFloatField(stateObj, "mBrightness");
                            if (!Float.isNaN(brightness) && brightness > 0f) {
                                float userMinNits = prefs.getFloat("min_nits_val", 2.0f);
                                float userMaxNits = (float) prefs.getInt("max_nits_val", 30);
                                
                                // Cap at maximum brightness if it exceeds userMax
                                Object ddc = XposedHelpers.getObjectField(param.thisObject, "mDisplayDeviceConfig");
                                if (ddc != null) {
                                    float maxCap = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", userMaxNits);
                                    if (brightness > maxCap) {
                                        XposedHelpers.setFloatField(stateObj, "mBrightness", maxCap);
                                    }
                                }
                            }
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }
}
