package com.n3th3r.intunebypass;

import android.database.MatrixCursor;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.Future;

public final class MamBoundaryHooks {
    private static final String[] BOUNDARY_CLASSES = {
        "com.microsoft.intune.origin.InstrumentationCheck",
        "com.microsoft.intune.origin.abstraction.DeviceOriginContentProvider",
        "com.microsoft.intune.mam.client.ipc.AbstractAppPolicyEndpoint",
        "com.microsoft.omadm.apppolicy.MDMAppPolicyEndpoint",
        "com.microsoft.intune.mam.client.ipc.AppPolicyEndpointProxy",
        "com.microsoft.intune.mam.client.ipc.AppPolicyEndpointWrapper",
        "com.microsoft.intune.mam.policy.PolicyChecker",
        "com.microsoft.intune.mam.policy.security.ComplianceVerdictManagerImpl",
        "com.microsoft.intune.mam.client.app.startup.ConditionalLaunchGetAccessFragment"
    };

    private MamBoundaryHooks() {}

    public static void installAll(ClassLoader cl) {
        for (String name : BOUNDARY_CLASSES) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
            if (cls != null && ClassLoaderHooker.markClassHooked(cls)) {
                hookLoadedClass(cls);
            }
        }
    }

    public static void hookLoadedClass(Class<?> cls) {
        String name = cls.getName();
        if ("com.microsoft.intune.origin.InstrumentationCheck".equals(name)) {
            hookInstrumentationCheck(cls);
        } else if ("com.microsoft.intune.origin.abstraction.DeviceOriginContentProvider".equals(name)) {
            hookDeviceOriginContentProvider(cls);
        } else if (name.endsWith("AppPolicyEndpoint")
                || name.endsWith("AppPolicyEndpointProxy")
                || name.endsWith("AppPolicyEndpointWrapper")) {
            hookAppPolicyEndpoint(cls);
        } else if ("com.microsoft.intune.mam.policy.PolicyChecker".equals(name)) {
            hookPolicyChecker(cls);
        } else if ("com.microsoft.intune.mam.policy.security.ComplianceVerdictManagerImpl".equals(name)) {
            hookComplianceVerdictManager(cls);
        } else if ("com.microsoft.intune.mam.client.app.startup.ConditionalLaunchGetAccessFragment".equals(name)) {
            hookConditionalLaunchFragment(cls);
        }
    }

    private static void hookInstrumentationCheck(Class<?> cls) {
        IntuneBypassInit.log("Hooking InstrumentationCheck");
        for (Method m : cls.getDeclaredMethods()) {
            Class<?> ret = m.getReturnType();
            if (ret == int.class || ret == Integer.class) {
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(0));
            } else if (ret == boolean.class || ret == Boolean.class) {
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(Boolean.FALSE));
            }
        }
    }

    private static void hookDeviceOriginContentProvider(Class<?> cls) {
        IntuneBypassInit.log("Hooking DeviceOriginContentProvider");
        XposedBridge.hookAllMethods(cls, "query", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                MatrixCursor cursor = new MatrixCursor(new String[] {"name", "value"}, 1);
                cursor.addRow(new Object[] {"origin", 0});
                return cursor;
            }
        });
        for (Field f : cls.getDeclaredFields()) {
            Class<?> fType = f.getType();
            if (fType.getName().startsWith("o.")) {
                ObfuscatedTargetHooks.hookOriginAggregatorClass(fType);
            }
        }
    }

    private static void hookAppPolicyEndpoint(final Class<?> cls) {
        IntuneBypassInit.log("Hooking Endpoint: " + cls.getName());
        hookMethodReturnConstant(cls, "isDeviceCompliant", Boolean.TRUE);
        hookMethodReturnConstant(cls, "isXposeDetected", Boolean.FALSE);
        hookMethodReturnConstant(cls, "assessComplianceState", Boolean.TRUE);
        hookMethodReturnConstant(cls, "setRaspDetected", null);
        hookDestroySecrets(cls);
        hookAttestationResult(cls);
        discoverRaspManagerFromEndpoint(cls);
    }

    private static void hookMethodReturnConstant(Class<?> cls, String methodName, Object constant) {
        try {
            XposedBridge.hookAllMethods(cls, methodName, XC_MethodReplacement.returnConstant(constant));
        } catch (Throwable ignored) {
        }
    }

    private static void hookDestroySecrets(Class<?> cls) {
        try {
            XposedBridge.hookAllMethods(cls, "destroySecretsIfNecessary", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length >= 2 && param.args[1] != null) {
                        String reason = param.args[1].toString();
                        if (reason.contains("NON_COMPLIANT") || reason.contains("ATTESTATION")) {
                            IntuneBypassInit.log("Blocked destroySecretsIfNecessary for " + reason);
                            param.setResult(null);
                        }
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookAttestationResult(final Class<?> cls) {
        try {
            XposedBridge.hookAllMethods(cls, "getAttestationResult", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return createCompliantAttestationInfo(cls.getClassLoader());
                }
            });
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object createCompliantAttestationInfo(ClassLoader cl) {
        try {
            Class<?> infoCls = XposedHelpers.findClass(
                    "com.microsoft.intune.mam.client.ipc.DeviceAttestationInfo", cl);
            Class<? extends Enum> resEnum = (Class<? extends Enum>) XposedHelpers.findClass(
                    "com.microsoft.intune.mam.policy.DeviceAttestationAgentResult", cl);
            Class<? extends Enum> evalEnum = (Class<? extends Enum>) XposedHelpers.findClass(
                    "com.microsoft.intune.mam.policy.DeviceAttestationEvaluationType", cl);
            Object resVal = Enum.valueOf(resEnum, "DEVICE_INTEGRITY_AND_CTS");
            Object evalVal = Enum.valueOf(evalEnum, "HARDWARE_BACKED");
            Constructor<?> ctor = infoCls.getDeclaredConstructor(resEnum, evalEnum);
            ctor.setAccessible(true);
            return ctor.newInstance(resVal, evalVal);
        } catch (Throwable t) {
            IntuneBypassInit.log("Failed to build DeviceAttestationInfo: " + t.getMessage());
            return null;
        }
    }

    private static void discoverRaspManagerFromEndpoint(Class<?> cls) {
        try {
            Field raspField = cls.getDeclaredField("mRaspManager");
            Class<?> raspCls = raspField.getType();
            if (raspCls != null && raspCls.getName().startsWith("o.")) {
                ObfuscatedTargetHooks.hookRaspManagerClass(raspCls);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookPolicyChecker(Class<?> cls) {
        IntuneBypassInit.log("Hooking PolicyChecker");
        hookMethodReturnConstant(cls, "onTransact", Boolean.TRUE);
        for (Method m : cls.getDeclaredMethods()) {
            String retName = m.getReturnType().getName();
            if (retName.endsWith("ConditionalLaunchAction")) {
                hookConditionalLaunchActionMethod(m);
            } else if (Queue.class.isAssignableFrom(m.getReturnType())) {
                hookQueueMethod(m);
            }
        }
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null) {
                    return;
                }
                for (Object arg : param.args) {
                    if (arg != null && isOriginCheckManagerCandidate(arg.getClass())) {
                        ObfuscatedTargetHooks.hookOriginCheckManagerClass(arg.getClass());
                    }
                }
            }
        });
    }

    private static boolean isOriginCheckManagerCandidate(Class<?> candidate) {
        if (!candidate.getName().startsWith("o.")) {
            return false;
        }
        for (Method m : candidate.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (m.getReturnType() == Future.class && params.length == 1 && params[0] == boolean.class) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hookConditionalLaunchActionMethod(Method method) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object res = param.getResult();
                if (res instanceof Enum<?>) {
                    String name = ((Enum<?>) res).name();
                    if (isBlockedLaunchAction(name)) {
                        Class<? extends Enum> enumCls = (Class<? extends Enum>) res.getClass();
                        param.setResult(Enum.valueOf(enumCls, "NONE"));
                    }
                }
            }
        });
    }

    private static void hookQueueMethod(Method method) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object res = param.getResult();
                if (res instanceof Queue<?>) {
                    Queue<?> queue = (Queue<?>) res;
                    Iterator<?> it = queue.iterator();
                    while (it.hasNext()) {
                        Object item = it.next();
                        if (item != null && isBlockedLaunchAction(item.toString())) {
                            it.remove();
                        }
                    }
                }
            }
        });
    }

    private static boolean isBlockedLaunchAction(String name) {
        return "DEVICE_NON_COMPLIANCE".equals(name)
                || "DEVICE_ATTESTATION_ERROR".equals(name)
                || "DEVICE_ATTESTATION_PROCESSING".equals(name)
                || "ORIGIN_CHECK_PENDING".equals(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hookComplianceVerdictManager(Class<?> cls) {
        IntuneBypassInit.log("Hooking ComplianceVerdictManagerImpl");
        for (Method m : cls.getDeclaredMethods()) {
            if ("getComplianceResult".equals(m.getName()) && m.getReturnType().isEnum()) {
                final Class<? extends Enum> enumCls = (Class<? extends Enum>) m.getReturnType();
                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        return getCompliantEnumValue(enumCls);
                    }
                });
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void hookConditionalLaunchFragment(final Class<?> cls) {
        IntuneBypassInit.log("Hooking ConditionalLaunchGetAccessFragment");
        hookMethodReturnConstant(cls, "areAllPolicyBucketsCompliant", Boolean.TRUE);
        for (Method m : cls.getDeclaredMethods()) {
            if ("getDeviceHealthyPoliciesResult".equals(m.getName()) && m.getReturnType().isEnum()) {
                final Class<? extends Enum> enumCls = (Class<? extends Enum>) m.getReturnType();
                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        setDeviceHealthySuccess(param.thisObject, cls.getClassLoader());
                        return getCompliantEnumValue(enumCls);
                    }
                });
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setDeviceHealthySuccess(Object fragment, ClassLoader cl) {
        if (fragment == null) {
            return;
        }
        try {
            Class<? extends Enum> statusCls = (Class<? extends Enum>) XposedHelpers.findClass(
                    "com.microsoft.intune.mam.client.app.startup.ConditionalLaunchGetAccessFragment$ComplianceStatus",
                    cl);
            Object successVal = Enum.valueOf(statusCls, "COMPLIANCE_SUCCESS");
            XposedHelpers.setObjectField(fragment, "mDeviceHealthyCompliance", successVal);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getCompliantEnumValue(Class<? extends Enum> enumCls) {
        try {
            return Enum.valueOf(enumCls, "COMPLIANT");
        } catch (IllegalArgumentException ignored) {
            return Enum.valueOf(enumCls, "NONE");
        }
    }
}
