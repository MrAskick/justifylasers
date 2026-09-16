package net.askcraft.justifylasers.laser;

import java.util.Locale;

/** Optical power is a flux in lumens, not a second energy buffer. */
public final class LuminousFlux {
    public static final long MAX = 1_000_000_000_000_000L;

    public static long clamp(long lumens) { return Math.max(0, Math.min(MAX, lumens)); }

    public static long fromEnergyRate(int rate, int lumensPerEnergyUnit) {
        return clamp((long) Math.max(0, rate) * lumensPerEnergyUnit);
    }

    public static int toEnergyRate(long lumens, int lumensPerEnergyUnit, double efficiency) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(clamp(lumens) / (double) lumensPerEnergyUnit * efficiency));
    }

    public static long share(long budget, double fraction) {
        if (!Double.isFinite(fraction) || fraction <= 0) return 0;
        return Math.min(clamp(budget), (long) Math.floor(clamp(budget) * Math.min(1, fraction)));
    }

    public static String format(long lumens) {
        long value = clamp(lumens);
        if (value < 1_000) return value + " lm";
        double divisor = value >= 1_000_000_000 ? 1_000_000_000 : value >= 1_000_000 ? 1_000_000 : 1_000;
        String unit = divisor == 1_000_000_000 ? "Glm" : divisor == 1_000_000 ? "Mlm" : "klm";
        // Do not round 999.999 klm up to a misleading 1000 klm at a prefix boundary.
        double scaled = Math.floor(value / divisor * 100) / 100;
        return String.format(Locale.ROOT, "%.2f", scaled).replaceAll("\\.?0+$", "") + " " + unit;
    }

    private LuminousFlux() { }
}
