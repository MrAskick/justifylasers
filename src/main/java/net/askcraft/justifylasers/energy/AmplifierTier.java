package net.askcraft.justifylasers.energy;

import java.util.List;

public final class AmplifierTier {
    public static final int MAX = 6;
    public static final long MAX_LUMENS = 1_048_576_000L;
    // V–VI remain readable in saved stacks, but are not offered or craftable for now.
    public static final List<Integer> AVAILABLE = List.of(1, 2, 3, 4 /*, 5, 6 */);

    public static long lumens(int tier) {
        if (tier < 1 || tier > MAX) return 0;
        return 32_000L << (3 * (tier - 1));
    }

    public static long energy(int tier) {
        return tier < 1 || tier > MAX ? 0 : 34L << (3 * (tier - 1));
    }

    private AmplifierTier() { }
}
