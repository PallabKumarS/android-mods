package com.n3th3r.passkeyenabler;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CredentialManagerHooks {

    private static final String TAG = "[PasskeyEnabler:CredMan] ";
    public static final String BITWARDEN_KEYWORD = "bitwarden";

    public static void hook(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;

        // 1. Hook RequestDisplayInfo to neutralize apps requesting Google as preferred default
        try {
            Class<?> rdiClass = XposedHelpers.findClass(
                "com.android.credentialmanager.createflow.RequestDisplayInfo",
                cl
            );

            XposedHelpers.findAndHookMethod(
                rdiClass,
                "getAppPreferredDefaultProviderId",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String current = (String) param.getResult();
                        if (current != null && !current.toLowerCase().contains(BITWARDEN_KEYWORD)) {
                            // Suppress 3rd party or browser preference for Google
                            param.setResult(null);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked RequestDisplayInfo.getAppPreferredDefaultProviderId");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking RequestDisplayInfo: " + t.getMessage());
        }

        // 2. Hook CreateFlowUtils.toCreateCredentialUiState to select Bitwarden as primary
        try {
            Class<?> companionClass = XposedHelpers.findClass(
                "com.android.credentialmanager.CreateFlowUtils$Companion",
                cl
            );

            for (Method m : companionClass.getDeclaredMethods()) {
                if ("toCreateCredentialUiState".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            List<?> enabledProviders = (List<?>) param.args[0];
                            if (enabledProviders == null || enabledProviders.isEmpty()) {
                                return;
                            }

                            Object bitwardenProvider = findBitwardenProvider(enabledProviders);
                            if (bitwardenProvider == null) {
                                return;
                            }

                            String bitwardenId = (String) XposedHelpers.callMethod(bitwardenProvider, "getId");
                            if (bitwardenId == null) {
                                return;
                            }

                            // Force Bitwarden as preferred default
                            param.args[2] = bitwardenId;

                            // Ensure Bitwarden is in userSetDefaultProviderIds and remove Google
                            @SuppressWarnings("unchecked")
                            Set<String> userSet = (Set<String>) param.args[3];
                            Set<String> newSet = new HashSet<>(userSet != null ? userSet : new HashSet<String>());
                            newSet.add(bitwardenId);
                            newSet.removeIf(s -> s.contains("google") || s.contains("gms"));
                            param.args[3] = newSet;

                            XposedBridge.log(TAG + "Forced Bitwarden in toCreateCredentialUiState: " + bitwardenId);
                        }
                    });
                    XposedBridge.log(TAG + "Hooked CreateFlowUtils.toCreateCredentialUiState");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking toCreateCredentialUiState: " + t.getMessage());
        }

        // 3. Hook CreateFlowUtils.toActiveEntry to guarantee Bitwarden ActiveEntry
        try {
            Class<?> companionClass = XposedHelpers.findClass(
                "com.android.credentialmanager.CreateFlowUtils$Companion",
                cl
            );
            Class<?> activeEntryClass = XposedHelpers.findClass(
                "com.android.credentialmanager.createflow.ActiveEntry",
                cl
            );

            for (Method m : companionClass.getDeclaredMethods()) {
                if ("toActiveEntry".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // If defaultProvider is not Bitwarden, check primaryProvider (arg 3)
                            Object currentDefault = param.args[0];
                            String defaultId = getProviderId(currentDefault);

                            if (defaultId == null || !defaultId.toLowerCase().contains(BITWARDEN_KEYWORD)) {
                                Object primary = param.args[3];
                                String primaryId = getProviderId(primary);
                                if (primaryId != null && primaryId.toLowerCase().contains(BITWARDEN_KEYWORD)) {
                                    param.args[0] = primary;
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object activeEntry = param.getResult();
                            if (activeEntry == null) {
                                return;
                            }

                            Object activeProvider = XposedHelpers.getObjectField(activeEntry, "activeProvider");
                            String providerId = getProviderId(activeProvider);

                            if (providerId != null && providerId.toLowerCase().contains(BITWARDEN_KEYWORD)) {
                                return; // Already Bitwarden
                            }

                            // Look for Bitwarden in sortedOptions (arg 1) or primary (arg 3)
                            Object primary = param.args[3];
                            Object bitwardenProvider = isBitwarden(primary) ? primary : null;

                            if (bitwardenProvider == null && param.args[1] instanceof List) {
                                List<?> sortedOptions = (List<?>) param.args[1];
                                for (Object optPair : sortedOptions) {
                                    Object prov = XposedHelpers.callMethod(optPair, "b");
                                    if (isBitwarden(prov)) {
                                        bitwardenProvider = prov;
                                        break;
                                    }
                                }
                            }

                            if (bitwardenProvider != null) {
                                List<?> options = (List<?>) XposedHelpers.callMethod(
                                    bitwardenProvider,
                                    "getSortedCreateOptions"
                                );
                                if (options != null && !options.isEmpty()) {
                                    Object firstOption = options.get(0);
                                    Constructor<?>[] ctors = activeEntryClass.getDeclaredConstructors();
                                    if (ctors.length > 0) {
                                        Constructor<?> ctor = ctors[0];
                                        ctor.setAccessible(true);
                                        Object newActiveEntry = ctor.newInstance(bitwardenProvider, firstOption);
                                        param.setResult(newActiveEntry);
                                        XposedBridge.log(TAG + "Replaced ActiveEntry with Bitwarden");
                                    }
                                }
                            }
                        }
                    });
                    XposedBridge.log(TAG + "Hooked CreateFlowUtils.toActiveEntry");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking toActiveEntry: " + t.getMessage());
        }
    }

    private static Object findBitwardenProvider(List<?> providers) {
        for (Object p : providers) {
            if (isBitwarden(p)) {
                return p;
            }
        }
        return null;
    }

    private static boolean isBitwarden(Object provider) {
        String id = getProviderId(provider);
        return id != null && id.toLowerCase().contains(BITWARDEN_KEYWORD);
    }

    private static String getProviderId(Object provider) {
        if (provider == null) {
            return null;
        }
        try {
            return (String) XposedHelpers.callMethod(provider, "getId");
        } catch (Throwable ignored) {
            return null;
        }
    }
}
