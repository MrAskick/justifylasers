package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.MathHelper;

final class LaserBeamProfile {
    static final double EMISSION_RADIUS = 0.035D;
    static final double[] COLOR_RADII = {
            0.0D, 0.030D, 0.055D, 0.086D, 0.125D, 0.174D, 0.232D, 0.300D
    };
    static final int[] COLOR_ALPHA = {255, 255, 224, 174, 116, 62, 22, 0};
    static final double[] COLOR_BRIGHTNESS = {1.0D, 1.0D, 0.93D, 0.76D, 0.53D, 0.31D, 0.12D, 0.0D};

    static final double[] CORE_RADII = {0.0D, 0.009D, 0.017D, 0.031D, 0.058D};
    static final int[] CORE_ALPHA = {255, 255, 232, 82, 0};

    static int scaleAlpha(int alpha, float intensity) {
        return MathHelper.clamp(Math.round(alpha * intensity), 0, 255);
    }

    static int scaleRgb(int rgb, double brightness) {
        int red = MathHelper.clamp((int) Math.round(((rgb >> 16) & 0xFF) * brightness), 0, 255);
        int green = MathHelper.clamp((int) Math.round(((rgb >> 8) & 0xFF) * brightness), 0, 255);
        int blue = MathHelper.clamp((int) Math.round((rgb & 0xFF) * brightness), 0, 255);
        return (red << 16) | (green << 8) | blue;
    }

    private LaserBeamProfile() {
    }
}
