package com.n3th3r.autobrightnessguard;

import android.view.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class NightRunawayHook {
    private static final float HLG_R = 0.5f;
    private static final float HLG_A = 0.17883277f;
    private static final float HLG_B = 0.28466892f;
    private static final float HLG_C = 0.55991073f;
    private static final float OVERRIDE_EPSILON = 0.0002f;

    private final Field mAmbientLuxField;
    private final Field mAmbientLuxValidField;
    private final Field mDisplayStateField;
    private final Field mScreenAutoBrightnessField;
    private final Field mRawScreenAutoBrightnessField;
    private final Field mRangeControllerField;
    private final Field mMapperField;
    private final Field mRangeMinField;
    private final Field mRangeMaxField;

    private volatile boolean mNightClampingActive = false;
    private volatile boolean mManualOverrideActive = false;

    private NightRunawayHook(Class<?> abcClass) {
        mAmbientLuxField = resolveField(abcClass, "mAmbientLux");
        mAmbientLuxValidField = resolveField(abcClass, "mAmbientLuxValid");
        mDisplayStateField = resolveField(abcClass, "mDisplayState");
        mScreenAutoBrightnessField = resolveField(abcClass, "mScreenAutoBrightness");
        mRawScreenAutoBrightnessField = resolveField(abcClass, "mRawScreenAutoBrightness");
        mRangeControllerField = resolveField(abcClass, "mBrightnessRangeController");
        mMapperField = resolveField(abcClass, "mCurrentBrightnessMapper");
        mRangeMinField = resolveField(abcClass, "mScreenBrightnessRangeMinimum");
        mRangeMaxField = resolveField(abcClass, "mScreenBrightnessRangeMaximum");
    }

    static void install(Class<?> abcClass) {
        NightRunawayHook hook = new NightRunawayHook(abcClass);
        hook.installClampHook(abcClass);
        hook.installGetterHook(abcClass);
        hook.installUserSliderHook(abcClass);
        hook.installSensorResetHook(abcClass);
    }

    private void installClampHook(Class<?> abcClass) {
        try {
            // Hooks inside updateAutoBrightness before hysteresis thresholds and mCallbacks.updateBrightness()
            XposedHelpers.findAndHookMethod(abcClass, "clampScreenBrightness", float.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object res = param.getResult();
                    if (!(res instanceof Float)) {
                        return;
                    }
                    float capped = evaluateCappedBrightness(param.thisObject, (Float) res);
                    if (capped < (Float) res) {
                        param.setResult(capped);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(BrightnessHookInit.TAG + "clampScreenBrightness fallback to updateAutoBrightness");
            installLegacyUpdateHook(abcClass);
        }
    }

    private void installLegacyUpdateHook(Class<?> abcClass) {
        XposedHelpers.findAndHookMethod(
            abcClass,
            "updateAutoBrightness",
            boolean.class,
            boolean.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (mScreenAutoBrightnessField == null) {
                        return;
                    }
                    float cur = mScreenAutoBrightnessField.getFloat(param.thisObject);
                    float capped = evaluateCappedBrightness(param.thisObject, cur);
                    if (capped < cur) {
                        mScreenAutoBrightnessField.setFloat(param.thisObject, capped);
                        if (mRawScreenAutoBrightnessField != null) {
                            mRawScreenAutoBrightnessField.setFloat(param.thisObject, capped);
                        }
                    }
                }
            }
        );
    }

    private void installGetterHook(Class<?> abcClass) {
        XC_MethodHook getterHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
                Object res = param.getResult();
                if (!(res instanceof Float)) {
                    return;
                }
                float current = (Float) res;
                float capped = evaluateCappedBrightness(param.thisObject, current);
                if (capped < current) {
                    param.setResult(capped);
                    if (mScreenAutoBrightnessField != null) {
                        mScreenAutoBrightnessField.setFloat(param.thisObject, capped);
                    }
                }
            }
        };

        for (Method method : abcClass.getDeclaredMethods()) {
            if ("getAutomaticScreenBrightness".equals(method.getName())) {
                XposedBridge.hookMethod(method, getterHook);
            }
        }
    }

    private void installUserSliderHook(Class<?> abcClass) {
        try {
            XposedHelpers.findAndHookMethod(
                abcClass,
                "setScreenBrightnessByUser",
                float.class,
                float.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        handleManualUserBrightness(param);
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(BrightnessHookInit.TAG + "setScreenBrightnessByUser hook skipped: " + t.getMessage());
        }
    }

    private void installSensorResetHook(Class<?> abcClass) {
        try {
            XposedHelpers.findAndHookMethod(abcClass, "setLightSensorEnabled", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    boolean enable = (Boolean) param.args[0];
                    if (!enable) {
                        mManualOverrideActive = false;
                        mNightClampingActive = false;
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(BrightnessHookInit.TAG + "setLightSensorEnabled hook skipped: " + t.getMessage());
        }
    }

    private void handleManualUserBrightness(XC_MethodHook.MethodHookParam param) {
        XSharedPreferences prefs = BrightnessHookInit.getPrefs();
        if (!isNightGuardEnabled(prefs)) {
            mManualOverrideActive = false;
            return;
        }

        float lux = (Float) param.args[0];
        float userBrightness = (Float) param.args[1];
        if (Float.isNaN(userBrightness) || userBrightness < 0.0f) {
            return;
        }

        int luxTrigger = prefs.getInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD);
        if (!isWithinNightLuxBand(lux, luxTrigger)) {
            mManualOverrideActive = false;
            return;
        }

        float linearCap = computeLinearCap(param.thisObject, prefs);
        if (userBrightness <= (linearCap + OVERRIDE_EPSILON)) {
            mManualOverrideActive = false;
            return;
        }

        boolean allowOverride = prefs.getBoolean(
            PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE,
            PrefKeys.DEF_ALLOW_MANUAL_OVERRIDE
        );
        if (allowOverride) {
            mManualOverrideActive = true;
        } else {
            mManualOverrideActive = false;
            param.args[1] = linearCap;
        }
    }

    private float evaluateCappedBrightness(Object controller, float brightness) {
        if (Float.isNaN(brightness) || brightness <= 0.0f || mAmbientLuxField == null) {
            return brightness;
        }

        XSharedPreferences prefs = BrightnessHookInit.getPrefs();
        if (!isNightGuardEnabled(prefs) || isDozeDisplayState(controller) || !isAmbientLuxReady(controller)) {
            return brightness;
        }

        float ambientLux;
        try {
            ambientLux = mAmbientLuxField.getFloat(controller);
        } catch (IllegalAccessException e) {
            return brightness;
        }

        int luxTrigger = prefs.getInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD);
        if (!isWithinNightLuxBand(ambientLux, luxTrigger)) {
            mManualOverrideActive = false;
            return brightness;
        }

        if (mManualOverrideActive && prefs.getBoolean(
            PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE,
            PrefKeys.DEF_ALLOW_MANUAL_OVERRIDE
        )) {
            return brightness;
        }

        float linearCap = computeLinearCap(controller, prefs);
        return Math.min(brightness, linearCap);
    }

    // Applies +20% (minimum +5 lux) exit hysteresis so OLED screen glow cannot cause oscillation
    private boolean isWithinNightLuxBand(float ambientLux, int luxTrigger) {
        if (ambientLux < 0.0f) {
            return false;
        }
        float trigger = (float) luxTrigger;
        if (mNightClampingActive) {
            float exitThreshold = trigger + Math.max(5.0f, trigger * 0.20f);
            if (ambientLux <= exitThreshold) {
                return true;
            }
            mNightClampingActive = false;
            return false;
        }
        if (ambientLux <= trigger) {
            mNightClampingActive = true;
            return true;
        }
        return false;
    }

    private float computeLinearCap(Object controller, XSharedPreferences prefs) {
        int maxPercent = prefs.getInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, PrefKeys.DEF_NIGHT_MAX_PERCENT);
        float minBacklight = resolveMinBacklight(controller);
        float normalMaxBacklight = resolveNormalMaxBacklight(controller);
        return gammaPercentToLinear(maxPercent, minBacklight, normalMaxBacklight);
    }

    private static boolean isNightGuardEnabled(XSharedPreferences prefs) {
        return prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED)
            && prefs.getBoolean(PrefKeys.KEY_NIGHT_GUARD_ENABLED, PrefKeys.DEF_NIGHT_GUARD_ENABLED);
    }

    private boolean isAmbientLuxReady(Object controller) {
        if (mAmbientLuxValidField == null) {
            return true;
        }
        try {
            return mAmbientLuxValidField.getBoolean(controller);
        } catch (IllegalAccessException e) {
            return true;
        }
    }

    private boolean isDozeDisplayState(Object controller) {
        if (mDisplayStateField == null) {
            return false;
        }
        try {
            int state = mDisplayStateField.getInt(controller);
            return state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    // Converts Android Quick Settings HLG perceptual percentage into linear hardware float [min..normalMax]
    private static float gammaPercentToLinear(int percent, float minVal, float maxVal) {
        float clamped = Math.max(0.01f, Math.min(1.0f, percent / 100.0f));
        float norm;
        if (clamped <= HLG_R) {
            float ratio = clamped / HLG_R;
            norm = (ratio * ratio) / 12.0f;
        } else {
            norm = ((float) Math.exp((clamped - HLG_C) / HLG_A) + HLG_B) / 12.0f;
        }
        float boundedNorm = Math.max(0.0f, Math.min(1.0f, norm));
        return minVal + boundedNorm * (maxVal - minVal);
    }

    private float resolveMinBacklight(Object controller) {
        if (mRangeControllerField != null) {
            try {
                Object rc = mRangeControllerField.get(controller);
                if (rc != null) {
                    float min = (Float) XposedHelpers.callMethod(rc, "getCurrentBrightnessMin");
                    if (!Float.isNaN(min) && min >= 0.0f && min < 0.5f) {
                        return min;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (mRangeMinField != null) {
            try {
                return Math.max(0.0f, mRangeMinField.getFloat(controller));
            } catch (Throwable ignored) {
            }
        }
        return 0.0f;
    }

    private float resolveNormalMaxBacklight(Object controller) {
        // Android 16 QPR / Android 17 moved normal max brightness to BrightnessRangeController
        if (mRangeControllerField != null) {
            try {
                Object rc = mRangeControllerField.get(controller);
                if (rc != null) {
                    float normalMax = (Float) XposedHelpers.callMethod(rc, "getNormalBrightnessMax");
                    if (!Float.isNaN(normalMax) && normalMax > 0.05f && normalMax <= 1.0f) {
                        return normalMax;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (mMapperField != null) {
            try {
                Object mapper = mMapperField.get(controller);
                if (mapper != null) {
                    float normalMax = (Float) XposedHelpers.callMethod(mapper, "getNormalMaxBrightness");
                    if (!Float.isNaN(normalMax) && normalMax > 0.05f && normalMax <= 1.0f) {
                        return normalMax;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (mRangeMaxField != null) {
            try {
                float rangeMax = mRangeMaxField.getFloat(controller);
                if (!Float.isNaN(rangeMax) && rangeMax > 0.05f) {
                    return Math.min(1.0f, rangeMax);
                }
            } catch (Throwable ignored) {
            }
        }
        return 0.5f;
    }

    static Field resolveField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
