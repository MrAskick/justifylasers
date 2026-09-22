package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.item.ItemStack;

/** Wear data and compatibility with pre-alpha.38 seeds. Origin tags are no longer a security boundary. */
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
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LegacyCrystalSeedItem old)
            return migrate(stack, old.crystal(), old.stage());
        var data = GameVersion.itemData(stack);
        int stage = data.getInt(STAGE);
        if (data.contains(STAGE) && stage >= 0 && stage <= 3 && !synthetic(stack))
            for (var crystal : CrystalGrowth.values()) if (stack.isOf(crystal.natural())) return migrate(stack, crystal, stage);
        return stack;
    }
    public static ItemStack migrate(ItemStack stack, CrystalGrowth crystal, int stage) {
        var result = GameVersion.replaceItem(stack, net.askcraft.justifylasers.registry.ModNutrients.GROWTH_SEED);
        var data = GameVersion.itemData(result);
        data.putInt(STAGE, stage);
        data.putString(net.askcraft.justifylasers.item.GrowthSeedItem.TYPE, crystal.id());
        GameVersion.setItemData(result, data);
        return result;
    }
    /** Retired origin/wear tags on cut gems break NBT-sensitive pipe and storage filters. */
    public static ItemStack cleanProduct(ItemStack stack) {
        for (var crystal : CrystalGrowth.values()) if (stack.isOf(crystal.natural())) {
            var data = GameVersion.itemData(stack);
            if (data.contains(SYNTHETIC) || data.contains(STAGE)) {
                data.remove(SYNTHETIC);
                data.remove(STAGE);
                GameVersion.setItemData(stack, data);
            }
            break;
        }
        return stack;
    }
    private CrystalSeed() { }
}
