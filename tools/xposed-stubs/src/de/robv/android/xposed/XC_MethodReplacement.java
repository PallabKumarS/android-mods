package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {

    public static final XC_MethodReplacement DO_NOTHING = new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            return null;
        }
    };

    public static XC_MethodReplacement returnConstant(final Object result) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                return result;
            }
        };
    }

    public XC_MethodReplacement() {}

    public XC_MethodReplacement(int priority) {
        super(priority);
    }

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
        try {
            Object result = replaceHookedMethod(param);
            param.setResult(result);
        } catch (Throwable t) {
            param.setThrowable(t);
        }
    }

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;
}
