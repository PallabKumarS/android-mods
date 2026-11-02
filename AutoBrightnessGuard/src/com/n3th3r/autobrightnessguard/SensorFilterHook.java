package com.n3th3r.autobrightnessguard;

import java.lang.reflect.Field;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

final class SensorFilterHook extends XC_MethodHook {
    private static final long WAKE_RESET_GAP_MS = 10_000L;
    private static final float EMA_ALPHA_UP = 0.45f;
    private static final float EMA_ALPHA_DOWN = 0.55f;

    private final Field mAmbientLuxValidField;

    private long mLastAcceptedTimeMs = 0L;
    private float mLastAcceptedLux = -1.0f;
    private float mSmoothedLux = -1.0f;

    private SensorFilterHook(Class<?> abcClass) {
        mAmbientLuxValidField = NightRunawayHook.resolveField(abcClass, "mAmbientLuxValid");
    }

    static void install(Class<?> abcClass) {
        final SensorFilterHook filterHook = new SensorFilterHook(abcClass);
        XposedHelpers.findAndHookMethod(
            abcClass,
            "handleLightSensorEvent",
            long.class,
            float.class,
            filterHook
        );

        try {
            XposedHelpers.findAndHookMethod(abcClass, "setLightSensorEnabled", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(Boolean) param.args[0]) {
                        filterHook.resetState();
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    void resetState() {
        mLastAcceptedTimeMs = 0L;
        mLastAcceptedLux = -1.0f;
        mSmoothedLux = -1.0f;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        XSharedPreferences prefs = BrightnessHookInit.getPrefs();
        if (!prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED)) {
            return;
        }

        long timeMs = (Long) param.args[0];
        float rawLux = (Float) param.args[1];

        // Pass initial wake warmup samples untouched so Android 17 AmbientLightRingBuffer initializes immediately
        if (!isAmbientLuxValid(param.thisObject)
            || mLastAcceptedTimeMs <= 0L
            || timeMs < mLastAcceptedTimeMs
            || (timeMs - mLastAcceptedTimeMs) > WAKE_RESET_GAP_MS) {
            mSmoothedLux = rawLux;
            mLastAcceptedLux = rawLux;
            mLastAcceptedTimeMs = timeMs;
            return;
        }

        // Hold stabilized lux rather than dropping events so AmbientLightRingBuffer never starves on prune()
        if (shouldHoldDeadband(prefs, timeMs, rawLux)) {
            param.args[1] = mLastAcceptedLux;
            return;
        }

        float effectiveLux = applySmoothing(prefs, rawLux);
        mLastAcceptedTimeMs = timeMs;
        mLastAcceptedLux = effectiveLux;
        param.args[1] = effectiveLux;
    }

    private boolean isAmbientLuxValid(Object controller) {
        if (mAmbientLuxValidField == null) {
            return true;
        }
        try {
            return mAmbientLuxValidField.getBoolean(controller);
        } catch (IllegalAccessException e) {
            return true;
        }
    }

    private boolean shouldHoldDeadband(XSharedPreferences prefs, long timeMs, float rawLux) {
        boolean antiFlicker = prefs.getBoolean(
            PrefKeys.KEY_DAYTIME_ANTI_FLICKER,
            PrefKeys.DEF_DAYTIME_ANTI_FLICKER
        );
        if (!antiFlicker || mLastAcceptedLux < 0.0f) {
            return false;
        }

        int throttleMs = prefs.getInt(PrefKeys.KEY_THROTTLE_MS, PrefKeys.DEF_THROTTLE_MS);
        if ((timeMs - mLastAcceptedTimeMs) >= throttleMs) {
            return false;
        }

        int diffPercent = prefs.getInt(
            PrefKeys.KEY_DIFF_THRESHOLD_PERCENT,
            PrefKeys.DEF_DIFF_THRESHOLD_PERCENT
        );
        float diffThreshold = Math.max(0.05f, diffPercent / 100.0f);
        float diffRatio = Math.abs(rawLux - mLastAcceptedLux) / Math.max(2.0f, mLastAcceptedLux);
        return diffRatio < diffThreshold;
    }

    private float applySmoothing(XSharedPreferences prefs, float rawLux) {
        boolean smoothing = prefs.getBoolean(PrefKeys.KEY_LUX_SMOOTHING, PrefKeys.DEF_LUX_SMOOTHING);
        if (!smoothing || mSmoothedLux < 0.0f) {
            mSmoothedLux = rawLux;
            return rawLux;
        }

        float alpha = (rawLux > mSmoothedLux) ? EMA_ALPHA_UP : EMA_ALPHA_DOWN;
        mSmoothedLux = mSmoothedLux + alpha * (rawLux - mSmoothedLux);
        return mSmoothedLux;
    }
}
