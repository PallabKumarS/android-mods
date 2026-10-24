package com.n3th3r.intunebypass;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public final class ObfuscatedTargetHooks {
    private static final String[] OBFUSCATED_CLASSES = {
        "o.safeValue",
        "o.FirebaseNetworkException",
        "o.UiExecutor",
        "o.putSync",
        "o.accessgetEditLockp",
        "o.TransportBackend",
        "o.JsonIOException",
        "o.pushTrace"
    };
    private static final Set<Class<?>> HOOKED_DYNAMIC =
            Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    private ObfuscatedTargetHooks() {}

    public static void installAll(ClassLoader cl) {
        for (String name : OBFUSCATED_CLASSES) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
            if (cls != null && ClassLoaderHooker.markClassHooked(cls)) {
                hookLoadedClass(cls);
            }
        }
    }

    public static void hookLoadedClass(Class<?> cls) {
        String name = cls.getName();
        if ("o.safeValue".equals(name)) {
            hookOriginCheckManagerClass(cls);
        } else if ("o.FirebaseNetworkException".equals(name)) {
            hookRaspManagerClass(cls);
        } else if ("o.UiExecutor".equals(name)) {
            hookUiExecutor(cls);
        } else if ("o.putSync".equals(name)) {
            hookPutSync(cls);
        } else if ("o.accessgetEditLockp".equals(name)) {
            hookOriginAggregatorClass(cls);
        } else if ("o.TransportBackend".equals(name) || "o.JsonIOException".equals(name)) {
            hookXposedDetectorClass(cls);
        } else if ("o.pushTrace".equals(name)) {
            hookPushTrace(cls);
        }
    }

    public static void hookOriginCheckManagerClass(final Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking OriginCheckManager: " + cls.getName());
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                setBooleanFieldSafe(param.thisObject, "getU1", true);
            }
        });
        hookNamedMethodConstant(cls, "UserChoiceDialog2", Boolean.TRUE);
        hookNamedMethodConstant(cls, "onReceive", Boolean.TRUE);
        hookNamedMethodConstant(cls, "MediaBrowserCompat", Boolean.TRUE);
        hookNamedMethodConstant(cls, "read", Boolean.FALSE);
        for (Method m : cls.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (m.getReturnType() == Future.class && params.length == 1 && params[0] == boolean.class) {
                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        setBooleanFieldSafe(param.thisObject, "getU1", true);
                        return createReadyFutureTrue(cls.getClassLoader());
                    }
                });
            }
        }
    }

    public static void hookRaspManagerClass(Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking RaspManager: " + cls.getName());
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                setBooleanFieldSafe(param.thisObject, "ElephantEngine1", false);
            }
        });
        hookNamedMethodConstant(cls, "MediaBrowserCompatMediaItem1", Boolean.FALSE);
        hookNamedMethodConstant(cls, "INotificationSideChannelDefault", null);
        hookNamedMethodConstant(cls, "MediaBrowserCompatMediaBrowserImplApi23", null);
        hookNamedMethodConstant(cls, "sendEvent", null);
        hookNamedMethodConstant(cls, "setSelector", null);
        hookNamedMethodConstant(cls, "YubiKitUsbSmartcardCertBasedAuthManager11", null);
        hookNamedMethodConstant(cls, "YubiKitUsbSmartcardCertBasedAuthManager2", null);
        hookNamedMethodConstant(cls, "YubiKitUsbSmartcardCertBasedAuthManager3", null);
        hookNamedMethodConstant(cls, "MediaBrowserCompatMediaBrowserImplApi213", null);
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getReturnType() == boolean.class && m.getParameterTypes().length == 1) {
                XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        setBooleanFieldSafe(param.thisObject, "ElephantEngine1", false);
                        return Boolean.FALSE;
                    }
                });
            }
        }
    }

    public static void hookOriginAggregatorClass(Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking OriginAggregator: " + cls.getName());
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getReturnType() == int.class && m.getParameterTypes().length == 0) {
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(0));
            }
        }
    }

    private static void hookXposedDetectorClass(Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking XposedDetector: " + cls.getName());
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getReturnType() == int.class) {
                XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(0));
            }
        }
    }

    private static void hookUiExecutor(Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking UiExecutor");
        hookNamedMethodConstant(cls, "createIdToken", Boolean.FALSE);
        hookNamedMethodConstant(cls, "asInterface", Boolean.FALSE);
        hookNamedMethodConstant(cls, "INotificationSideChannelStub", 0);
    }

    private static void hookPutSync(Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking putSync (OriginDetectionResult)");
        XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null && param.args.length >= 1 && param.args[0] instanceof Integer) {
                    param.args[0] = 0;
                    for (int i = 1; i < param.args.length; i++) {
                        if (param.args[i] instanceof Long) {
                            param.args[i] = null;
                        }
                    }
                }
            }
        });
    }

    private static void hookPushTrace(Class<?> cls) {
        if (!HOOKED_DYNAMIC.add(cls)) {
            return;
        }
        IntuneBypassInit.log("Hooking pushTrace (DeviceOriginUseCase)");
        hookNamedMethodConstant(cls, "getInflatedId", Boolean.TRUE);
        hookNamedMethodConstant(cls, "SignInIntrospectApiResultDefaultImpls", Boolean.TRUE);
        hookNamedMethodConstant(cls, "SignInIntrospectApiResultRedirect", Boolean.FALSE);
        hookNamedMethodConstant(cls, "SignInInitiateApiResultUserNotFound", Boolean.FALSE);
        hookNamedMethodConstant(cls, "getBitmap", Boolean.FALSE);
        hookNamedMethodConstant(cls, "containsKey", null);
    }

    private static void hookNamedMethodConstant(Class<?> cls, String name, Object constant) {
        try {
            XposedBridge.hookAllMethods(cls, name, XC_MethodReplacement.returnConstant(constant));
        } catch (Throwable ignored) {
        }
    }

    private static void setBooleanFieldSafe(Object obj, String fieldName, boolean value) {
        if (obj == null) {
            return;
        }
        try {
            XposedHelpers.setBooleanField(obj, fieldName, value);
        } catch (Throwable ignored) {
        }
    }

    private static Object createReadyFutureTrue(ClassLoader cl) {
        try {
            Class<?> rfCls = XposedHelpers.findClass(
                    "com.microsoft.intune.mam.client.util.ReadyFuture", cl);
            Constructor<?> ctor = rfCls.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            return ctor.newInstance(Boolean.TRUE);
        } catch (Throwable t) {
            return null;
        }
    }
}
