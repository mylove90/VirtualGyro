package io.github.virtualgyro;

/**
 * Builds an orientation from accelerometer + magnetometer and derives a virtual gyroscope from it.
 * Pure Java, fed by Engine; not thread safe (use from one thread).
 */
final class GyroEstimator {
    // low-pass strengths (0..1, higher = faster/noisier) and noise gate
    private final float alphaGravity;
    private final float alphaMagnetic;
    private final float alphaOmega;
    private final float deadband;

    private final float[] g = new float[3];
    private final float[] m = new float[3];
    private boolean haveG;
    private boolean haveM;

    private final float[] rot = new float[9];
    private final float[] prevRot = new float[9];
    private final float[] rawOmega = new float[3];
    private boolean havePrev;
    private long prevT;

    /** Smoothed angular velocity (rad/s) with the noise gate applied. */
    final float[] omega = new float[3];
    private final float[] omegaFiltered = new float[3];
    /** Orientation as unit quaternion x, y, z, w. */
    final float[] quat = new float[4];

    GyroEstimator() {
        this(0.10f, 0.10f, 0.20f, 0.08f);
    }

    GyroEstimator(float alphaGravity, float alphaMagnetic, float alphaOmega, float deadband) {
        this.alphaGravity = alphaGravity;
        this.alphaMagnetic = alphaMagnetic;
        this.alphaOmega = alphaOmega;
        this.deadband = deadband;
    }

    void onMag(float[] v) {
        if (!haveM) {
            System.arraycopy(v, 0, m, 0, 3);
            haveM = true;
        } else {
            for (int i = 0; i < 3; i++) {
                m[i] += alphaMagnetic * (v[i] - m[i]);
            }
        }
    }

    /** Feed one accelerometer sample (timestamp in ns). Returns true when omega/quat were updated. */
    boolean onAccel(long tNs, float[] v) {
        if (!haveG) {
            System.arraycopy(v, 0, g, 0, 3);
            haveG = true;
        } else {
            for (int i = 0; i < 3; i++) {
                g[i] += alphaGravity * (v[i] - g[i]);
            }
        }
        if (!haveM) {
            return false;
        }
        if (!FusionMath.rotationMatrix(g, m, rot)) {
            return false;
        }
        if (havePrev) {
            double dt = (tNs - prevT) * 1e-9;
            if (dt >= 0.002 && dt <= 0.25) {
                FusionMath.angularVelocity(prevRot, rot, dt, rawOmega);
                for (int i = 0; i < 3; i++) {
                    float w = rawOmega[i];
                    if (Float.isNaN(w) || Float.isInfinite(w)) {
                        w = 0f;
                    }
                    // an impossible spin means a magnetic glitch, not real motion
                    if (w > 35f) {
                        w = 35f;
                    } else if (w < -35f) {
                        w = -35f;
                    }
                    omegaFiltered[i] += alphaOmega * (w - omegaFiltered[i]);
                }
            } else {
                omegaFiltered[0] = 0f;
                omegaFiltered[1] = 0f;
                omegaFiltered[2] = 0f;
            }
        }
        System.arraycopy(rot, 0, prevRot, 0, 9);
        prevT = tNs;
        havePrev = true;

        float n = (float) Math.sqrt(omegaFiltered[0] * omegaFiltered[0]
                + omegaFiltered[1] * omegaFiltered[1] + omegaFiltered[2] * omegaFiltered[2]);
        if (n < deadband) {
            omega[0] = 0f;
            omega[1] = 0f;
            omega[2] = 0f;
        } else {
            System.arraycopy(omegaFiltered, 0, omega, 0, 3);
        }
        FusionMath.quaternion(rot, quat);
        return true;
    }
}
