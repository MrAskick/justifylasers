package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class IndustryRecipes {
    public static final String INSTALLED_CRYSTAL = "AssembledCrystal";

    public static boolean accepts(MachineKind kind, int slot, ItemStack stack) {
        if (stack.isEmpty() || slot < 0 || slot >= kind.inputs()) return false;
        return switch (kind) {
            case FUEL_GENERATOR -> fuelTicks(stack) > 0;
            case CRYSTAL_GROWER -> slot == 0 ? stack.isOf(ModIndustry.RAW_PHOTONIC_CRYSTAL) : stack.isOf(Items.QUARTZ);
            case ASSEMBLY_CHAMBER -> false;
        };
    }

    public static int fuelTicks(ItemStack stack) {
        return stack.isEmpty() ? 0 : AbstractFurnaceBlockEntity.createFuelTimeMap().getOrDefault(stack.getItem(), 0);
    }

    private IndustryRecipes() { }
}
