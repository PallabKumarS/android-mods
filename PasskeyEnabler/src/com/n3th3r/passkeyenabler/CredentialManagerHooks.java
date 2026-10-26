package com.n3th3r.passkeyenabler;

import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CredentialManagerHooks {

    private static final String TAG = "[PasskeyEnabler:CredMan] ";
    public static final String DEFAULT_PROVIDER = "com.x8bit.bitwarden";

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
                        String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                        if ("com.google.android.gms".equals(activePkg)) {
                            return;
                        }

                        String current = (String) param.getResult();
                        if (current != null && !current.toLowerCase().contains(activePkg)) {
                            param.setResult(null);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + "Hooked RequestDisplayInfo.getAppPreferredDefaultProviderId");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking RequestDisplayInfo: " + t.getMessage());
        }

        // 2. Hook CreateFlowUtils.toCreateCredentialUiState to select active provider
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
                            String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                            if ("com.google.android.gms".equals(activePkg)) {
                                return;
                            }

                            List<?> enabledProviders = (List<?>) param.args[0];
                            if (enabledProviders == null || enabledProviders.isEmpty()) {
                                return;
                            }

                            Object targetProvider = findProviderByPackage(enabledProviders, activePkg);
                            if (targetProvider == null) {
                                return;
                            }

                            String targetId = (String) XposedHelpers.callMethod(targetProvider, "getId");
                            if (targetId == null) {
                                return;
                            }

                            // Force active provider as preferred default
                            param.args[2] = targetId;

                            // Ensure active provider is in userSetDefaultProviderIds and remove Google
                            @SuppressWarnings("unchecked")
                            Set<String> userSet = (Set<String>) param.args[3];
                            Set<String> newSet = new HashSet<>(userSet != null ? userSet : new HashSet<String>());
                            newSet.add(targetId);
                            newSet.removeIf(s -> s.contains("google") || s.contains("gms"));
                            param.args[3] = newSet;

                            XposedBridge.log(TAG + "Forced active provider in toCreateCredentialUiState: " + targetId);
                        }
                    });
                    XposedBridge.log(TAG + "Hooked CreateFlowUtils.toCreateCredentialUiState");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking toCreateCredentialUiState: " + t.getMessage());
        }

        // 3. Hook CreateFlowUtils.toActiveEntry to guarantee active provider ActiveEntry
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
                            String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                            if ("com.google.android.gms".equals(activePkg)) {
                                return;
                            }

                            Object currentDefault = param.args[0];
                            String defaultId = getProviderId(currentDefault);

                            if (defaultId == null || !defaultId.toLowerCase().contains(activePkg)) {
                                Object primary = param.args[3];
                                String primaryId = getProviderId(primary);
                                if (primaryId != null && primaryId.toLowerCase().contains(activePkg)) {
                                    param.args[0] = primary;
                                }
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                            if ("com.google.android.gms".equals(activePkg)) {
                                return;
                            }

                            Object activeEntry = param.getResult();
                            if (activeEntry == null) {
                                return;
                            }

                            Object activeProvider = XposedHelpers.getObjectField(activeEntry, "activeProvider");
                            String providerId = getProviderId(activeProvider);

                            if (providerId != null && providerId.toLowerCase().contains(activePkg)) {
                                return;
                            }

                            Object primary = param.args[3];
                            Object targetProvider = isTargetPackage(primary, activePkg) ? primary : null;

                            if (targetProvider == null && param.args[1] instanceof List) {
                                List<?> sortedOptions = (List<?>) param.args[1];
                                for (Object optPair : sortedOptions) {
                                    Object prov = XposedHelpers.callMethod(optPair, "b");
                                    if (isTargetPackage(prov, activePkg)) {
                                        targetProvider = prov;
                                        break;
                                    }
                                }
                            }

                            if (targetProvider != null) {
                                List<?> options = (List<?>) XposedHelpers.callMethod(
                                    targetProvider,
                                    "getSortedCreateOptions"
                                );
                                if (options != null && !options.isEmpty()) {
                                    Object firstOption = options.get(0);
                                    Constructor<?>[] ctors = activeEntryClass.getDeclaredConstructors();
                                    if (ctors.length > 0) {
                                        Constructor<?> ctor = ctors[0];
                                        ctor.setAccessible(true);
                                        Object newActiveEntry = ctor.newInstance(targetProvider, firstOption);
                                        param.setResult(newActiveEntry);
                                        XposedBridge.log(TAG + "Replaced ActiveEntry with: " + activePkg);
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

        // 4. Hook ProviderDisplayInfo.getCredentialEntryInfoList to rescue credentials with empty usernames
        try {
            Class<?> pdiClass = XposedHelpers.findClass(
                "com.android.credentialmanager.getflow.ProviderDisplayInfo",
                cl
            );

            XposedHelpers.findAndHookMethod(
                pdiClass,
                "getCredentialEntryInfoList",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        List<?> list = (List<?>) param.getResult();
                        List<Object> allEntries = new ArrayList<>();
                        if (list != null && !list.isEmpty()) {
                            allEntries.addAll(list);
                        } else {
                            Object pdi = param.thisObject;
                            List<?> perUserList = (List<?>) XposedHelpers.callMethod(pdi, "getSortedUserNameToCredentialEntryList");
                            if (perUserList != null && !perUserList.isEmpty()) {
                                for (Object perUser : perUserList) {
                                    List<?> creds = (List<?>) XposedHelpers.callMethod(perUser, "getSortedCredentialEntryList");
                                    if (creds != null) {
                                        allEntries.addAll(creds);
                                    }
                                }
                                if (!allEntries.isEmpty()) {
                                    XposedBridge.log(TAG + "Rescued " + allEntries.size() + " credential entries from perUserList");
                                }
                            }
                        }

                        if (!allEntries.isEmpty()) {
                            sortCredentialEntries(allEntries);
                            param.setResult(allEntries);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + "Hooked ProviderDisplayInfo.getCredentialEntryInfoList");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking ProviderDisplayInfo.getCredentialEntryInfoList: " + t.getMessage());
        }

        // 5. Hook GetMiCredentialComponentsKt.GetMiCredentialScreen to handle locked providers and empty states
        try {
            Class<?> compClass = XposedHelpers.findClass(
                "com.android.credentialmanager.getflow.GetMiCredentialComponentsKt",
                cl
            );

            for (Method m : compClass.getDeclaredMethods()) {
                if ("GetMiCredentialScreen".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object viewModel = param.args[0];
                            Object getUiState = param.args[1];
                            if (viewModel == null || getUiState == null) {
                                return;
                            }

                            extractRequestedUserHint(viewModel, getUiState);

                            Object screenState = XposedHelpers.callMethod(getUiState, "getCurrentScreenState");
                            String stateStr = screenState != null ? screenState.toString() : "";

                            Object pdi = XposedHelpers.callMethod(getUiState, "getProviderDisplayInfo");
                            if (pdi == null) {
                                return;
                            }

                            if ("UNLOCKED_AUTH_ENTRIES_ONLY".equals(stateStr)) {
                                List<?> authList = (List<?>) XposedHelpers.callMethod(pdi, "getAuthenticationEntryList");
                                if (authList != null && !authList.isEmpty()) {
                                    Object authEntry = authList.get(0);
                                    XposedBridge.log(TAG + "GetMiCredentialScreen launching auth entry from UNLOCKED_AUTH_ENTRIES_ONLY: " + authEntry);
                                    XposedHelpers.callMethod(viewModel, "getFlowOnEntrySelected", authEntry, null);
                                }
                            } else if ("PRIMARY_SELECTION".equals(stateStr)) {
                                List<?> credList = (List<?>) XposedHelpers.callMethod(pdi, "getCredentialEntryInfoList");
                                if (credList == null || credList.isEmpty()) {
                                    List<?> authList = (List<?>) XposedHelpers.callMethod(pdi, "getAuthenticationEntryList");
                                    if (authList != null && !authList.isEmpty()) {
                                        Object authEntry = authList.get(0);
                                        XposedBridge.log(TAG + "GetMiCredentialScreen empty PRIMARY_SELECTION launching auth entry: " + authEntry);
                                        XposedHelpers.callMethod(viewModel, "getFlowOnEntrySelected", authEntry, null);
                                    }
                                }
                            }
                        }
                    });
                    XposedBridge.log(TAG + "Hooked GetMiCredentialComponentsKt.GetMiCredentialScreen");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking GetMiCredentialScreen: " + t.getMessage());
        }

        // 6. Hook CredentialSelectorViewModel.shouldCancelCurrentUi to prevent caller from prematurely killing UI
        try {
            Class<?> vmClass = XposedHelpers.findClass(
                "com.android.credentialmanager.CredentialSelectorViewModel",
                cl
            );

            for (Method m : vmClass.getDeclaredMethods()) {
                if ("shouldCancelCurrentUi".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                            if (!"com.google.android.gms".equals(activePkg)) {
                                param.setResult(Boolean.FALSE);
                            }
                        }
                    });
                    XposedBridge.log(TAG + "Hooked CredentialSelectorViewModel.shouldCancelCurrentUi");
                    break;
                }
            }

            // 7. Hook CredentialSelectorViewModel.onIllegalUiState to rescue aborted flows
            for (Method m : vmClass.getDeclaredMethods()) {
                if ("onIllegalUiState".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String activePkg = getSystemProp("persist.passkey.provider", DEFAULT_PROVIDER);
                            if ("com.google.android.gms".equals(activePkg)) {
                                return;
                            }

                            String reason = (String) param.args[0];
                            XposedBridge.log(TAG + "Intercepted onIllegalUiState: " + reason);

                            param.setResult(null);

                            Object viewModel = param.thisObject;
                            try {
                                Object uiState = XposedHelpers.callMethod(viewModel, "getUiState");
                                if (uiState != null) {
                                    Object getUiState = XposedHelpers.callMethod(uiState, "getGetCredentialUiState");
                                    if (getUiState != null) {
                                        Object pdi = XposedHelpers.callMethod(getUiState, "getProviderDisplayInfo");
                                        if (pdi != null) {
                                            List<?> authList = (List<?>) XposedHelpers.callMethod(pdi, "getAuthenticationEntryList");
                                            if (authList != null && !authList.isEmpty()) {
                                                Object authEntry = authList.get(0);
                                                XposedBridge.log(TAG + "Auto-launching auth entry from onIllegalUiState: " + authEntry);
                                                XposedHelpers.callMethod(viewModel, "getFlowOnEntrySelected", authEntry, null);
                                                return;
                                            }

                                            List<?> perUserList = (List<?>) XposedHelpers.callMethod(pdi, "getSortedUserNameToCredentialEntryList");
                                            if (perUserList != null && !perUserList.isEmpty()) {
                                                for (Object perUser : perUserList) {
                                                    List<?> credEntries = (List<?>) XposedHelpers.callMethod(perUser, "getSortedCredentialEntryList");
                                                    if (credEntries != null && !credEntries.isEmpty()) {
                                                        Object credEntry = credEntries.get(0);
                                                        XposedBridge.log(TAG + "Auto-selecting credential entry from onIllegalUiState: " + credEntry);
                                                        XposedHelpers.callMethod(viewModel, "getFlowOnEntrySelected", credEntry, null);
                                                        return;
                                                    }
                                                }
                                            }

                                            Object remoteEntry = XposedHelpers.callMethod(pdi, "getRemoteEntry");
                                            if (remoteEntry != null) {
                                                XposedBridge.log(TAG + "Auto-selecting remote entry from onIllegalUiState: " + remoteEntry);
                                                XposedHelpers.callMethod(viewModel, "getFlowOnEntrySelected", remoteEntry, null);
                                                return;
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Error in onIllegalUiState handler: " + t.getMessage());
                            }
                        }
                    });
                    XposedBridge.log(TAG + "Hooked CredentialSelectorViewModel.onIllegalUiState");
                    break;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed hooking CredentialSelectorViewModel: " + t.getMessage());
        }
    }

    private static Object findProviderByPackage(List<?> providers, String pkg) {
        for (Object p : providers) {
            if (isTargetPackage(p, pkg)) {
                return p;
            }
        }
        return null;
    }

    private static boolean isTargetPackage(Object provider, String pkg) {
        String id = getProviderId(provider);
        return id != null && id.toLowerCase().contains(pkg.toLowerCase());
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

    private static String getSystemProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static volatile String sRequestedUserHint = null;

    private static void extractRequestedUserHint(Object viewModel, Object getUiState) {
        try {
            Object repo = XposedHelpers.getObjectField(viewModel, "credManRepo");
            if (repo == null) return;

            Object requestInfo = XposedHelpers.callMethod(repo, "getRequestInfo");
            if (requestInfo == null) return;

            Object getReq = XposedHelpers.callMethod(requestInfo, "getGetCredentialRequest");
            if (getReq == null) return;

            List<?> options = (List<?>) XposedHelpers.callMethod(getReq, "getCredentialOptions");
            if (options == null) return;

            for (Object opt : options) {
                Bundle bundle = (Bundle) XposedHelpers.callMethod(opt, "getCredentialRetrievalData");
                if (bundle == null) continue;

                for (String key : bundle.keySet()) {
                    Object val = bundle.get(key);
                    if (val instanceof String) {
                        String str = (String) val;
                        if (str.contains("@") && str.length() < 120) {
                            sRequestedUserHint = str.trim();
                            XposedBridge.log(TAG + "Extracted requested user hint: " + sRequestedUserHint);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void sortCredentialEntries(List<Object> entries) {
        if (entries == null || entries.size() <= 1) {
            return;
        }

        Collections.sort(entries, new Comparator<Object>() {
            @Override
            public int compare(Object a, Object b) {
                int scoreA = getEntryScore(a);
                int scoreB = getEntryScore(b);
                if (scoreA != scoreB) {
                    return Integer.compare(scoreB, scoreA);
                }

                try {
                    Object timeA = XposedHelpers.callMethod(a, "getLastUsedTimeMillis");
                    Object timeB = XposedHelpers.callMethod(b, "getLastUsedTimeMillis");
                    if (timeA instanceof Comparable && timeB != null) {
                        @SuppressWarnings("unchecked")
                        int cmp = ((Comparable<Object>) timeB).compareTo(timeA);
                        if (cmp != 0) return cmp;
                    }
                } catch (Throwable ignored) {}

                return 0;
            }
        });
    }

    private static int getEntryScore(Object entry) {
        int score = 0;
        try {
            Object typeObj = XposedHelpers.callMethod(entry, "getCredentialType");
            String typeStr = typeObj != null ? typeObj.toString() : "";
            String rawType = (String) XposedHelpers.callMethod(entry, "getRawCredentialType");
            if (rawType == null) rawType = "";

            if ("PASSKEY".equalsIgnoreCase(typeStr) || rawType.contains("PUBLIC_KEY") || rawType.toLowerCase().contains("passkey")) {
                score += 10000;
            } else if ("PASSWORD".equalsIgnoreCase(typeStr) || rawType.contains("PASSWORD")) {
                score += 1000;
            }

            try {
                Boolean autoSelect = (Boolean) XposedHelpers.callMethod(entry, "isAutoSelectable");
                if (Boolean.TRUE.equals(autoSelect)) {
                    score += 5000;
                }
            } catch (Throwable ignored) {}

            if (sRequestedUserHint != null && !sRequestedUserHint.isEmpty()) {
                try {
                    String uName = (String) XposedHelpers.callMethod(entry, "getUserName");
                    if (uName != null && !uName.isEmpty()) {
                        if (uName.equalsIgnoreCase(sRequestedUserHint) || sRequestedUserHint.toLowerCase().contains(uName.toLowerCase())) {
                            score += 50000;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return score;
    }
}
