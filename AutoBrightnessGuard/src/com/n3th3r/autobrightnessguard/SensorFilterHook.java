package com.n3th3r.autobrightnessguard;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

final class SensorFilterHook extends XC_MethodHook {
    private static final long WAKE_RESET_GAP_MS = 15_000L;
    private static final float EMA_ALPHA_UP = 0.20f;
    private static final float EMA_ALPHA_DOWN = 0.40f;

    private long mLastEventTimeMs = 0L;
    private float mLastAcceptedLux = -1.0f;
    private float mSmoothedLux = -1.0f;

    static void install(Class<?> abcClass) {
        XposedHelpers.findAndHookMethod(
            abcClass,
            "handleLightSensorEvent",
            long.class,
            float.class,
            new SensorFilterHook()
        );
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        XSharedPreferences prefs = BrightnessHookInit.getPrefs();
        if (!prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED)) {
            return;
        }

        long timeMs = (Long) param.args[0];
        float rawLux = (Float) param.args[1];

        // Reset stale smoothing state after screen-off intervals so wake response is immediate
        if (mLastEventTimeMs <= 0L || timeMs < mLastEventTimeMs || (timeMs - mLastEventTimeMs) > WAKE_RESET_GAP_MS) {
            mSmoothedLux = rawLux;
            mLastAcceptedLux = rawLux;
            mLastEventTimeMs = timeMs;
            return;
        }

        if (shouldSuppressJitter(prefs, timeMs, rawLux)) {
            param.setResult(null);
            return;
        }

        float effectiveLux = applySmoothing(prefs, rawLux);
        mLastEventTimeMs = timeMs;
        mLastAcceptedLux = effectiveLux;
        param.args[1] = effectiveLux;
    }

    private boolean shouldSuppressJitter(XSharedPreferences prefs, long timeMs, float rawLux) {
        boolean antiFlicker = prefs.getBoolean(
            PrefKeys.KEY_DAYTIME_ANTI_FLICKER,
            PrefKeys.DEF_DAYTIME_ANTI_FLICKER
        );
        if (!antiFlicker || mLastAcceptedLux < 0.0f) {
            return false;
        }

        int throttleMs = prefs.getInt(PrefKeys.KEY_THROTTLE_MS, PrefKeys.DEF_THROTTLE_MS);
        if ((timeMs - mLastEventTimeMs) >= throttleMs) {
            return false;
        }

        int diffPercent = prefs.getInt(
            PrefKeys.KEY_DIFF_THRESHOLD_PERCENT,
            PrefKeys.DEF_DIFF_THRESHOLD_PERCENT
        );
        float diffThreshold = Math.max(0.05f, diffPercent / 100.0f);
        float diffRatio = Math.abs(rawLux - mLastAcceptedLux) / Math.max(1.0f, mLastAcceptedLux);
        return diffRatio < diffThreshold;
    }

    private float applySmoothing(XSharedPreferences prefs, float rawLux) {
        boolean smoothing = prefs.getBoolean(PrefKeys.KEY_LUX_SMOOTHING, PrefKeys.DEF_LUX_SMOOTHING);
        if (!smoothing) {
            mSmoothedLux = rawLux;
            return rawLux;
        }

        if (mSmoothedLux < 0.0f) {
            mSmoothedLux = rawLux;
            return rawLux;
        }

        float alpha = (rawLux > mSmoothedLux) ? EMA_ALPHA_UP : EMA_ALPHA_DOWN;
        mSmoothedLux = mSmoothedLux + alpha * (rawLux - mSmoothedLux);
        return mSmoothedLux;
    }
}
