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

    // Sensor debouncing and smoothing state
    private static long sLastSensorEventTime = 0;
    private static float sLastAcceptedLux = -1.0f;
    private static float sSmoothedLux = -1.0f;

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

        XposedBridge.log(TAG + "Initializing AOD & Auto-Brightness Stabilizer in system_server...");

        // 1. Hook AOSP AutomaticBrightnessController for Anti-Runaway and Anti-Flicker
        try {
            Class<?> abcClass = XposedHelpers.findClass(
                "com.android.server.display.AutomaticBrightnessController",
                lpparam.classLoader
            );

            // A. Sensor Event Filtering: Debounce jitter, EMA smoothing, and upward rate-limiting
            XposedHelpers.findAndHookMethod(
                abcClass,
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
                        float rawLux = (float) param.args[1];

                        boolean antiFlicker = prefs.getBoolean("daytime_anti_flicker_enabled", true);
                        int throttleMs = prefs.getInt("sensor_throttle_ms", 2000);
                        int diffPercent = prefs.getInt("sensor_diff_threshold_percent", 25);
                        float diffThreshold = Math.max(0.05f, diffPercent / 100.0f);
                        boolean smoothing = prefs.getBoolean("lux_smoothing_enabled", true);

                        // Daytime Debouncing / Jitter Suppression
                        if (antiFlicker && sLastSensorEventTime > 0 && (time - sLastSensorEventTime) < throttleMs) {
                            if (sLastAcceptedLux >= 0) {
                                float diffRatio = Math.abs(rawLux - sLastAcceptedLux) / Math.max(1.0f, sLastAcceptedLux);
                                if (diffRatio < diffThreshold) {
                                    // Suppress redundant jitter
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }

                        // Low-pass EMA filter for silky smooth transitions without flickering
                        float effectiveLux = rawLux;
                        if (smoothing) {
                            if (sSmoothedLux < 0) {
                                sSmoothedLux = rawLux;
                            } else {
                                // Slower upward ramp (0.20), faster downward ramp (0.40)
                                float alpha = (rawLux > sSmoothedLux) ? 0.20f : 0.40f;
                                sSmoothedLux = sSmoothedLux + alpha * (rawLux - sSmoothedLux);
                            }
                            effectiveLux = sSmoothedLux;
                        }

                        sLastSensorEventTime = time;
                        sLastAcceptedLux = effectiveLux;
                        param.args[1] = effectiveLux;
                    }
                }
            );

            // B. Anti-Runaway Protection: Cap max brightness when reading in dark environments
            XposedHelpers.findAndHookMethod(
                abcClass,
                "updateAutoBrightness",
                boolean.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true)) {
                            return;
                        }

                        if (!prefs.getBoolean("night_anti_runaway_enabled", true)) {
                            return;
                        }

                        Object thisObj = param.thisObject;
                        float ambientLux = XposedHelpers.getFloatField(thisObj, "mAmbientLux");
                        int luxTrigger = prefs.getInt("night_lux_threshold", 25);

                        // If ambient light is in the dark/night zone (reading comic in dark)
                        if (ambientLux <= (float) luxTrigger) {
                            float maxCap = prefs.getInt("night_max_brightness_percent", 25) / 100.0f;
                            float currentBrightness = XposedHelpers.getFloatField(thisObj, "mScreenAutoBrightness");

                            if (currentBrightness > maxCap) {
                                XposedHelpers.setFloatField(thisObj, "mScreenAutoBrightness", maxCap);
                                try {
                                    XposedHelpers.setFloatField(thisObj, "mRawScreenAutoBrightness", maxCap);
                                } catch (Throwable ignored) {}

                                XposedBridge.log(TAG + "Night Runaway Guard active: clamped brightness "
                                    + currentBrightness + " -> " + maxCap + " (ambient=" + ambientLux + " lux)");
                            }
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Successfully hooked AutomaticBrightnessController for AOSP!");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Could not hook AutomaticBrightnessController: " + t.getMessage());
        }

        // 2. Hook AOSP DozeBrightnessStrategy for Custom ROM AOD Dimming
        try {
            Class<?> dozeStrategyClass = XposedHelpers.findClass(
                "com.android.server.display.brightness.strategy.DozeBrightnessStrategy",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                dozeStrategyClass,
                "updateBrightness",
                "com.android.server.display.brightness.StrategyExecutionRequest",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true) || !prefs.getBoolean("aod_dimmer_enabled", true)) {
                            return;
                        }

                        Object stateObj = param.getResult();
                        if (stateObj == null) {
                            return;
                        }

                        float currentBrightness = XposedHelpers.getFloatField(stateObj, "mBrightness");
                        if (Float.isNaN(currentBrightness) || currentBrightness <= 0f) {
                            return;
                        }

                        float userMinNits = prefs.getFloat("min_nits_val", 2.0f);
                        float userMaxNits = (float) prefs.getInt("max_nits_val", 25);

                        // Normalized fallback ratio: 2 nits ~ 0.015f, 25 nits ~ 0.08f
                        float minCap = Math.max(0.005f, userMinNits / 150.0f);
                        float maxCap = Math.max(minCap, userMaxNits / 120.0f);

                        float targetBrightness = Math.max(minCap, Math.min(maxCap, currentBrightness));
                        XposedHelpers.setFloatField(stateObj, "mBrightness", targetBrightness);

                        XposedBridge.log(TAG + "AOSP Doze Brightness shaped: " + currentBrightness + " -> " + targetBrightness);
                    }
                }
            );

            XposedBridge.log(TAG + "Successfully hooked DozeBrightnessStrategy for AOSP!");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Could not hook DozeBrightnessStrategy: " + t.getMessage());
        }

        // 3. Fallback Hook: HyperOS DozeAutoBrightnessController (Dual-ROM compatibility)
        try {
            Class<?> hyperosDozeClass = XposedHelpers.findClass(
                "com.android.server.display.DozeAutoBrightnessController",
                lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                hyperosDozeClass,
                "handleLightSensorEvent",
                long.class,
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true) || !prefs.getBoolean("aod_dimmer_enabled", true)) {
                            return;
                        }
                        long time = (long) param.args[0];
                        float lux = (float) param.args[1];
                        int throttleMs = prefs.getInt("sensor_throttle_ms", 2000);
                        if (sLastSensorEventTime > 0 && (time - sLastSensorEventTime) < throttleMs) {
                            param.setResult(null);
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                hyperosDozeClass,
                "updateAutoBrightness",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XSharedPreferences prefs = getPrefs();
                        if (!prefs.getBoolean("module_enabled", true) || !prefs.getBoolean("aod_dimmer_enabled", true)) {
                            return;
                        }
                        Object thisObj = param.thisObject;
                        float userMinNits = prefs.getFloat("min_nits_val", 2.0f);
                        float userMaxNits = (float) prefs.getInt("max_nits_val", 25);
                        Object ddc = XposedHelpers.getObjectField(thisObj, "mDisplayDeviceConfig");
                        if (ddc != null) {
                            float minB = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", userMinNits);
                            float maxB = (float) XposedHelpers.callMethod(ddc, "getBrightnessFromNit", userMaxNits);
                            float cur = XposedHelpers.getFloatField(thisObj, "mCurrentBrightness");
                            float target = Math.max(minB, Math.min(maxB, cur));
                            XposedHelpers.setFloatField(thisObj, "mCurrentBrightness", target);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Successfully hooked HyperOS DozeAutoBrightnessController fallback!");
        } catch (Throwable ignored) {}
    }
}
