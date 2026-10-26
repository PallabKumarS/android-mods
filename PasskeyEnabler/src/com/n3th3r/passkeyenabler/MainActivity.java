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
    private TextView mTvCurrentActive;
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
        mTvCurrentActive = findViewById(R.id.tv_current_active);

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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(32, 28, 32, 28);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setClickable(true);

        ImageView ivIcon = new ImageView(this);
        int iconSize = (int) (44 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginEnd(24);
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
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.WHITE);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(item.packageName);
        tvSub.setTextSize(12);
        tvSub.setTextColor(Color.parseColor("#8E8E93"));
        textCol.addView(tvSub);

        row.addView(textCol);

        RadioButton rb = new RadioButton(this);
        rb.setClickable(false);
        rb.setFocusable(false);
        row.addView(rb);

        item.radioButton = rb;
        item.rowView = row;

        row.setOnClickListener(v -> selectProvider(item));

        mListContainer.addView(row);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#1FFFFFFF"));
        mListContainer.addView(divider);
    }

    private void selectProvider(ProviderItem selected) {
        mActivePackage = selected.packageName;
        refreshSelectionState();

        StringBuilder sb = new StringBuilder();
        sb.append("setprop ").append(PROP_PROVIDER).append(" ").append(selected.packageName).append("\n");
        sb.append("setprop ").append(PROP_SERVICE).append(" ").append(selected.serviceComponent).append("\n");
        sb.append("settings put secure credential_service_primary ").append(selected.serviceComponent).append("\n");
        sb.append("settings put secure credential_service ").append(selected.serviceComponent).append("\n");

        if (selected.autofillComponent != null && !selected.autofillComponent.isEmpty()) {
            sb.append("settings put secure autofill_service ").append(selected.autofillComponent).append("\n");
        }

        if (!"com.google.android.gms".equals(selected.packageName)) {
            sb.append("am force-stop com.android.chrome\n");
            sb.append("sed -i 's/name=\"Chrome.AutofillThirdPartyMode.State\" value=\"false\"/name=\"Chrome.AutofillThirdPartyMode.State\" value=\"true\"/g' /data/data/com.android.chrome/shared_prefs/com.android.chrome_preferences.xml\n");
        }

        // Update persistent boot service
        sb.append("sed -i 's|^settings put secure credential_service_primary .*|settings put secure credential_service_primary ").append(selected.serviceComponent).append("|g' /data/adb/service.d/passkey_enabler.sh 2>/dev/null\n");

        executeRootCommand(sb.toString());

        Toast.makeText(this, "Active Passkey Manager: " + selected.label, Toast.LENGTH_SHORT).show();
    }

    private void refreshSelectionState() {
        for (ProviderItem item : mProviders) {
            boolean isSelected = item.packageName.equals(mActivePackage);
            if (item.radioButton != null) {
                item.radioButton.setChecked(isSelected);
            }
            if (isSelected) {
                mTvCurrentActive.setText("Active Provider: " + item.label + "\n(" + item.serviceComponent + ")");
            }
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
