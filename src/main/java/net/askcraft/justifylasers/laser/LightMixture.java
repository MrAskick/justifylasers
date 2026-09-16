package net.askcraft.justifylasers.laser;

/** Flux-weighted color in linear light, normalized to retain the beam's saturated emission. */
public final class LightMixture {
    private double weight, red, green, blue;

    public void add(int rgb, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        double blend = amount / (weight + amount);
        red += (linear(rgb >> 16) - red) * blend;
        green += (linear(rgb >> 8) - green) * blend;
        blue += (linear(rgb) - blue) * blend;
        weight += amount;
    }

    private static double linear(int channel) { return Math.pow((channel & 255) / 255d, 2.2); }
    private static int channel(double value) { return (int) Math.round(Math.pow(Math.max(0, Math.min(1, value)), 1 / 2.2) * 255); }
    public int rgb() {
        double peak = Math.max(red, Math.max(green, blue));
        if (peak == 0) return 0;
        return channel(red / peak) << 16 | channel(green / peak) << 8 | channel(blue / peak);
    }
    public double weight() { return weight; }
}
