package io.github.virtualgyro;

/** Pure math helpers (no Android dependencies). All matrices are row-major 3x3, device -> world. */
final class FusionMath {
    private FusionMath() {
    }

    /**
     * Same algorithm as SensorManager.getRotationMatrix(R, null, gravity, geomagnetic).
     * Rows of R are East, North and Up expressed in device coordinates.
     */
    static boolean rotationMatrix(float[] gravity, float[] geomagnetic, float[] r) {
        double ax = gravity[0], ay = gravity[1], az = gravity[2];
        double ex = geomagnetic[0], ey = geomagnetic[1], ez = geomagnetic[2];
        double hx = ey * az - ez * ay;
        double hy = ez * ax - ex * az;
        double hz = ex * ay - ey * ax;
        double normH = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (normH < 0.1) {
            return false;
        }
        double invH = 1.0 / normH;
        hx *= invH;
        hy *= invH;
        hz *= invH;
        double normA = Math.sqrt(ax * ax + ay * ay + az * az);
        if (normA < 1e-6) {
            return false;
        }
        double invA = 1.0 / normA;
        ax *= invA;
        ay *= invA;
        az *= invA;
        double mx = ay * hz - az * hy;
        double my = az * hx - ax * hz;
        double mz = ax * hy - ay * hx;
        r[0] = (float) hx;
        r[1] = (float) hy;
        r[2] = (float) hz;
        r[3] = (float) mx;
        r[4] = (float) my;
        r[5] = (float) mz;
        r[6] = (float) ax;
        r[7] = (float) ay;
        r[8] = (float) az;
        return true;
    }

    /**
     * Angular velocity (rad/s, device frame) that turns rOld into rNew over dt seconds.
     * D = rOld^T * rNew = exp([w]x * dt).
     */
    static void angularVelocity(float[] rOld, float[] rNew, double dt, float[] out) {
        double[] d = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) {
                    s += (double) rOld[k * 3 + i] * rNew[k * 3 + j];
                }
                d[i * 3 + j] = s;
            }
        }
        double sx = (d[7] - d[5]) * 0.5;
        double sy = (d[2] - d[6]) * 0.5;
        double sz = (d[3] - d[1]) * 0.5;
        double sinT = Math.sqrt(sx * sx + sy * sy + sz * sz);
        double cosT = (d[0] + d[4] + d[8] - 1.0) * 0.5;
        double theta = Math.atan2(sinT, cosT);
        double scale;
        if (sinT < 1e-9) {
            scale = 1.0 / dt;
        } else {
            scale = theta / (sinT * dt);
        }
        out[0] = (float) (sx * scale);
        out[1] = (float) (sy * scale);
        out[2] = (float) (sz * scale);
    }

    /** Rotation matrix to unit quaternion (x, y, z, w), w >= 0, as used by TYPE_ROTATION_VECTOR. */
    static void quaternion(float[] r, float[] q) {
        double m00 = r[0], m01 = r[1], m02 = r[2];
        double m10 = r[3], m11 = r[4], m12 = r[5];
        double m20 = r[6], m21 = r[7], m22 = r[8];
        double tr = m00 + m11 + m22;
        double x, y, z, w;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1.0) * 2.0;
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n > 0) {
            x /= n;
            y /= n;
            z /= n;
            w /= n;
        }
        if (w < 0) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }
        q[0] = (float) x;
        q[1] = (float) y;
        q[2] = (float) z;
        q[3] = (float) w;
    }
}
