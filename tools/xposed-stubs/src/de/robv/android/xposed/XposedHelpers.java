package de.robv.android.xposed;

public final class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader classLoader) {
        return null;
    }
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        return null;
    }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }
    public static XC_MethodHook.Unhook findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        return null;
    }
    public static XC_MethodHook.Unhook findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) {
        return null;
    }
    public static void setBooleanField(Object obj, String fieldName, boolean value) {}
    public static boolean getBooleanField(Object obj, String fieldName) {
        return false;
    }
    public static void setObjectField(Object obj, String fieldName, Object value) {}
    public static Object getObjectField(Object obj, String fieldName) {
        return null;
    }
    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        return null;
    }
}
