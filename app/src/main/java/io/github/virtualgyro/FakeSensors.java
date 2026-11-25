package io.github.virtualgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

/** Creates Sensor / SensorEvent objects through hidden constructors (works because the app process is hooked). */
final class FakeSensors {
    static final int TYPE_GYROSCOPE = 4;
    static final int TYPE_ROTATION_VECTOR = 11;
    static final int TYPE_GAME_ROTATION_VECTOR = 15;
    static final int TYPE_GYROSCOPE_UNCALIBRATED = 16;

    private static final Map<Integer, Sensor> CACHE = new HashMap<Integer, Sensor>();

    private FakeSensors() {
    }

    static boolean supports(int type) {
        return type == TYPE_GYROSCOPE || type == TYPE_ROTATION_VECTOR
                || type == TYPE_GAME_ROTATION_VECTOR || type == TYPE_GYROSCOPE_UNCALIBRATED;
    }

    static int valueCount(int type) {
        switch (type) {
            case TYPE_GYROSCOPE:
                return 3;
            case TYPE_GYROSCOPE_UNCALIBRATED:
                return 6;
            case TYPE_ROTATION_VECTOR:
                return 5;
            default:
                return 4;
        }
    }

    private static String stringType(int type) {
        switch (type) {
            case TYPE_GYROSCOPE:
                return "android.sensor.gyroscope";
            case TYPE_GYROSCOPE_UNCALIBRATED:
                return "android.sensor.gyroscope_uncalibrated";
            case TYPE_ROTATION_VECTOR:
                return "android.sensor.rotation_vector";
            default:
                return "android.sensor.game_rotation_vector";
        }
    }

    private static String name(int type) {
        switch (type) {
            case TYPE_GYROSCOPE:
                return "Virtual Gyroscope";
            case TYPE_GYROSCOPE_UNCALIBRATED:
                return "Virtual Gyroscope Uncalibrated";
            case TYPE_ROTATION_VECTOR:
                return "Virtual Rotation Vector";
            default:
                return "Virtual Game Rotation Vector";
        }
    }

    /** Returns the (cached) fake sensor for this type, or null if it could not be created. */
    static synchronized Sensor get(int type) {
        if (!supports(type)) {
            return null;
        }
        Sensor s = CACHE.get(type);
        if (s != null) {
            return s;
        }
        try {
            Constructor<Sensor> c = Sensor.class.getDeclaredConstructor();
            c.setAccessible(true);
            s = c.newInstance();
            boolean gyro = type == TYPE_GYROSCOPE || type == TYPE_GYROSCOPE_UNCALIBRATED;
            Reflect.setField(s, "mName", name(type));
            Reflect.setField(s, "mVendor", "VirtualGyro");
            Reflect.setField(s, "mVersion", Integer.valueOf(1));
            Reflect.setField(s, "mHandle", Integer.valueOf(0x7F560000 | type));
            Reflect.setField(s, "mType", Integer.valueOf(type));
            Reflect.setField(s, "mMaxRange", Float.valueOf(gyro ? 34.9f : 1.0f));
            Reflect.setField(s, "mResolution", Float.valueOf(gyro ? 0.001f : 0.0001f));
            Reflect.setField(s, "mPower", Float.valueOf(0.2f));
            Reflect.setField(s, "mMinDelay", Integer.valueOf(10000));
            Reflect.setField(s, "mMaxDelay", Integer.valueOf(0));
            Reflect.setField(s, "mFlags", Integer.valueOf(0));
            Reflect.setField(s, "mStringType", stringType(type));
            Reflect.setField(s, "mRequiredPermission", "");
            CACHE.put(type, s);
            return s;
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: cannot create fake sensor type " + type + ": " + t);
            return null;
        }
    }

    static synchronized boolean isFake(Object o) {
        if (!(o instanceof Sensor)) {
            return false;
        }
        for (Sensor s : CACHE.values()) {
            if (s == o) {
                return true;
            }
        }
        return false;
    }

    static SensorEvent newEvent(int valueCount) {
        try {
            Constructor<SensorEvent> c = SensorEvent.class.getDeclaredConstructor(int.class);
            c.setAccessible(true);
            return c.newInstance(Integer.valueOf(valueCount));
        } catch (Throwable t) {
            XposedBridge.log("VirtualGyro: cannot create SensorEvent: " + t);
            return null;
        }
    }
}
