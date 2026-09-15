package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;

import java.util.List;

public record MachineRecipeData(String key, MachineKind kind, String blueprint, List<Ingredient> inputs,
                                List<Integer> counts, ItemStack result, int ticks, int energy, int water) {
    public MachineRecipeData {
        inputs = List.copyOf(inputs); counts = List.copyOf(counts); result = result.copy();
        if (key.isBlank() || inputs.isEmpty() || inputs.size() > 4 || inputs.size() != counts.size()
                || counts.stream().anyMatch(count -> count < 1 || count > 64) || result.isEmpty()
                || ticks < 0 || ticks > 72_000 || energy < 0 || water < 0 || water > 64_000
                || kind != MachineKind.CRYSTAL_GROWER && water != 0 || kind == MachineKind.FUEL_GENERATOR)
            throw new IllegalArgumentException("Invalid industrial recipe: " + key);
    }
    public int duration() {
        if (ticks == 0) return kind.duration();
        return kind == MachineKind.ASSEMBLY_CHAMBER ? (int)Math.max(1, Math.min(72_000, (long)ticks * kind.duration() / 400)) : ticks;
    }
    public int rate() {
        if (energy == 0) return kind.rate();
        return kind == MachineKind.ASSEMBLY_CHAMBER ? (int)Math.max(1, Math.min(Integer.MAX_VALUE, (long)energy * kind.rate() / 160)) : energy;
    }
    public int waterCost() { return kind == MachineKind.CRYSTAL_GROWER && water == 0 ? net.askcraft.justifylasers.config.LaserConfig.get().crystalWaterPerRecipe : water; }
    public boolean matches(Inventory inventory) {
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (slot >= inputs.size() ? !stack.isEmpty() : !inputs.get(slot).test(stack) || stack.getCount() < counts.get(slot)) return false;
        }
        return true;
    }
    public int color(Inventory inventory) {
        for (int slot = 0; slot < inputs.size(); slot++) if (inventory.getStack(slot).getItem() instanceof LaserCrystalItem crystal) return crystal.color().ordinal();
        return LaserColor.WHITE.ordinal();
    }
    public ItemStack output(Inventory inventory) {
        ItemStack output = result.copy();
        if (output.isOf(ModBlocks.POWERED_LASER_EMITTER_ITEM)) {
            var data = GameVersion.itemData(output);
            data.putInt(IndustryRecipes.INSTALLED_CRYSTAL, color(inventory));
            GameVersion.setItemData(output, data);
        }
        return output;
    }
}
