package net.askcraft.justifylasers.energy;

public final class LaserEnergyCost {
    public record Rates(int basePerTick, int miningPerTick, double miningSpeedMultiplier,
                        double damagePerHealthPoint, double knockbackPerHit, double ignitionPerHit) {
        public Rates {
            if (basePerTick < 1 || miningPerTick < 0 || !Double.isFinite(miningSpeedMultiplier) || miningSpeedMultiplier < 1
                    || !Double.isFinite(damagePerHealthPoint) || damagePerHealthPoint < 0
                    || !Double.isFinite(knockbackPerHit) || knockbackPerHit < 0
                    || !Double.isFinite(ignitionPerHit) || ignitionPerHit < 0) {
                throw new IllegalArgumentException("Energy costs must be finite and non-negative; base >= 1, mining multiplier >= 1");
            }
        }
    }

    public static int perTick(Rates rates, boolean mining, int miningSpeed, boolean damage,
                              double damagePerHit, int hitsPerSecond, double knockback, boolean ignition, long moduleCost) {
        double result = (double) rates.basePerTick() + Math.max(0L, moduleCost);
        if (mining) {
            double speed = Math.max(0, Math.min(100, miningSpeed)) / 100.0D;
            result += rates.miningPerTick() * Math.pow(rates.miningSpeedMultiplier(), speed);
        }
        if (damage) {
            double hitsPerTick = Math.max(1, Math.min(20, hitsPerSecond)) / 20.0D;
            result += hitsPerTick * (Math.max(0, damagePerHit) * rates.damagePerHealthPoint()
                    + Math.max(0, knockback) * rates.knockbackPerHit()
                    + (ignition ? rates.ignitionPerHit() : 0));
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(result));
    }

    private LaserEnergyCost() {
    }
}
