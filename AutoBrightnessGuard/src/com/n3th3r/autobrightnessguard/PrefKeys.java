package com.n3th3r.autobrightnessguard;

public final class PrefKeys {
    public static final String PKG = "com.n3th3r.autobrightnessguard";
    public static final String PREF_NAME = "auto_brightness_guard_prefs";

    public static final String KEY_MODULE_ENABLED = "module_enabled";
    public static final String KEY_NIGHT_GUARD_ENABLED = "night_anti_runaway_enabled";
    public static final String KEY_NIGHT_MAX_PERCENT = "night_max_brightness_percent";
    public static final String KEY_NIGHT_LUX_THRESHOLD = "night_lux_threshold";
    public static final String KEY_ALLOW_MANUAL_OVERRIDE = "allow_manual_slider_override";

    public static final String KEY_DAYTIME_ANTI_FLICKER = "daytime_anti_flicker_enabled";
    public static final String KEY_DIFF_THRESHOLD_PERCENT = "sensor_diff_threshold_percent";
    public static final String KEY_THROTTLE_MS = "sensor_throttle_ms";
    public static final String KEY_LUX_SMOOTHING = "lux_smoothing_enabled";

    public static final boolean DEF_MODULE_ENABLED = true;
    public static final boolean DEF_NIGHT_GUARD_ENABLED = true;
    public static final int DEF_NIGHT_MAX_PERCENT = 25;
    public static final int DEF_NIGHT_LUX_THRESHOLD = 25;
    public static final boolean DEF_ALLOW_MANUAL_OVERRIDE = true;

    public static final boolean DEF_DAYTIME_ANTI_FLICKER = true;
    public static final int DEF_DIFF_THRESHOLD_PERCENT = 15;
    public static final int DEF_THROTTLE_MS = 800;
    public static final boolean DEF_LUX_SMOOTHING = true;

    private PrefKeys() {}
}
