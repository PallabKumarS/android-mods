package com.n3th3r.passkeyenabler;

import android.content.ComponentName;
import android.content.Intent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SystemServerHooks {

    private static final String TAG = "[PasskeyEnabler:SystemServer] ";
    public static final String DEFAULT_PROVIDER = "com.x8bit.bitwarden";
    public static final String DEFAULT_SERVICE = "com.x8bit.bitwarden/com.x8bit.bitwarden.Autofill.CredentialProviderService";

    public static void hook(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // 1. Hook CredentialManagerService to force active provider as primary
        try {
            Class<?> cmsClass = XposedHelpers.findClass(
                "com.android.server.credentials.CredentialManagerService",
                cl
            );

            XposedHelpers.findAndHookMethod(
                cmsClass,
                "getPrimaryProvidersForUserId",
                android.content.Context.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                        if ("com.google.android.gms".equals(activePkg)) {
                            return; // Leave Google default
                        }

                        @SuppressWarnings("unchecked")
                        Set<ComponentName> original = (Set<ComponentName>) param.getResult();
                        Set<ComponentName> modified = new HashSet<>(original != null ? original : Collections.emptySet());

                        String activeSvc = getSystemProp("persist.passkey.service", DEFAULT_SERVICE);
                        ComponentName activeComponent = ComponentName.unflattenFromString(activeSvc);
                        if (activeComponent != null) {
                            modified.add(activeComponent);
                        }
                        modified.removeIf(cn -> "com.google.android.gms".equals(cn.getPackageName()));

                        param.setResult(modified);
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked CredentialManagerService.getPrimaryProvidersForUserId");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking getPrimaryProvidersForUserId: " + t.getMessage());
        }

        // 2. Hook CredentialManagerServiceStub to ensure active provider is enabled
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
                        String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                        if ("com.google.android.gms".equals(activePkg)) {
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Set<ComponentName> original = (Set<ComponentName>) param.getResult();
                        if (original != null) {
                            Set<ComponentName> modified = new HashSet<>(original);
                            String activeSvc = getSystemProp("persist.passkey.service", DEFAULT_SERVICE);
                            ComponentName activeComponent = ComponentName.unflattenFromString(activeSvc);
                            if (activeComponent != null) {
                                modified.add(activeComponent);
                            }
                            param.setResult(modified);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked CredentialManagerServiceStub.getEnabledProvidersForUser");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking getEnabledProvidersForUser: " + t.getMessage());
        }

        // 3. Hook ActivityTaskManagerService to intercept settings intent and Google FIDO hijacking
        try {
            Class<?> atmsClass = XposedHelpers.findClass(
                "com.android.server.wm.ActivityTaskManagerService",
                cl
            );

            for (Method m : atmsClass.getDeclaredMethods()) {
                if ("startActivityAsUser".equals(m.getName()) || "startActivity".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            for (int i = 0; i < param.args.length; i++) {
                                if (param.args[i] instanceof Intent) {
                                    Intent intent = (Intent) param.args[i];
                                    String act = intent.getAction();
                                    ComponentName cmp = intent.getComponent();

                                    // Redirect stripped Settings intent
                                    if ("android.settings.CREDENTIAL_PROVIDER".equals(act)) {
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
                                        break;
                                    }

                                    // Neutralize Google Password Manager / GMS FIDO fallback if non-Google provider active
                                    String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                                    if (!"com.google.android.gms".equals(activePkg)) {
                                        boolean isGmsFido = (cmp != null && "com.google.android.gms".equals(cmp.getPackageName())
                                                && cmp.getClassName().contains("fido.authentication"))
                                                || "com.google.android.gms.auth.api.credentials.FIDO_AUTHENTICATION".equals(act);

                                        if (isGmsFido && "com.x8bit.bitwarden".equals(activePkg)) {
                                            // Redirect Google FIDO to Bitwarden CredentialProviderActivity
                                            intent.setComponent(new ComponentName(
                                                "com.x8bit.bitwarden",
                                                "com.x8bit.bitwarden.CredentialProviderActivity"
                                            ));
                                            intent.setAction("com.x8bit.bitwarden.credentials.ACTION_GET_PASSKEY");
                                            intent.setPackage("com.x8bit.bitwarden");
                                            XposedBridge.log(TAG + "Redirected GMS FIDO fallback to Bitwarden");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }

            XposedBridge.log(TAG + "Hooked ActivityTaskManagerService for intent redirection");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking ActivityTaskManagerService: " + t.getMessage());
        }
    }

    private static String getSystemProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Throwable t) {
            return def;
        }
    }
}
