package com.n3th3r.passkeyenabler;

import android.content.ComponentName;
import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SystemServerHooks {

    private static final String TAG = "[PasskeyEnabler:SystemServer] ";
    public static final String BITWARDEN_PKG = "com.x8bit.bitwarden";
    public static final String BITWARDEN_SERVICE = "com.x8bit.bitwarden.Autofill.CredentialProviderService";

    public static void hook(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // 1. Hook CredentialManagerService to force Bitwarden as primary passkey provider
        try {
            Class<?> cmsClass = XposedHelpers.findClass(
                "com.android.server.credentials.CredentialManagerService",
                cl
            );

            XposedHelpers.findAndHookMethod(
                cmsClass,
                "getPrimaryProvidersForUserId",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        @SuppressWarnings("unchecked")
                        Set<ComponentName> original = (Set<ComponentName>) param.getResult();
                        Set<ComponentName> modified = new HashSet<>(original != null ? original : Collections.emptySet());

                        ComponentName bwComponent = new ComponentName(BITWARDEN_PKG, BITWARDEN_SERVICE);
                        modified.add(bwComponent);
                        modified.removeIf(cn -> "com.google.android.gms".equals(cn.getPackageName()));

                        param.setResult(modified);
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked CredentialManagerService.getPrimaryProvidersForUserId");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking getPrimaryProvidersForUserId: " + t.getMessage());
        }

        // 2. Hook CredentialManagerServiceStub to ensure Bitwarden is included in enabled providers
        try {
            Class<?> stubClass = XposedHelpers.findClass(
                "com.android.server.credentials.CredentialManagerService$CredentialManagerServiceStub",
                cl
            );

            XposedHelpers.findAndHookMethod(
                stubClass,
                "getEnabledProvidersForUser",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        @SuppressWarnings("unchecked")
                        Set<ComponentName> original = (Set<ComponentName>) param.getResult();
                        if (original != null) {
                            Set<ComponentName> modified = new HashSet<>(original);
                            modified.add(new ComponentName(BITWARDEN_PKG, BITWARDEN_SERVICE));
                            param.setResult(modified);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked CredentialManagerServiceStub.getEnabledProvidersForUser");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking getEnabledProvidersForUser: " + t.getMessage());
        }

        // 3. Hook ActivityTaskManagerService to redirect android.settings.CREDENTIAL_PROVIDER to CredentialsPickerActivity
        try {
            Class<?> atmsClass = XposedHelpers.findClass(
                "com.android.server.wm.ActivityTaskManagerService",
                cl
            );

            for (java.lang.reflect.Method m : atmsClass.getDeclaredMethods()) {
                if ("startActivityAsUser".equals(m.getName()) || "startActivity".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof Intent) {
                                    Intent intent = (Intent) param.args[i];
                                    if ("android.settings.CREDENTIAL_PROVIDER".equals(intent.getAction())) {
                                        intent.setComponent(new ComponentName(
                                            "com.android.settings",
                                            "com.android.settings.applications.credentials.CredentialsPickerActivity"
                                        ));
                                        intent.putExtra(
                                            ":settings:show_fragment",
                                            "com.android.settings.applications.credentials.DefaultCombinedPicker"
                                        );
                                        intent.putExtra("isWorkProfile", false);
                                        intent.putExtra("isPrivateSpace", false);
                                    }
                                    break;
                                }
                            }
                        }
                    });
                }
            }

            XposedBridge.log(TAG + "Hooked ActivityTaskManagerService for CREDENTIAL_PROVIDER intent redirect");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking ActivityTaskManagerService: " + t.getMessage());
        }
    }
}
