package com.n3th3r.aoddimmer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "aod_dimmer_prefs";
    private SharedPreferences mPrefs;

    // Master
    private SwitchCompat mSwitchMaster;

    // Night Anti-Runaway
    private SwitchCompat mSwitchNightGuard;
    private SeekBar mSeekBarNightMaxCap;
    private TextView mTvNightMaxCap;
    private SeekBar mSeekBarNightLuxThreshold;
    private TextView mTvNightLuxThreshold;

    // Daytime Anti-Flicker
    private SwitchCompat mSwitchDaytimeAntiFlicker;
    private SeekBar mSeekBarDiffThreshold;
    private TextView mTvDiffThreshold;
    private SeekBar mSeekBarThrottle;
    private TextView mTvThrottle;
    private SwitchCompat mSwitchSmoothing;

    // AOD Dimmer
    private SwitchCompat mSwitchAodEnabled;
    private SeekBar mSeekBarMinNits;
    private TextView mTvMinNits;
    private SeekBar mSeekBarMaxNits;
    private TextView mTvMaxNits;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Bind Views
        mSwitchMaster = findViewById(R.id.switch_enabled);

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

        mSwitchAodEnabled = findViewById(R.id.switch_aod_enabled);
        mSeekBarMinNits = findViewById(R.id.seekbar_min_nits);
        mTvMinNits = findViewById(R.id.tv_min_nits);
        mSeekBarMaxNits = findViewById(R.id.seekbar_max_nits);
        mTvMaxNits = findViewById(R.id.tv_max_nits);

        // Load values
        boolean masterEnabled = mPrefs.getBoolean("module_enabled", true);
        boolean nightGuardEnabled = mPrefs.getBoolean("night_anti_runaway_enabled", true);
        int nightMaxCap = mPrefs.getInt("night_max_brightness_percent", 25);
        int nightLuxThreshold = mPrefs.getInt("night_lux_threshold", 25);

        boolean daytimeAntiFlicker = mPrefs.getBoolean("daytime_anti_flicker_enabled", true);
        int diffPercent = mPrefs.getInt("sensor_diff_threshold_percent", 25);
        int throttleMs = mPrefs.getInt("sensor_throttle_ms", 2000);
        boolean smoothingEnabled = mPrefs.getBoolean("lux_smoothing_enabled", true);

        boolean aodEnabled = mPrefs.getBoolean("aod_dimmer_enabled", true);
        float minNits = mPrefs.getFloat("min_nits_val", 2.0f);
        int maxNits = mPrefs.getInt("max_nits_val", 25);

        // Populate UI
        mSwitchMaster.setChecked(masterEnabled);

        mSwitchNightGuard.setChecked(nightGuardEnabled);
        mSeekBarNightMaxCap.setProgress(Math.max(0, Math.min(40, nightMaxCap - 10)));
        mTvNightMaxCap.setText(nightMaxCap + "%");

        mSeekBarNightLuxThreshold.setProgress(Math.max(0, Math.min(55, nightLuxThreshold - 5)));
        mTvNightLuxThreshold.setText(nightLuxThreshold + " lux");

        mSwitchDaytimeAntiFlicker.setChecked(daytimeAntiFlicker);
        mSeekBarDiffThreshold.setProgress(Math.max(0, Math.min(40, diffPercent - 10)));
        mTvDiffThreshold.setText(diffPercent + "%");

        int throttleProgress = (throttleMs - 500) / 500;
        mSeekBarThrottle.setProgress(Math.max(0, Math.min(9, throttleProgress)));
        mTvThrottle.setText(String.format("%.1fs", throttleMs / 1000.0f));

        mSwitchSmoothing.setChecked(smoothingEnabled);

        mSwitchAodEnabled.setChecked(aodEnabled);
        int minProgress = Math.round((minNits - 0.5f) / 0.5f);
        mSeekBarMinNits.setProgress(Math.max(0, Math.min(19, minProgress)));
        mTvMinNits.setText(String.format("%.1f nits", minNits));

        mSeekBarMaxNits.setProgress(Math.max(0, Math.min(110, maxNits - 10)));
        mTvMaxNits.setText(maxNits + " nits");

        // Listeners
        mSwitchMaster.setOnCheckedChangeListener((btn, isChecked) -> {
            mPrefs.edit().putBoolean("module_enabled", isChecked).apply();
            makeWorldReadable();
            Toast.makeText(this, isChecked ? "Display Stabilizer Enabled" : "Display Stabilizer Disabled", Toast.LENGTH_SHORT).show();
        });

        mSwitchNightGuard.setOnCheckedChangeListener((btn, isChecked) -> {
            mPrefs.edit().putBoolean("night_anti_runaway_enabled", isChecked).apply();
            makeWorldReadable();
        });

        mSeekBarNightMaxCap.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 10 + progress;
                mTvNightMaxCap.setText(val + "%");
                mPrefs.edit().putInt("night_max_brightness_percent", val).apply();
                makeWorldReadable();
            }
        });

        mSeekBarNightLuxThreshold.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 5 + progress;
                mTvNightLuxThreshold.setText(val + " lux");
                mPrefs.edit().putInt("night_lux_threshold", val).apply();
                makeWorldReadable();
            }
        });

        mSwitchDaytimeAntiFlicker.setOnCheckedChangeListener((btn, isChecked) -> {
            mPrefs.edit().putBoolean("daytime_anti_flicker_enabled", isChecked).apply();
            makeWorldReadable();
        });

        mSeekBarDiffThreshold.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 10 + progress;
                mTvDiffThreshold.setText(val + "%");
                mPrefs.edit().putInt("sensor_diff_threshold_percent", val).apply();
                makeWorldReadable();
            }
        });

        mSeekBarThrottle.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int ms = 500 + progress * 500;
                mTvThrottle.setText(String.format("%.1fs", ms / 1000.0f));
                mPrefs.edit().putInt("sensor_throttle_ms", ms).apply();
                makeWorldReadable();
            }
        });

        mSwitchSmoothing.setOnCheckedChangeListener((btn, isChecked) -> {
            mPrefs.edit().putBoolean("lux_smoothing_enabled", isChecked).apply();
            makeWorldReadable();
        });

        mSwitchAodEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            mPrefs.edit().putBoolean("aod_dimmer_enabled", isChecked).apply();
            makeWorldReadable();
        });

        mSeekBarMinNits.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + progress * 0.5f;
                mTvMinNits.setText(String.format("%.1f nits", val));
                mPrefs.edit().putFloat("min_nits_val", val).apply();
                makeWorldReadable();
            }
        });

        mSeekBarMaxNits.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 10 + progress;
                mTvMaxNits.setText(val + " nits");
                mPrefs.edit().putInt("max_nits_val", val).apply();
                makeWorldReadable();
            }
        });

        makeWorldReadable();
    }

    private void makeWorldReadable() {
        try {
            java.io.File prefFile = new java.io.File(
                getApplicationInfo().dataDir, 
                "shared_prefs/" + PREF_NAME + ".xml"
            );
            if (prefFile.exists()) {
                prefFile.setReadable(true, false);
                prefFile.setExecutable(true, false);
            }
        } catch (Exception ignored) {}
    }

    private static abstract class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
