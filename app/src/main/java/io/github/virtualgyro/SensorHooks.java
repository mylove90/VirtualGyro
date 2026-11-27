package io.github.virtualgyro;

import android.app.Application;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Hooks SensorManager using the modern libxposed interceptor-chain API. */
final class SensorHooks {
    private static boolean installed;
    private static final Set<Integer> LOGGED = new HashSet<>();

    private SensorHooks() {}

    private static synchronized void logOnce(int type, String what) {
        if (LOGGED.add(type)) {
            VGLog.info("sensor type " + type + " requested: " + what);
        }
    }

    private static boolean hasType(List<Sensor> list, int type) {
        for (Sensor sensor : list) {
            if (sensor != null && sensor.getType() == type) return true;
        }
        return false;
    }

    private static void hookAll(XposedModule module, Class<?> owner, String name,
                                XposedInterface.Hooker hooker) {
        int count = 0;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                module.hook(method).intercept(hooker);
                count++;
            }
        }
        if (count == 0) VGLog.info("No methods found for hook: " + owner.getName() + "." + name);
    }

    static synchronized void install(XposedModule module, String packageName) {
        if (installed) return;
        installed = true;
        VGLog.attach(module);
        VGLog.info("Installing modern hooks in " + packageName);

        try {
            hookAll(module, SensorManager.class, "getDefaultSensor", chain -> {
                Object result = chain.proceed();
                if (result != null || chain.getArgs().isEmpty() || !(chain.getArg(0) instanceof Integer)) {
                    return result;
                }
                int type = (Integer) chain.getArg(0);
                if (!FakeSensors.supports(type)) {
                    logOnce(type, "not available and not emulated");
                    return null;
                }
                Sensor fake = FakeSensors.get(type);
                if (fake != null) {
                    logOnce(type, "provided a virtual sensor");
                    return fake;
                }
                return null;
            });
        } catch (Throwable t) {
            VGLog.error("getDefaultSensor hook failed", t);
        }

        try {
            hookAll(module, SensorManager.class, "getSensorList", chain -> {
                Object result = chain.proceed();
                if (!(result instanceof List) || chain.getArgs().isEmpty()
                        || !(chain.getArg(0) instanceof Integer)) return result;
                int type = (Integer) chain.getArg(0);
                int[] types;
                if (type == Sensor.TYPE_ALL) {
                    types = new int[]{FakeSensors.TYPE_GYROSCOPE, FakeSensors.TYPE_ROTATION_VECTOR,
                            FakeSensors.TYPE_GAME_ROTATION_VECTOR, FakeSensors.TYPE_GYROSCOPE_UNCALIBRATED};
                } else if (FakeSensors.supports(type)) {
                    types = new int[]{type};
                } else {
                    return result;
                }
                @SuppressWarnings("unchecked") List<Sensor> original = (List<Sensor>) result;
                ArrayList<Sensor> output = null;
                for (int sensorType : types) {
                    if (!hasType(original, sensorType)) {
                        Sensor fake = FakeSensors.get(sensorType);
                        if (fake != null) {
                            if (output == null) output = new ArrayList<>(original);
                            output.add(fake);
                        }
                    }
                }
                return output != null ? output : result;
            });
        } catch (Throwable t) {
            VGLog.error("getSensorList hook failed", t);
        }

        try {
            hookAll(module, SensorManager.class, "registerListener", chain -> {
                List<Object> args = chain.getArgs();
                if (args.size() < 3 || !(args.get(0) instanceof SensorEventListener)
                        || !(args.get(1) instanceof Sensor)) return chain.proceed();
                Sensor sensor = (Sensor) args.get(1);
                if (!FakeSensors.isFake(sensor)) return chain.proceed();
                Handler handler = null;
                for (Object arg : args) if (arg instanceof Handler) handler = (Handler) arg;
                Engine.get((SensorManager) chain.getThisObject())
                        .add((SensorEventListener) args.get(0), sensor, handler);
                return Boolean.TRUE;
            });
        } catch (Throwable t) {
            VGLog.error("registerListener hook failed", t);
        }

        try {
            hookAll(module, SensorManager.class, "unregisterListener", chain -> {
                List<Object> args = chain.getArgs();
                if (args.isEmpty() || !(args.get(0) instanceof SensorEventListener)
                        || args.get(0) instanceof Engine) return chain.proceed();
                Engine engine = Engine.peek();
                if (engine == null) return chain.proceed();
                Sensor sensor = args.size() >= 2 && args.get(1) instanceof Sensor
                        ? (Sensor) args.get(1) : null;
                if (sensor != null && !FakeSensors.isFake(sensor)) return chain.proceed();
                engine.remove((SensorEventListener) args.get(0), sensor);
                return sensor != null ? null : chain.proceed();
            });
        } catch (Throwable t) {
            VGLog.error("unregisterListener hook failed", t);
        }

        // Some apps check the gyroscope feature flag before requesting a sensor.
        try {
            Class<?> pmClass = Class.forName("android.app.ApplicationPackageManager");
            hookAll(module, pmClass, "hasSystemFeature", chain -> {
                Object result = chain.proceed();
                if (!Boolean.TRUE.equals(result) && !chain.getArgs().isEmpty()
                        && "android.hardware.sensor.gyroscope".equals(chain.getArg(0))) {
                    logOnce(-1, "hasSystemFeature(gyroscope) answered true");
                    return Boolean.TRUE;
                }
                return result;
            });
        } catch (Throwable t) {
            VGLog.error("hasSystemFeature hook failed", t);
        }
    }
}
