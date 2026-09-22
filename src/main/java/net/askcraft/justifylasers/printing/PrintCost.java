package net.askcraft.justifylasers.printing;

public record PrintCost(int polymer, int ticks, int energyPerTick, int lumens) {
    public static PrintCost of(PrintDesign model) {
        int volume = model.occupiedVoxels(), detail = model.elements().size(), faces = model.faceCount();
        return new PrintCost(25 + (volume + 7) / 8 + detail * 2,
                100 + (volume + 7) / 8 + detail * 4,
                16 + Math.min(112, (faces + 15) / 16),
                8_000 + volume * 8 + Math.min(160_000, faces * 80));
    }
    public static boolean acceptsSpectrum(int rgb) {
        double r = (rgb >> 16 & 255) / 255d, g = (rgb >> 8 & 255) / 255d, b = (rgb & 255) / 255d;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        if (max < .2 || delta / max < .35) return false;
        double hue = 60 * (max == r ? (g - b) / delta : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4);
        if (hue < 0) hue += 360;
        return hue >= 260 && hue <= 305;
    }
}
