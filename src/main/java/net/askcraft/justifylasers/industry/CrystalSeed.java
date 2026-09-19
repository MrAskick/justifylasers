package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.item.ItemStack;

/** Item data keeps vanilla gems usable in ordinary recipes without letting them seed another growth cycle. */
public final class CrystalSeed {
    public static final String STAGE = "JustifyLasersSeedStage", SYNTHETIC = "JustifyLasersSynthetic";

    public static boolean synthetic(ItemStack stack) { return GameVersion.itemData(stack).getBoolean(SYNTHETIC); }
    public static ItemStack syntheticResult(ItemStack stack) {
        if (!stack.isEmpty()) {
            var data = GameVersion.itemData(stack);
            data.remove(STAGE);
            data.putBoolean(SYNTHETIC, true);
            GameVersion.setItemData(stack, data);
        }
        return stack;
    }
    public static ItemStack migrate(ItemStack stack) {
        if (!(stack.getItem() instanceof net.askcraft.justifylasers.item.LegacyCrystalSeedItem old)) return stack;
        var result = GameVersion.replaceItem(stack, old.crystal().natural());
        var data = GameVersion.itemData(result);
        data.putInt(STAGE, old.stage());
        GameVersion.setItemData(result, data);
        return result;
    }
    public static ItemStack crafted(ItemStack output, int size, java.util.function.IntFunction<ItemStack> input) {
        // Compressing and unpacking gems must not erase their origin or reset a worn seed.
        var item = output.getItem();
        if (item != net.minecraft.item.Items.DIAMOND && item != net.minecraft.item.Items.DIAMOND_BLOCK
                && item != net.minecraft.item.Items.EMERALD && item != net.minecraft.item.Items.EMERALD_BLOCK
                && item != net.minecraft.item.Items.AMETHYST_SHARD && item != net.minecraft.item.Items.AMETHYST_BLOCK
                && item != net.askcraft.justifylasers.registry.ModIndustry.PHOTONITE_CRYSTAL) return output;
        for (int slot = 0; slot < size; slot++) {
            var stack = input.apply(slot);
            if (synthetic(stack) || GameVersion.itemData(stack).getInt(STAGE) > 0) return syntheticResult(output.copy());
        }
        return output;
    }
    private CrystalSeed() { }
}
