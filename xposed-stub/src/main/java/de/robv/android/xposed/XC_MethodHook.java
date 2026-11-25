package de.robv.android.xposed;

import java.lang.reflect.Member;

public abstract class XC_MethodHook {
    public XC_MethodHook() {
    }

    public XC_MethodHook(int priority) {
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        public Object getResult() {
            throw new RuntimeException("stub");
        }

        public void setResult(Object result) {
            throw new RuntimeException("stub");
        }

        public Throwable getThrowable() {
            throw new RuntimeException("stub");
        }

        public boolean hasThrowable() {
            throw new RuntimeException("stub");
        }

        public void setThrowable(Throwable throwable) {
            throw new RuntimeException("stub");
        }

        public Object getResultOrThrowable() throws Throwable {
            throw new RuntimeException("stub");
        }
    }

    public class Unhook {
        public void unhook() {
            throw new RuntimeException("stub");
        }
    }
}
