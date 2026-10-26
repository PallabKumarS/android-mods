package com.n3th3r.passkeyenabler;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    public static final String PROP_PROVIDER = "persist.passkey.provider";
    public static final String PROP_SERVICE = "persist.passkey.service";
    public static final String DEFAULT_PROVIDER = "com.x8bit.bitwarden";

    private LinearLayout mListContainer;
    private ImageView mIvActiveIcon;
    private TextView mTvActiveName;
    private TextView mTvActivePkg;
    private final List<ProviderItem> mProviders = new ArrayList<>();
    private String mActivePackage = "";

    private static class ProviderItem {
        String packageName;
        String serviceComponent;
        String autofillComponent;
        String label;
        Drawable icon;
        RadioButton radioButton;
        View rowView;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && "android.settings.CREDENTIAL_PROVIDER".equals(intent.getAction())) {
            openSystemPicker();
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        mListContainer = findViewById(R.id.ll_providers_list);
        mIvActiveIcon = findViewById(R.id.iv_active_icon);
        mTvActiveName = findViewById(R.id.tv_active_name);
        mTvActivePkg = findViewById(R.id.tv_active_pkg);

        Button btnSettings = findViewById(R.id.btn_open_system_settings);
        btnSettings.setOnClickListener(v -> openSystemPicker());

        loadProviders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSelectionState();
    }

    private void loadProviders() {
        mProviders.clear();
        mListContainer.removeAllViews();

        PackageManager pm = getPackageManager();
        Intent queryIntent = new Intent("android.service.credentials.CredentialProviderService");
        List<ResolveInfo> services = pm.queryIntentServices(queryIntent, PackageManager.MATCH_ALL);

        mActivePackage = getSystemProp(PROP_PROVIDER);
        if (mActivePackage == null || mActivePackage.isEmpty()) {
            String primary = Settings.Secure.getString(getContentResolver(), "credential_service_primary");
            if (primary != null && primary.contains("google")) {
                mActivePackage = "com.google.android.gms";
            } else {
                mActivePackage = DEFAULT_PROVIDER;
            }
        }

        for (ResolveInfo ri : services) {
            if (ri.serviceInfo == null) continue;

            ProviderItem item = new ProviderItem();
            item.packageName = ri.serviceInfo.packageName;
            item.serviceComponent = item.packageName + "/" + ri.serviceInfo.name;
            item.label = ri.loadLabel(pm).toString();
            item.icon = ri.loadIcon(pm);
            item.autofillComponent = findAutofillServiceForPackage(item.packageName);

            createProviderRow(item);
            mProviders.add(item);
        }

        refreshSelectionState();
    }

    private String findAutofillServiceForPackage(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent("android.service.autofill.AutofillService");
        List<ResolveInfo> list = pm.queryIntentServices(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo ri : list) {
            if (ri.serviceInfo != null && pkg.equals(ri.serviceInfo.packageName)) {
                return pkg + "/" + ri.serviceInfo.name;
            }
        }
        return null;
    }

    private void createProviderRow(ProviderItem item) {
        float density = getResources().getDisplayMetrics().density;
        boolean isActive = item.packageName.equals(mActivePackage);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int padH = (int) (18 * density);
        int padV = (int) (16 * density);
        row.setPadding(padH, padV, padH, padV);
        row.setBackgroundResource(isActive ? R.drawable.bg_card_provider_active : R.drawable.bg_card_provider);
        row.setClickable(true);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = (int) (10 * density);
        row.setLayoutParams(rowParams);

        ImageView ivIcon = new ImageView(this);
        int iconSize = (int) (40 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd((int) (16 * density));
        ivIcon.setLayoutParams(iconParams);
        ivIcon.setImageDrawable(item.icon);
        row.addView(ivIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(textParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.label);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvTitle.setTextColor(Color.WHITE);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(item.packageName);
        tvSub.setTextSize(12);
        tvSub.setTextColor(Color.parseColor("#8E8E93"));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subParams.topMargin = (int) (2 * density);
        tvSub.setLayoutParams(subParams);
        textCol.addView(tvSub);

        row.addView(textCol);

        RadioButton rb = new RadioButton(this);
        rb.setClickable(false);
        rb.setFocusable(false);
        rb.setChecked(isActive);
        row.addView(rb);

        item.radioButton = rb;
        item.rowView = row;

        row.setOnClickListener(v -> selectProvider(item));

        mListContainer.addView(row);
    }

    private void selectProvider(ProviderItem selected) {
        mActivePackage = selected.packageName;

        // Instant UI update on UI thread (0ms latency)
        for (ProviderItem item : mProviders) {
            boolean active = item.packageName.equals(mActivePackage);
            if (item.radioButton != null) {
                item.radioButton.setChecked(active);
            }
            if (item.rowView != null) {
                item.rowView.setBackgroundResource(active ? R.drawable.bg_card_provider_active : R.drawable.bg_card_provider);
            }
        }
        updateStatusCard(selected);

        // Dispatch root commands asynchronously
        final String activePkg = selected.packageName;
        final String label = selected.label;
        final String svc = selected.serviceComponent;
        final String autofill = selected.autofillComponent;

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("setprop ").append(PROP_PROVIDER).append(" ").append(activePkg).append("\n");
            sb.append("setprop ").append(PROP_SERVICE).append(" ").append(svc).append("\n");
            sb.append("settings put secure credential_service_primary ").append(svc).append("\n");
            sb.append("settings put secure credential_service ").append(svc).append("\n");

            if (autofill != null && !autofill.isEmpty()) {
                sb.append("settings put secure autofill_service ").append(autofill).append("\n");
            }

            if (!"com.google.android.gms".equals(activePkg)) {
                sb.append("am force-stop com.android.chrome\n");
                sb.append("sed -i 's/name=\"Chrome.AutofillThirdPartyMode.State\" value=\"false\"/name=\"Chrome.AutofillThirdPartyMode.State\" value=\"true\"/g' /data/data/com.android.chrome/shared_prefs/com.android.chrome_preferences.xml 2>/dev/null\n");
            }

            sb.append("sed -i 's|^settings put secure credential_service_primary .*|settings put secure credential_service_primary ").append(svc).append("|g' /data/adb/service.d/passkey_enabler.sh 2>/dev/null\n");

            executeRootCommand(sb.toString());

            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Active: " + label, Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void updateStatusCard(ProviderItem item) {
        if (item == null) return;
        if (mIvActiveIcon != null && item.icon != null) {
            mIvActiveIcon.setImageDrawable(item.icon);
        }
        if (mTvActiveName != null) {
            mTvActiveName.setText(item.label);
        }
        if (mTvActivePkg != null) {
            mTvActivePkg.setText(item.packageName);
        }
    }

    private void refreshSelectionState() {
        ProviderItem activeItem = null;
        for (ProviderItem item : mProviders) {
            boolean isSelected = item.packageName.equals(mActivePackage);
            if (item.radioButton != null) {
                item.radioButton.setChecked(isSelected);
            }
            if (item.rowView != null) {
                item.rowView.setBackgroundResource(isSelected ? R.drawable.bg_card_provider_active : R.drawable.bg_card_provider);
            }
            if (isSelected) {
                activeItem = item;
            }
        }
        if (activeItem != null) {
            updateStatusCard(activeItem);
        }
    }

    private void openSystemPicker() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                "com.android.settings",
                "com.android.settings.applications.credentials.CredentialsPickerActivity"
            ));
            intent.putExtra(":settings:show_fragment", "com.android.settings.applications.credentials.DefaultCombinedPicker");
            intent.putExtra("isWorkProfile", false);
            intent.putExtra("isPrivateSpace", false);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Failed opening picker: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getSystemProp(String key) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", key});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String val = br.readLine();
            br.close();
            p.waitFor();
            return val != null ? val.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
