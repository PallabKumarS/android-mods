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
        }
    }

    private void writeDefaultPreferences() {
        mPrefs.edit()
            .putBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEF_MODULE_ENABLED)
            .putBoolean(PrefKeys.KEY_NIGHT_GUARD_ENABLED, PrefKeys.DEF_NIGHT_GUARD_ENABLED)
            .putInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, PrefKeys.DEF_NIGHT_MAX_PERCENT)
            .putInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD)
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
            Toast.makeText(this, "Restored recommended defaults", Toast.LENGTH_SHORT).show();
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
        mSeekBarNightMaxCap.setProgress(clamp(nightMaxCap - 10, 0, 40));
        mTvNightMaxCap.setText(String.format(Locale.US, "%d%%", nightMaxCap));

        int nightLux = mPrefs.getInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, PrefKeys.DEF_NIGHT_LUX_THRESHOLD);
        mSeekBarNightLuxThreshold.setProgress(clamp(nightLux - 5, 0, 55));
        mTvNightLuxThreshold.setText(String.format(Locale.US, "%d lux", nightLux));

        mSwitchDaytimeAntiFlicker.setChecked(
            mPrefs.getBoolean(PrefKeys.KEY_DAYTIME_ANTI_FLICKER, PrefKeys.DEF_DAYTIME_ANTI_FLICKER)
        );

        int diffPercent = mPrefs.getInt(PrefKeys.KEY_DIFF_THRESHOLD_PERCENT, PrefKeys.DEF_DIFF_THRESHOLD_PERCENT);
        mSeekBarDiffThreshold.setProgress(clamp(diffPercent - 10, 0, 40));
        mTvDiffThreshold.setText(String.format(Locale.US, "%d%%", diffPercent));

        int throttleMs = mPrefs.getInt(PrefKeys.KEY_THROTTLE_MS, PrefKeys.DEF_THROTTLE_MS);
        mSeekBarThrottle.setProgress(clamp((throttleMs - 500) / 500, 0, 9));
        mTvThrottle.setText(String.format(Locale.US, "%.1fs", throttleMs / 1000.0f));

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
            int val = 10 + progress;
            mTvNightMaxCap.setText(String.format(Locale.US, "%d%%", val));
            mPrefs.edit().putInt(PrefKeys.KEY_NIGHT_MAX_PERCENT, val).apply();
        }));

        mSeekBarNightLuxThreshold.setOnSeekBarChangeListener(onSeek((progress) -> {
            int val = 5 + progress;
            mTvNightLuxThreshold.setText(String.format(Locale.US, "%d lux", val));
            mPrefs.edit().putInt(PrefKeys.KEY_NIGHT_LUX_THRESHOLD, val).apply();
        }));

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
            int ms = 500 + progress * 500;
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
