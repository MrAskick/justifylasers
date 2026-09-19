package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.item.ItemStack;

public final class LaserSpectrum {
    public static final int DEFAULT = 0xFFFFFF;
    public static int color(ItemStack stack) {
        var data = GameVersion.itemData(stack);
        return data.contains("SpectrumRgb") ? data.getInt("SpectrumRgb") & 0xFFFFFF : DEFAULT;
    }
    public static void color(ItemStack stack, int rgb) {
        var data = GameVersion.itemData(stack); data.putInt("SpectrumRgb", rgb & 0xFFFFFF); GameVersion.setItemData(stack, data);
    }
    public static int parse(String value) {
        return value != null && value.matches("[0-9a-fA-F]{6}") ? Integer.parseInt(value, 16) : -1;
    }
    private LaserSpectrum() { }
}
