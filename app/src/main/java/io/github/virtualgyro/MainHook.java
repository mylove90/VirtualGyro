package io.github.virtualgyro;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** Modern libxposed entry point. */
public final class MainHook extends XposedModule {
    private static final String TARGET_PACKAGE = "com.samsung.android.scan3d";

    public MainHook() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        VGLog.attach(this);
        VGLog.info("Module loaded");
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }
        VGLog.attach(this);
        SensorHooks.install(this, packageName);
    }
}
