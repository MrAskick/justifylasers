package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModNutrients;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Locale;

public enum CrystalGrowth {
    PHOTONITE(0xFFD45A, 620_000, 155_000, 600, 35, 60),
    AMETHYST(0xA24EFF, 960_000, 240_000, 800, 265, 295),
    EMERALD(0x20EE65, 2_480_000, 620_000, 1_200, 115, 155),
    DIAMOND(0x35DFFF, 2_480_000, 620_000, 1_600, 165, 200);

    private final int spectrum, reference, minimum, ticks, hueMin, hueMax;
    CrystalGrowth(int spectrum, int reference, int minimum, int ticks, int hueMin, int hueMax) {
        this.spectrum = spectrum; this.reference = reference; this.minimum = minimum; this.ticks = ticks;
        this.hueMin = hueMin; this.hueMax = hueMax;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public int spectrum() { return spectrum; }
    public int reference() { return reference; }
    public int minimum() { return minimum; }
    public int ticks() { return ticks; }
    public ProcessFluid nutrient() { return ProcessFluid.valueOf(name()); }
    public Item natural() {
        return switch (this) {
            case PHOTONITE -> ModIndustry.PHOTONITE_CRYSTAL;
            case AMETHYST -> Items.AMETHYST_SHARD;
            case EMERALD -> Items.EMERALD;
            case DIAMOND -> Items.DIAMOND;
        };
    }
    public Item grown() { return ModNutrients.GROWN.get(this); }
    public ItemStack seed(int stage) {
        if (stage < 0 || stage > 3) throw new IllegalArgumentException("Invalid seed stage");
        ItemStack stack = new ItemStack(natural());
        if (stage > 0) {
            var data = net.askcraft.justifylasers.platform.GameVersion.itemData(stack);
            data.putInt(CrystalSeed.STAGE, stage);
            net.askcraft.justifylasers.platform.GameVersion.setItemData(stack, data);
        }
        return stack;
    }
    public int stage(ItemStack stack) {
        if (stack.isEmpty() || CrystalSeed.synthetic(stack)) return -1;
        if (stack.isOf(natural())) {
            int value = net.askcraft.justifylasers.platform.GameVersion.itemData(stack).getInt(CrystalSeed.STAGE);
            return value >= 0 && value <= 3 ? value : -1;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LegacyCrystalSeedItem old && old.crystal() == this) return old.stage();
        return -1;
    }
    public ItemStack degraded(ItemStack seed, int roll) {
        int next = nextStage(stage(seed), roll);
        if (next < 0) return ItemStack.EMPTY;
        var result = net.askcraft.justifylasers.platform.GameVersion.replaceItem(seed, natural());
        result.setCount(1);
        var data = net.askcraft.justifylasers.platform.GameVersion.itemData(result);
        data.putInt(CrystalSeed.STAGE, next);
        net.askcraft.justifylasers.platform.GameVersion.setItemData(result, data);
        return result;
    }
    public static int nextStage(int stage, int roll) {
        if (stage < 0 || stage > 3 || roll < 0 || roll >= 1000) throw new IllegalArgumentException("Invalid seed wear roll");
        int next = stage + (roll < 800 ? 1 : roll < 950 ? 2 : roll < 995 ? 3 : 4);
        return next > 3 ? -1 : next;
    }

    public static int yield(int stage, int roll) {
        if (stage < 0 || stage > 3 || roll < 0 || roll >= 1000) throw new IllegalArgumentException("Invalid growth roll");
        int[] none = {0, 50, 250, 900}, triple = {5, 1, 0, 0}, doubleYield = {180, 70, 10, 0};
        if (roll < none[stage]) return 0;
        if (roll < none[stage] + triple[stage]) return 3;
        return roll < none[stage] + triple[stage] + doubleYield[stage] ? 2 : 1;
    }

    public static boolean matchesSpectrum(int actual, int expected) {
        for (var crystal : values()) if (crystal.spectrum == expected) return crystal.acceptsSpectrum(actual);
        return actual == expected;
    }

    public int hueMin() { return hueMin; }
    public int hueMax() { return hueMax; }
    public boolean acceptsSpectrum(int rgb) {
        double r = (rgb >> 16 & 255) / 255d, g = (rgb >> 8 & 255) / 255d, b = (rgb & 255) / 255d;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        if (max < .20 || delta / max < .35) return false;
        double hue = 60 * (max == r ? (g - b) / delta : max == g ? (b - r) / delta + 2 : (r - g) / delta + 4);
        if (hue < 0) hue += 360;
        return hue >= hueMin && hue <= hueMax;
    }

    public static CrystalGrowth byId(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
}
