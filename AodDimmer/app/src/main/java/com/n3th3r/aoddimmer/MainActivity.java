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
    private SwitchCompat mSwitchEnabled;
    private SeekBar mSeekBarMinNits;
    private TextView mTvMinNits;
    private SeekBar mSeekBarMaxNits;
    private TextView mTvMaxNits;
    private SeekBar mSeekBarDiffThreshold;
    private TextView mTvDiffThreshold;
    private SeekBar mSeekBarThrottle;
    private TextView mTvThrottle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        mSwitchEnabled = findViewById(R.id.switch_enabled);
        mSeekBarMinNits = findViewById(R.id.seekbar_min_nits);
        mTvMinNits = findViewById(R.id.tv_min_nits);
        mSeekBarMaxNits = findViewById(R.id.seekbar_max_nits);
        mTvMaxNits = findViewById(R.id.tv_max_nits);
        mSeekBarDiffThreshold = findViewById(R.id.seekbar_diff_threshold);
        mTvDiffThreshold = findViewById(R.id.tv_diff_threshold);
        mSeekBarThrottle = findViewById(R.id.seekbar_throttle);
        mTvThrottle = findViewById(R.id.tv_throttle_interval);

        // Load saved preferences
        boolean isEnabled = mPrefs.getBoolean("module_enabled", true);
        float minNits = mPrefs.getFloat("min_nits_val", 2.0f);
        int maxNits = mPrefs.getInt("max_nits_val", 30);
        int diffPercent = mPrefs.getInt("sensor_diff_threshold_percent", 20);
        int throttleMs = mPrefs.getInt("sensor_throttle_ms", 2000);

        mSwitchEnabled.setChecked(isEnabled);

        // Min nits: progress 0..19 corresponds to 0.5 .. 10.0 nits
        int minProgress = Math.round((minNits - 0.5f) / 0.5f);
        mSeekBarMinNits.setProgress(Math.max(0, Math.min(19, minProgress)));
        mTvMinNits.setText(String.format("%.1f nits", minNits));

        // Max nits: progress 0..110 corresponds to 10 .. 120 nits
        int maxProgress = maxNits - 10;
        mSeekBarMaxNits.setProgress(Math.max(0, Math.min(110, maxProgress)));
        mTvMaxNits.setText(maxNits + " nits");

        // Diff Threshold: progress 0..45 corresponds to 5% .. 50%
        int diffProgress = diffPercent - 5;
        mSeekBarDiffThreshold.setProgress(Math.max(0, Math.min(45, diffProgress)));
        mTvDiffThreshold.setText(diffPercent + "%");

        // Throttle: progress 0..9 corresponds to 500ms .. 5000ms (500ms steps)
        int throttleProgress = (throttleMs - 500) / 500;
        mSeekBarThrottle.setProgress(Math.max(0, Math.min(9, throttleProgress)));
        mTvThrottle.setText(String.format("%.1fs", throttleMs / 1000.0f));

        mSwitchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("module_enabled", isChecked).apply();
            makeWorldReadable();
            Toast.makeText(this, isChecked ? "AOD Dimmer Enabled" : "AOD Dimmer Disabled", Toast.LENGTH_SHORT).show();
        });

        mSeekBarMinNits.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.5f + progress * 0.5f;
                mTvMinNits.setText(String.format("%.1f nits", val));
                mPrefs.edit().putFloat("min_nits_val", val).apply();
                makeWorldReadable();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mSeekBarMaxNits.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 10 + progress;
                mTvMaxNits.setText(val + " nits");
                mPrefs.edit().putInt("max_nits_val", val).apply();
                makeWorldReadable();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mSeekBarDiffThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 5 + progress;
                mTvDiffThreshold.setText(val + "%");
                mPrefs.edit().putInt("sensor_diff_threshold_percent", val).apply();
                makeWorldReadable();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mSeekBarThrottle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int ms = 500 + progress * 500;
                mTvThrottle.setText(String.format("%.1fs", ms / 1000.0f));
                mPrefs.edit().putInt("sensor_throttle_ms", ms).apply();
                makeWorldReadable();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
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
}
