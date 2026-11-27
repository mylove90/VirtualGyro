package io.github.virtualgyro;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Listens to the real accelerometer + magnetometer and feeds the apps' listeners that registered
 * for one of the fake sensors.
 */
final class Engine implements SensorEventListener {
    private static Engine instance;

    static synchronized Engine get(SensorManager sm) {
        if (instance == null) {
            instance = new Engine(sm);
        }
        return instance;
    }

    static synchronized Engine peek() {
        return instance;
    }

    private static final class Client {
        final SensorEventListener listener;
        final Sensor sensor;
        final int type;
        final Handler handler;
        final AtomicInteger pending = new AtomicInteger();
        SensorEvent event;

        Client(SensorEventListener listener, Sensor sensor, Handler handler) {
            this.listener = listener;
            this.sensor = sensor;
            this.type = sensor.getType();
            this.handler = handler != null ? handler : new Handler(Looper.getMainLooper());
        }
    }

    private final SensorManager sm;
    private final HandlerThread thread;
    private final Handler worker;
    private final GyroEstimator estimator = new GyroEstimator();
    private final ArrayList<Client> clients = new ArrayList<Client>();
    private boolean running;
    private boolean unavailableLogged;

    private Engine(SensorManager sm) {
        this.sm = sm;
        this.thread = new HandlerThread("VirtualGyro");
        this.thread.start();
        this.worker = new Handler(thread.getLooper());
    }

    synchronized void add(SensorEventListener listener, Sensor sensor, Handler handler) {
        for (Client c : clients) {
            if (c.listener == listener && c.sensor == sensor) {
                return;
            }
        }
        clients.add(new Client(listener, sensor, handler));
        VGLog.info("VirtualGyro: listener registered for fake sensor type " + sensor.getType());
        startIfNeeded();
    }

    /** sensor == null removes every fake registration of this listener. */
    synchronized void remove(SensorEventListener listener, Sensor sensor) {
        Iterator<Client> it = clients.iterator();
        while (it.hasNext()) {
            Client c = it.next();
            if (c.listener == listener && (sensor == null || c.sensor == sensor)) {
                it.remove();
            }
        }
        if (clients.isEmpty()) {
            stopIfRunning();
        }
    }

    private void startIfNeeded() {
        if (running) {
            return;
        }
        Sensor accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (accel == null || mag == null) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                VGLog.info("VirtualGyro: accelerometer or magnetometer missing, cannot emulate");
            }
            return;
        }
        // these go through the normal (unhooked for real sensors) path
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME, worker);
        sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME, worker);
        running = true;
    }

    private void stopIfRunning() {
        if (!running) {
            return;
        }
        running = false; // set first: unregisterListener goes through our own hook
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        int t = e.sensor.getType();
        if (t == Sensor.TYPE_MAGNETIC_FIELD) {
            estimator.onMag(e.values);
            return;
        }
        if (t != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        if (!estimator.onAccel(e.timestamp, e.values)) {
            return;
        }
        final long ts = e.timestamp;
        ArrayList<Client> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<Client>(clients);
        }
        for (Client c : snapshot) {
            float[] v = values(c.type);
            if (v != null) {
                deliver(c, v, ts);
            }
        }
    }

    private float[] values(int type) {
        float[] o = estimator.omega;
        float[] q = estimator.quat;
        switch (type) {
            case FakeSensors.TYPE_GYROSCOPE:
                return new float[]{o[0], o[1], o[2]};
            case FakeSensors.TYPE_GYROSCOPE_UNCALIBRATED:
                return new float[]{o[0], o[1], o[2], 0f, 0f, 0f};
            case FakeSensors.TYPE_ROTATION_VECTOR:
                return new float[]{q[0], q[1], q[2], q[3], -1f};
            case FakeSensors.TYPE_GAME_ROTATION_VECTOR:
                return new float[]{q[0], q[1], q[2], q[3]};
            default:
                return null;
        }
    }

    private void deliver(final Client c, final float[] v, final long ts) {
        if (c.pending.get() > 2) {
            return; // the app's thread is behind, drop instead of queueing forever
        }
        c.pending.incrementAndGet();
        c.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (c.event == null) {
                        c.event = FakeSensors.newEvent(FakeSensors.valueCount(c.type));
                    }
                    SensorEvent ev = c.event;
                    if (ev == null) {
                        return;
                    }
                    System.arraycopy(v, 0, ev.values, 0, Math.min(v.length, ev.values.length));
                    ev.timestamp = ts;
                    ev.accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM;
                    ev.sensor = c.sensor;
                    c.listener.onSensorChanged(ev);
                } catch (Throwable t) {
                    VGLog.info("VirtualGyro: delivery failed: " + t);
                } finally {
                    c.pending.decrementAndGet();
                }
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
