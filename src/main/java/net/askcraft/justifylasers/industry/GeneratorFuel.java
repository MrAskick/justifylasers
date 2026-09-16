package net.askcraft.justifylasers.industry;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

/** Temperatures are stored in tenths of a degree; fuel duration remains the furnace duration. */
public record GeneratorFuel(int maxTemperature, int efficiency) {
    public static final int AMBIENT = 200;
    public static final int MAX_TEMPERATURE = 12_000;
    public static final int MAX_EFFICIENCY = 95;
    public static final long OUTPUT_DIVISOR = (long) (MAX_TEMPERATURE - AMBIENT) * MAX_EFFICIENCY;
    public static final GeneratorFuel LAVA = new GeneratorFuel(12_000, 95);
    public static final GeneratorFuel BLAZE = new GeneratorFuel(10_500, 90);
    public static final GeneratorFuel COAL = new GeneratorFuel(9_000, 85);
    public static final GeneratorFuel CHARCOAL = new GeneratorFuel(8_000, 80);
    public static final GeneratorFuel WOOD = new GeneratorFuel(6_000, 65);
    public static final GeneratorFuel KINDLING = new GeneratorFuel(3_000, 45);
    public static final GeneratorFuel PLANT = new GeneratorFuel(3_500, 45);
    public static final GeneratorFuel WOOL = new GeneratorFuel(4_000, 50);
    public static final GeneratorFuel KELP = new GeneratorFuel(5_500, 60);

    public GeneratorFuel {
        if (maxTemperature <= AMBIENT || maxTemperature > MAX_TEMPERATURE || efficiency < 1 || efficiency > MAX_EFFICIENCY)
            throw new IllegalArgumentException("Invalid generator fuel profile");
    }

    public static GeneratorFuel of(ItemStack stack) {
        if (stack.isOf(Items.LAVA_BUCKET)) return LAVA;
        if (stack.isOf(Items.BLAZE_ROD)) return BLAZE;
        if (stack.isOf(Items.COAL) || stack.isOf(Items.COAL_BLOCK)) return COAL;
        if (stack.isOf(Items.CHARCOAL)) return CHARCOAL;
        if (stack.isOf(Items.STICK) || stack.isOf(Items.BAMBOO) || stack.isOf(Items.SCAFFOLDING)) return KINDLING;
        if (stack.isIn(ItemTags.SAPLINGS) || stack.isOf(Items.DEAD_BUSH) || stack.isOf(Items.AZALEA) || stack.isOf(Items.FLOWERING_AZALEA)) return PLANT;
        if (stack.isIn(ItemTags.WOOL) || stack.isIn(ItemTags.WOOL_CARPETS)) return WOOL;
        if (stack.isOf(Items.DRIED_KELP_BLOCK)) return KELP;
        return WOOD;
    }

    public int approach(int temperature, int heatingStep, boolean burning) {
        int target = burning ? maxTemperature : AMBIENT;
        int step = temperature > target ? 3 * heatingStep : heatingStep;
        return temperature + Math.max(-step, Math.min(step, target - temperature));
    }

    public int efficiencyAt(int temperature) {
        return (int) ((long) usefulHeat(temperature) * efficiency / (maxTemperature - AMBIENT));
    }

    public long outputNumerator(int temperature, int peakRate) {
        // 128 FE/t is the net lava output at 1200 C / 95%, not a second pre-efficiency rating.
        return (long) peakRate * usefulHeat(temperature) * efficiency;
    }

    private int usefulHeat(int temperature) {
        // A hotter previous fuel must not let sticks sustain lava-level output.
        return Math.max(0, Math.min(maxTemperature, temperature) - AMBIENT);
    }
}
