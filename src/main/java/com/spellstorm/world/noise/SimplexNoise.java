package com.spellstorm.world.noise;

/**
 * High-performance, self-contained, continuous 2D Simplex Noise implementation.
 * Features zero runtime object allocations and C^2 continuity across R^2.
 */
public final class SimplexNoise {

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    // 12 2D gradient vectors
    private static final int[] GRAD_X = {1, -1, 1, -1, 1, -1, 1, -1, 0, 0, 0, 0};
    private static final int[] GRAD_Y = {1, 1, -1, -1, 0, 0, 0, 0, 1, -1, 1, -1};

    private final short[] perm = new short[512];
    private final short[] permMod12 = new short[512];

    public SimplexNoise(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) {
            p[i] = i;
        }

        // Deterministic Fisher-Yates shuffle using SplitMix64
        long state = seed;
        for (int i = 255; i > 0; i--) {
            state += 0x9E3779B97F4A7C15L;
            long z = (state ^ (state >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            long rand = z ^ (z >>> 31);
            int j = (int) Math.floorMod(rand, i + 1);

            short tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }

        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = (short) (perm[i] % 12);
        }
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /**
     * Evaluates continuous 2D Simplex Noise at (xin, yin), returning values in [-1.0f, +1.0f].
     */
    public float eval(double xin, double yin) {
        double n0, n1, n2;

        // Skew input space to locate simplex cell
        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);

        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;

        // Determine simplex traversal triangle
        int i1, j1;
        if (x0 > y0) {
            i1 = 1;
            j1 = 0;
        } else {
            i1 = 0;
            j1 = 1;
        }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];

        // Radial kernel contributions (0.5 - r^2)^4
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0.0) {
            n0 = 0.0;
        } else {
            t0 *= t0;
            n0 = t0 * t0 * (GRAD_X[gi0] * x0 + GRAD_Y[gi0] * y0);
        }

        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0.0) {
            n1 = 0.0;
        } else {
            t1 *= t1;
            n1 = t1 * t1 * (GRAD_X[gi1] * x1 + GRAD_Y[gi1] * y1);
        }

        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0.0) {
            n2 = 0.0;
        } else {
            t2 *= t2;
            n2 = t2 * t2 * (GRAD_X[gi2] * x2 + GRAD_Y[gi2] * y2);
        }

        // Scaled to interval [-1.0, 1.0]
        float result = (float) (70.0 * (n0 + n1 + n2));
        if (result < -1.0f) return -1.0f;
        if (result > 1.0f) return 1.0f;
        return result;
    }
}
