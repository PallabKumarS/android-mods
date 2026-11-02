package com.n3th3r.autobrightnessguard;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String KEY_SCHEMA_VERSION = "pref_schema_version";
    private static final int CURRENT_SCHEMA_VERSION = 2;

    private SharedPreferences mPrefs;
    private boolean mWorldReadableActive;
    private boolean mUpdatingUi;

    private TextView mTvHookBadge;
    private Switch mSwitchMaster;
    private Switch mSwitchNightGuard;
    private SeekBar mSeekBarNightMaxCap;
    private TextView mTvNightMaxCap;
    private SeekBar mSeekBarNightLuxThreshold;
    private TextView mTvNightLuxThreshold;
    private Switch mSwitchManualOverride;

    private Switch mSwitchDaytimeAntiFlicker;
    private SeekBar mSeekBarDiffThreshold;
    private TextView mTvDiffThreshold;
    private SeekBar mSeekBarThrottle;
    private TextView mTvThrottle;
    private Switch mSwitchSmoothing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPreferences();
        bindViews();
        populateUi();
        attachListeners();
    }

    @SuppressWarnings("deprecation")
    private void initPreferences() {
        try {
            mPrefs = getSharedPreferences(PrefKeys.PREF_NAME, Context.MODE_WORLD_READABLE);
            mWorldReadableActive = true;
        } catch (SecurityException ignored) {
            mPrefs = getSharedPreferences(PrefKeys.PREF_NAME, Context.MODE_PRIVATE);
            mWorldReadableActive = false;
        }

        if (!mPrefs.contains(PrefKeys.KEY_MODULE_ENABLED)) {
            writeDefaultPreferences();
            return;
        }

        migrateV20DefaultsIfNeeded();
    }

    private void migrateV20DefaultsIfNeeded() {
        if (mPrefs.getInt(KEY_SCHEMA_VERSION, 1) >= CURRENT_SCHEMA_VERSION) {
            return;
        }
        SharedPreferences.Editor editor = mPrefs.edit();
        if (!mPrefs.contains(PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE)) {
            editor.putBoolean(PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE, PrefKeys.DEF_ALLOW_MANUAL_OVERRIDE);
        }
        if (mPrefs.getInt(PrefKeys.KEY_THROTTLE_MS, 2000) == 2000) {
            editor.putInt(PrefKeys.KEY_THROTTLE_MS, PrefKeys.DEF_THROTTLE_MS);
        }
        if (mPrefs.getInt(PrefKeys.KEY_DIFF_THRESHOLD_PERCENT, 25) == 25) {
            editor.putInt(PrefKeys.KEY_DIFF_THRESHOLD_PERCENT, PrefKeys.DEF_DIFF_THRESHOLD_PERCENT);
        }
        editor.putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply();
    }

    private void writeDefaultPreferences() {
        mPrefs.edit()
            .putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
            .putBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED)
            .putBoolean(PrefKeys.KEY_NIGHT_GUARD_ENABLED, PrefKeys.DEF_NIGHT_GUARD_ENABLED)
            .putInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, PrefKeys.DEF_NIGHT_MAX_PERCENT)
            .putInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD)
            .putBoolean(PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE, PrefKeys.DEF_ALLOW_MANUAL_OVERRIDE)
            .putBoolean(PrefKeys.KEY_DAYTIME_ANTI_FLICKER, PrefKeys.DEF_DAYTIME_ANTI_FLICKER)
            .putInt(PrefKeys.KEY_DIFF_THRESHOLD_PERCENT, PrefKeys.DEF_DIFF_THRESHOLD_PERCENT)
            .putInt(PrefKeys.KEY_THROTTLE_MS, PrefKeys.DEF_THROTTLE_MS)
            .putBoolean(PrefKeys.KEY_LUX_SMOOTHING, PrefKeys.DEF_LUX_SMOOTHING)
            .apply();
    }

    private void bindViews() {
        mTvHookBadge = findViewById(R.id.tv_hook_badge);
        mSwitchMaster = findViewById(R.id.switch_master);

        mSwitchNightGuard = findViewById(R.id.switch_night_guard);
        mSeekBarNightMaxCap = findViewById(R.id.seekbar_night_max_cap);
        mTvNightMaxCap = findViewById(R.id.tv_night_max_cap);
        mSeekBarNightLuxThreshold = findViewById(R.id.seekbar_night_lux_threshold);
        mTvNightLuxThreshold = findViewById(R.id.tv_night_lux_threshold);
        mSwitchManualOverride = findViewById(R.id.switch_manual_override);

        mSwitchDaytimeAntiFlicker = findViewById(R.id.switch_daytime_anti_flicker);
        mSeekBarDiffThreshold = findViewById(R.id.seekbar_diff_threshold);
        mTvDiffThreshold = findViewById(R.id.tv_diff_threshold);
        mSeekBarThrottle = findViewById(R.id.seekbar_throttle);
        mTvThrottle = findViewById(R.id.tv_throttle_interval);
        mSwitchSmoothing = findViewById(R.id.switch_smoothing);

        Button btnReset = findViewById(R.id.btn_reset_defaults);
        btnReset.setOnClickListener(v -> {
            writeDefaultPreferences();
            populateUi();
            Toast.makeText(this, "Restored recommended v2.1.0 defaults", Toast.LENGTH_SHORT).show();
        });
    }

    private void populateUi() {
        mUpdatingUi = true;

        if (mWorldReadableActive) {
            mTvHookBadge.setText("Active");
            mTvHookBadge.setTextColor(Color.parseColor("#30D158"));
        } else {
            mTvHookBadge.setText("Enable in Vector");
            mTvHookBadge.setTextColor(Color.parseColor("#FF9F0A"));
        }

        mSwitchMaster.setChecked(mPrefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED));
        mSwitchNightGuard.setChecked(mPrefs.getBoolean(PrefKeys.KEY_NIGHT_GUARD_ENABLED, PrefKeys.DEF_NIGHT_GUARD_ENABLED));

        int nightMaxCap = mPrefs.getInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, PrefKeys.DEF_NIGHT_MAX_PERCENT);
        mSeekBarNightMaxCap.setProgress(clamp(nightMaxCap - 5, 0, 45));
        mTvNightMaxCap.setText(String.format(Locale.US, "%d%%", nightMaxCap));

        int nightLux = mPrefs.getInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD);
        mSeekBarNightLuxThreshold.setProgress(clamp(nightLux - 5, 0, 75));
        mTvNightLuxThreshold.setText(String.format(Locale.US, "%d lux", nightLux));

        mSwitchManualOverride.setChecked(
            mPrefs.getBoolean(PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE, PrefKeys.DEF_ALLOW_MANUAL_OVERRIDE)
        );

        mSwitchDaytimeAntiFlicker.setChecked(
            mPrefs.getBoolean(PrefKeys.KEY_DAYTIME_ANTI_FLICKER, PrefKeys.DEF_DAYTIME_ANTI_FLICKER)
        );

        int diffPercent = mPrefs.getInt(PrefKeys.KEY_DIFF_THRESHOLD_PERCENT, PrefKeys.DEF_DIFF_THRESHOLD_PERCENT);
        mSeekBarDiffThreshold.setProgress(clamp(diffPercent - 10, 0, 40));
        mTvDiffThreshold.setText(String.format(Locale.US, "%d%%", diffPercent));

        int throttleMs = mPrefs.getInt(PrefKeys.KEY_THROTTLE_MS, PrefKeys.DEF_THROTTLE_MS);
        int clampedThrottle = clamp((throttleMs - 200) / 200, 0, 9);
        int displayMs = 200 + clampedThrottle * 200;
        mSeekBarThrottle.setProgress(clampedThrottle);
        mTvThrottle.setText(String.format(Locale.US, "%.1fs", displayMs / 1000.0f));

        mSwitchSmoothing.setChecked(mPrefs.getBoolean(PrefKeys.KEY_LUX_SMOOTHING, PrefKeys.DEF_LUX_SMOOTHING));
        mUpdatingUi = false;
    }

    private void attachListeners() {
        mSwitchMaster.setOnCheckedChangeListener((btn, checked) -> {
            if (mUpdatingUi) return;
            mPrefs.edit().putBoolean(PrefKeys.KEY_MODULE_ENABLED, checked).apply();
        });

        mSwitchNightGuard.setOnCheckedChangeListener((btn, checked) -> {
            if (mUpdatingUi) return;
            mPrefs.edit().putBoolean(PrefKeys.KEY_NIGHT_GUARD_ENABLED, checked).apply();
        });

        mSeekBarNightMaxCap.setOnSeekBarChangeListener(onSeek((progress) -> {
            int val = 5 + progress;
            mTvNightMaxCap.setText(String.format(Locale.US, "%d%%", val));
            mPrefs.edit().putInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, val).apply();
        }));

        mSeekBarNightLuxThreshold.setOnSeekBarChangeListener(onSeek((progress) -> {
            int val = 5 + progress;
            mTvNightLuxThreshold.setText(String.format(Locale.US, "%d lux", val));
            mPrefs.edit().putInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, val).apply();
        }));

        mSwitchManualOverride.setOnCheckedChangeListener((btn, checked) -> {
            if (mUpdatingUi) return;
            mPrefs.edit().putBoolean(PrefKeys.KEY_ALLOW_MANUAL_OVERRIDE, checked).apply();
        });

        mSwitchDaytimeAntiFlicker.setOnCheckedChangeListener((btn, checked) -> {
            if (mUpdatingUi) return;
            mPrefs.edit().putBoolean(PrefKeys.KEY_DAYTIME_ANTI_FLICKER, checked).apply();
        });

        mSeekBarDiffThreshold.setOnSeekBarChangeListener(onSeek((progress) -> {
            int val = 10 + progress;
            mTvDiffThreshold.setText(String.format(Locale.US, "%d%%", val));
            mPrefs.edit().putInt(PrefKeys.KEY_DIFF_THRESHOLD_PERCENT, val).apply();
        }));

        mSeekBarThrottle.setOnSeekBarChangeListener(onSeek((progress) -> {
            int ms = 200 + progress * 200;
            mTvThrottle.setText(String.format(Locale.US, "%.1fs", ms / 1000.0f));
            mPrefs.edit().putInt(PrefKeys.KEY_THROTTLE_MS, ms).apply();
        }));

        mSwitchSmoothing.setOnCheckedChangeListener((btn, checked) -> {
            if (mUpdatingUi) return;
            mPrefs.edit().putBoolean(PrefKeys.KEY_LUX_SMOOTHING, checked).apply();
        });
    }

    private SeekBar.OnSeekBarChangeListener onSeek(ProgressConsumer consumer) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mUpdatingUi) return;
                consumer.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private interface ProgressConsumer {
        void accept(int progress);
    }
}
