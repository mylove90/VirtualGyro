package io.github.virtualgyro;

import android.util.Log;
import io.github.libxposed.api.XposedModule;

/** Centralized logging through the modern libxposed API. */
final class VGLog {
    private static volatile XposedModule module;

    private VGLog() {}

    static void attach(XposedModule owner) {
        module = owner;
    }

    static void info(String message) {
        XposedModule owner = module;
        if (owner != null) {
            owner.log(Log.INFO, "VirtualGyro", message);
        } else {
            Log.i("VirtualGyro", message);
        }
    }

    static void error(String message, Throwable error) {
        XposedModule owner = module;
        if (owner != null) {
            owner.log(Log.ERROR, "VirtualGyro", message + ": " + error);
        } else {
            Log.e("VirtualGyro", message, error);
        }
    }
}
