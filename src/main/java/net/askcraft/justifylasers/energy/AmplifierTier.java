package net.askcraft.justifylasers.energy;

public final class AmplifierTier {
    public static final int MAX = 15;
    public static final long MAX_LUMENS = 1_000_000_000_000L;

    public static long lumens(int tier) {
        if (tier < 1 || tier > MAX) return 0;
        return Math.min(MAX_LUMENS, 1L << (3 * (tier - 1)));
    }

    public static long energy(int tier, int lumensPerEnergyUnit) {
        long flux = lumens(tier);
        int conversion = Math.max(1, lumensPerEnergyUnit);
        return (flux + conversion - 1) / conversion;
    }

    private AmplifierTier() { }
}
