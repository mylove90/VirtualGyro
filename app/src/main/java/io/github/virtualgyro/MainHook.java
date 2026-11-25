package io.github.virtualgyro;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String pkg = lpparam.packageName;
        if (pkg == null || "android".equals(pkg) || "io.github.virtualgyro".equals(pkg)) {
            return;
        }
        SensorHooks.install(pkg);
    }
}
