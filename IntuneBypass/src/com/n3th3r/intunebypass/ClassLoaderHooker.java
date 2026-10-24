package com.n3th3r.intunebypass;

import android.content.Context;
import android.content.ContextWrapper;
import dalvik.system.BaseDexClassLoader;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClassLoaderHooker {
    private static final AtomicBoolean GLOBAL_INSTALLED = new AtomicBoolean(false);
    private static final Set<ClassLoader> HOOKED_LOADERS =
            Collections.newSetFromMap(new ConcurrentHashMap<ClassLoader, Boolean>());
    private static final Set<Class<?>> HOOKED_CLASSES =
            Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());
    private static final ThreadLocal<Boolean> IN_INTERNAL_LOOKUP =
            new ThreadLocal<Boolean>() {
                @Override
                protected Boolean initialValue() {
                    return Boolean.FALSE;
                }
            };

    private ClassLoaderHooker() {}

    public static void installGlobalHooks(final String packageName) {
        if (!GLOBAL_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        hookDexClassLoaderConstructors(packageName);
        hookCreatePackageContext(packageName);
    }

    public static void hookClassLoader(ClassLoader cl, String packageName) {
        if (cl == null || isFrameworkHookLoader(cl) || !HOOKED_LOADERS.add(cl)) {
            return;
        }
        IN_INTERNAL_LOOKUP.set(Boolean.TRUE);
        try {
            IntuneBypassInit.log("Hooking ClassLoader in " + packageName + ": " + cl.getClass().getName());
            MamBoundaryHooks.installAll(cl);
            ObfuscatedTargetHooks.installAll(cl);
        } catch (Throwable t) {
            IntuneBypassInit.log("Error hooking ClassLoader: " + t.getMessage());
        } finally {
            IN_INTERNAL_LOOKUP.set(Boolean.FALSE);
        }
    }

    public static boolean markClassHooked(Class<?> cls) {
        return cls != null && HOOKED_CLASSES.add(cls);
    }

    private static boolean isFrameworkHookLoader(ClassLoader cl) {
        String name = cl.getClass().getName();
        return name.contains("InMemoryDexClassLoader")
                || name.contains("LSP")
                || name.contains("Vector");
    }

    private static void hookDexClassLoaderConstructors(final String packageName) {
        try {
            XposedBridge.hookAllConstructors(BaseDexClassLoader.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(IN_INTERNAL_LOOKUP.get())) {
                        return;
                    }
                    if (param.thisObject instanceof ClassLoader) {
                        hookClassLoader((ClassLoader) param.thisObject, packageName);
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static void hookCreatePackageContext(final String packageName) {
        try {
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class,
                    "createPackageContext",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(IN_INTERNAL_LOOKUP.get())) {
                                return;
                            }
                            Object res = param.getResult();
                            if (res instanceof Context) {
                                ClassLoader cl = ((Context) res).getClassLoader();
                                hookClassLoader(cl, packageName);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }
}
