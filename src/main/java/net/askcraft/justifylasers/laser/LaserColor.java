package net.askcraft.justifylasers.laser;

import net.minecraft.util.StringIdentifiable;
import org.joml.Vector3f;

import java.util.Locale;

public enum LaserColor implements StringIdentifiable {
    RED(0xFF0808, "gui.justifylasers.color.red"),
    ORANGE(0xFF5A00, "gui.justifylasers.color.orange"),
    YELLOW(0xFFE600, "gui.justifylasers.color.yellow"),
    GREEN(0x00FF3C, "gui.justifylasers.color.green"),
    CYAN(0x00EEFF, "gui.justifylasers.color.cyan"),
    BLUE(0x1250FF, "gui.justifylasers.color.blue"),
    VIOLET(0x8A18FF, "gui.justifylasers.color.violet"),
    MAGENTA(0xFF00C8, "gui.justifylasers.color.magenta"),
    WHITE(0xF8FCFF, "gui.justifylasers.color.white");

    private static final LaserColor[] VALUES = values();

    private final int rgb;
    private final String translationKey;

    LaserColor(int rgb, String translationKey) {
        this.rgb = rgb;
        this.translationKey = translationKey;
    }

    public int rgb() {
        return rgb;
    }

    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }

    public float red() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public float green() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public float blue() {
        return (rgb & 0xFF) / 255.0F;
    }

    public Vector3f vector() {
        return new Vector3f(red(), green(), blue());
    }

    public String translationKey() {
        return translationKey;
    }

    public LaserColor next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static LaserColor byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }

    public static LaserColor nearest(int rgb) {
        LaserColor closest = RED;
        int distance = Integer.MAX_VALUE;
        for (LaserColor candidate : VALUES) {
            int r = (rgb >> 16 & 255) - (candidate.rgb >> 16 & 255);
            int g = (rgb >> 8 & 255) - (candidate.rgb >> 8 & 255);
            int b = (rgb & 255) - (candidate.rgb & 255);
            int difference = r * r + g * g + b * b;
            if (difference < distance) {
                distance = difference;
                closest = candidate;
            }
        }
        return closest;
    }
}
