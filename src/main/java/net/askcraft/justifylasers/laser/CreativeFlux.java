package net.askcraft.justifylasers.laser;

/** Logarithmic control: zero, then 1 lm through 1 Tlm. */
public final class CreativeFlux {
    public static final int STEPS = 1_201;
    public static final int DEFAULT_STEP = 601;

    private CreativeFlux() { }

    public static int clamp(int step) { return Math.max(0, Math.min(STEPS, step)); }
    public static long lumens(int step) {
        int bounded = clamp(step);
        return bounded == 0 ? 0 : Math.round(Math.pow(10, 12 * (bounded - 1) / (double) (STEPS - 1)));
    }
    public static int migrate(int oldStep) {
        return oldStep <= 0 ? 0 : clamp(1 + (int) Math.round(900.0 * (Math.min(1000, oldStep) - 1) / 999));
    }
}
