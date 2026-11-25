package de.robv.android.xposed.callbacks;

public abstract class XC_LoadPackage {
    public static final class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public Object appInfo; // real type: android.content.pm.ApplicationInfo (unused by this module)
        public boolean isFirstApplication;
    }
}
