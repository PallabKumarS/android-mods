package com.n3th3r.passkeyenabler;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class PasskeyInit implements IXposedHookLoadPackage {

    private static final String TAG = "[PasskeyEnabler] ";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        String pkg = lpparam.packageName;
        if ("android".equals(pkg)) {
            XposedBridge.log(TAG + "Initializing SystemServer CredentialManager hooks...");
            SystemServerHooks.hook(lpparam);
        } else if ("com.android.settings".equals(pkg)) {
            XposedBridge.log(TAG + "Initializing Settings CredentialProvider hooks...");
            SettingsHooks.hook(lpparam);
        } else if ("com.android.chrome".equals(pkg)) {
            XposedBridge.log(TAG + "Initializing Chrome ThirdPartyAutofill hooks...");
            ChromeHooks.hook(lpparam);
        } else if ("com.android.credentialmanager".equals(pkg)) {
            XposedBridge.log(TAG + "Initializing CredentialManager UI hooks...");
            CredentialManagerHooks.hook(lpparam);
        }
    }
}
