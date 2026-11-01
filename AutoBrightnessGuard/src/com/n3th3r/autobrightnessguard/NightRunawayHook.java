package com.n3th3r.autobrightnessguard;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

final class NightRunawayHook extends XC_MethodHook {
    private static final float HLG_R = 0.5f;
    private static final float HLG_A = 0.17883277f;
    private static final float HLG_B = 0.28466892f;
    private static final float HLG_C = 0.55991073f;

    private final Field mAmbientLuxField;
    private final Field mScreenAutoBrightnessField;
    private final Field mRawScreenAutoBrightnessField;
    private final Field mRangeMinField;
    private final Field mRangeMaxField;
    private final Field mMapperField;

    private NightRunawayHook(Class<?> abcClass) {
        mAmbientLuxField = resolveField(abcClass, "mAmbientLux");
        mScreenAutoBrightnessField = resolveField(abcClass, "mScreenAutoBrightness");
        mRawScreenAutoBrightnessField = resolveField(abcClass, "mRawScreenAutoBrightness");
        mRangeMinField = resolveField(abcClass, "mScreenBrightnessRangeMinimum");
        mRangeMaxField = resolveField(abcClass, "mScreenBrightnessRangeMaximum");
        mMapperField = resolveField(abcClass, "mCurrentBrightnessMapper");
    }

    static void install(Class<?> abcClass) {
        XposedHelpers.findAndHookMethod(
            abcClass,
            "updateAutoBrightness",
            boolean.class,
            boolean.class,
            new NightRunawayHook(abcClass)
        );
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws IllegalAccessException {
        if (mAmbientLuxField == null || mScreenAutoBrightnessField == null) {
            return;
        }

        XSharedPreferences prefs = BrightnessHookInit.getPrefs();
        if (!prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED)) {
            return;
        }
        if (!prefs.getBoolean(PrefKeys.KEY_NIGHT_GUARD_ENABLED, PrefKeys.DEF_NIGHT_GUARD_ENABLED)) {
            return;
        }

        Object controller = param.thisObject;
        float ambientLux = mAmbientLuxField.getFloat(controller);
        int luxTrigger = prefs.getInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD);
        if (ambientLux < 0.0f || ambientLux > (float) luxTrigger) {
            return;
        }

        float currentBrightness = mScreenAutoBrightnessField.getFloat(controller);
        if (Float.isNaN(currentBrightness) || currentBrightness <= 0.0f) {
            return;
        }

        int maxPercent = prefs.getInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, PrefKeys.DEF_NIGHT_MAX_PERCENT);
        float minBacklight = resolveMinBacklight(controller);
        float normalMaxBacklight = resolveNormalMaxBacklight(controller);
        float linearCap = gammaPercentToLinear(maxPercent, minBacklight, normalMaxBacklight);

        if (currentBrightness > linearCap) {
            mScreenAutoBrightnessField.setFloat(controller, linearCap);
            if (mRawScreenAutoBrightnessField != null) {
                mRawScreenAutoBrightnessField.setFloat(controller, linearCap);
            }
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
        if (mRangeMinField == null) {
            return 0.0f;
        }
        try {
            return Math.max(0.0f, mRangeMinField.getFloat(controller));
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    private float resolveNormalMaxBacklight(Object controller) {
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

    private static Field resolveField(Class<?> cls, String name) {
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
