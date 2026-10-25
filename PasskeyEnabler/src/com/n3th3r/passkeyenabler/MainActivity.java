package com.n3th3r.passkeyenabler;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;

public class MainActivity extends Activity {

    private static final String BITWARDEN_CREDENTIAL_SERVICE = "com.x8bit.bitwarden/com.x8bit.bitwarden.Autofill.CredentialProviderService";
    private static final String GOOGLE_CREDENTIAL_SERVICE = "com.google.android.gms/com.google.android.gms.auth.api.credentials.credman.service.PasswordAndPasskeyService";
    private static final String BITWARDEN_AUTOFILL_SERVICE = "com.x8bit.bitwarden/com.x8bit.bitwarden.Autofill.AutofillService";

    private TextView mTvStatus;

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

        mTvStatus = findViewById(R.id.tv_status);
        Button btnSetBitwarden = findViewById(R.id.btn_set_bitwarden);
        Button btnOpenPicker = findViewById(R.id.btn_open_picker);
        Button btnOpenChrome = findViewById(R.id.btn_open_chrome);

        btnSetBitwarden.setOnClickListener(v -> setBitwardenDefaults());
        btnOpenPicker.setOnClickListener(v -> openSystemPicker());
        btnOpenChrome.setOnClickListener(v -> configureChromePasskeys());

        updateStatusView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusView();
    }

    private void updateStatusView() {
        String primary = Settings.Secure.getString(getContentResolver(), "credential_service_primary");
        String enabled = Settings.Secure.getString(getContentResolver(), "credential_service");
        String autofill = Settings.Secure.getString(getContentResolver(), "autofill_service");

        boolean isPrimaryBw = primary != null && primary.contains("com.x8bit.bitwarden");
        boolean isAutofillBw = autofill != null && autofill.contains("com.x8bit.bitwarden");

        StringBuilder sb = new StringBuilder();
        sb.append("Primary Passkey Provider:\n  ")
          .append(isPrimaryBw ? "[ACTIVE] Bitwarden" : (primary != null ? primary : "None"))
          .append("\n\n");

        sb.append("Autofill Service:\n  ")
          .append(isAutofillBw ? "[ACTIVE] Bitwarden" : (autofill != null ? autofill : "None"))
          .append("\n\n");

        sb.append("Enabled Credential Services:\n  ")
          .append(enabled != null ? enabled.replace(":", "\n  ") : "None");

        mTvStatus.setText(sb.toString());
    }

    private void setBitwardenDefaults() {
        String cmd = "settings put secure credential_service " + BITWARDEN_CREDENTIAL_SERVICE + ":" + GOOGLE_CREDENTIAL_SERVICE +
                     " && settings put secure credential_service_primary " + BITWARDEN_CREDENTIAL_SERVICE +
                     " && settings put secure autofill_service " + BITWARDEN_AUTOFILL_SERVICE;
        executeRootCommand(cmd);
        Toast.makeText(this, "Bitwarden set as primary Passkey & Autofill provider", Toast.LENGTH_SHORT).show();
        updateStatusView();
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

    private void configureChromePasskeys() {
        String chromePrefPath = "/data/data/com.android.chrome/shared_prefs/com.android.chrome_preferences.xml";
        String cmd = "am force-stop com.android.chrome && " +
                     "sed -i 's/name=\"Chrome.AutofillThirdPartyMode.State\" value=\"false\"/name=\"Chrome.AutofillThirdPartyMode.State\" value=\"true\"/g' " + chromePrefPath;
        executeRootCommand(cmd);
        Toast.makeText(this, "Chrome configured for third-party Passkeys", Toast.LENGTH_SHORT).show();
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
