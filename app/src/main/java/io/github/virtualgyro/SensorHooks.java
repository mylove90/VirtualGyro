package io.github.virtualgyro;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Hooks android.hardware.SensorManager inside the target app so missing gyro/rotation sensors appear. */
final class SensorHooks {
    private static boolean installed;
    private static final Set<Integer> LOGGED = new HashSet<Integer>();

    private SensorHooks() {
    }

    private static synchronized void logOnce(int type, String what) {
        if (LOGGED.add(type)) {
            XposedBridge.log("VirtualGyro: sensor type " + type + " requested: " + what);
        }
    }

    private static boolean hasType(List<Sensor> list, int type) {
        for (Sensor s : list) {
            if (s != null && s.getType() == type) {
                return true;
            }
        }
        return false;
    }

    static synchronized void install(String pkg) {
        if (installed) {
            return;
        }
        installed = true;
        XposedBridge.log("VirtualGyro: installing hooks in " + pkg);

        try {
            XposedBridge.hookAllMethods(SensorManager.class, "getDefaultSensor", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    if (param.getResult() != null) {
                        return;
                    }
                    int type = ((Integer) param.args[0]).intValue();
                    if (FakeSensors.supports(type)) {
                        Sensor fake = FakeSensors.get(type);
                        if (fake != null) {
                            param.setResult(fake);
                            logOnce(type, "provided a virtual sensor");
                        }
                    } else {
                        logOnce(type, "not available and not emulated");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: getDefaultSensor hook failed: " + t);
        }

        try {
            XposedBridge.hookAllMethods(SensorManager.class, "getSensorList", new XC_MethodHook() {
                @Override
                @SuppressWarnings("unchecked")
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    Object r = param.getResult();
                    if (!(r instanceof List)) {
                        return;
                    }
                    int type = ((Integer) param.args[0]).intValue();
                    int[] types;
                    if (type == Sensor.TYPE_ALL) {
                        types = new int[]{FakeSensors.TYPE_GYROSCOPE, FakeSensors.TYPE_ROTATION_VECTOR,
                                FakeSensors.TYPE_GAME_ROTATION_VECTOR, FakeSensors.TYPE_GYROSCOPE_UNCALIBRATED};
                    } else if (FakeSensors.supports(type)) {
                        types = new int[]{type};
                    } else {
                        return;
                    }
                    List<Sensor> orig = (List<Sensor>) r;
                    ArrayList<Sensor> out = null;
                    for (int t : types) {
                        if (!hasType(orig, t)) {
                            Sensor fake = FakeSensors.get(t);
                            if (fake != null) {
                                if (out == null) {
                                    out = new ArrayList<Sensor>(orig);
                                }
                                out.add(fake);
                            }
                        }
                    }
                    if (out != null) {
                        param.setResult(out);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: getSensorList hook failed: " + t);
        }

        try {
            XposedBridge.hookAllMethods(SensorManager.class, "registerListener", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object[] a = param.args;
                    if (a == null || a.length < 3 || !(a[0] instanceof SensorEventListener) || !(a[1] instanceof Sensor)) {
                        return;
                    }
                    Sensor s = (Sensor) a[1];
                    if (!FakeSensors.isFake(s)) {
                        return;
                    }
                    Handler h = null;
                    for (Object o : a) {
                        if (o instanceof Handler) {
                            h = (Handler) o;
                        }
                    }
                    Engine.get((SensorManager) param.thisObject).add((SensorEventListener) a[0], s, h);
                    param.setResult(Boolean.TRUE);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: registerListener hook failed: " + t);
        }

        try {
            XposedBridge.hookAllMethods(SensorManager.class, "unregisterListener", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object[] a = param.args;
                    if (a == null || a.length < 1 || !(a[0] instanceof SensorEventListener)) {
                        return;
                    }
                    if (a[0] instanceof Engine) {
                        return; // our own listener, normal path
                    }
                    Engine e = Engine.peek();
                    if (e == null) {
                        return;
                    }
                    Sensor s = (a.length >= 2 && a[1] instanceof Sensor) ? (Sensor) a[1] : null;
                    if (s != null && !FakeSensors.isFake(s)) {
                        return; // a real sensor, not ours
                    }
                    e.remove((SensorEventListener) a[0], s);
                    if (s != null) {
                        param.setResult(null); // only a fake sensor was named: skip the framework call
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: unregisterListener hook failed: " + t);
        }

        // Some apps check the feature flag before they ever ask for the sensor.
        try {
            Class<?> pm = XposedHelpers.findClass("android.app.ApplicationPackageManager", null);
            XposedBridge.hookAllMethods(pm, "hasSystemFeature", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args != null && param.args.length >= 1
                            && "android.hardware.sensor.gyroscope".equals(param.args[0])
                            && Boolean.FALSE.equals(param.getResult())) {
                        param.setResult(Boolean.TRUE);
                        logOnce(-1, "hasSystemFeature(android.hardware.sensor.gyroscope) answered true");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: hasSystemFeature hook failed: " + t);
        }
    }
}
