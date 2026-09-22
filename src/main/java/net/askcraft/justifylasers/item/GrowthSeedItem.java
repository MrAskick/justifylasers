package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.industry.CrystalSeed;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModNutrients;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.IntFunction;

/** A consumable growth substrate; recycled gems alone cannot replenish it. */
public final class GrowthSeedItem extends Item {
    public static final String TYPE = "GrowthCrystal";
    public GrowthSeedItem(Settings settings) { super(settings); }
    public static CrystalGrowth type(ItemStack stack) {
        if (!stack.isOf(ModNutrients.GROWTH_SEED)) return null;
        String id = GameVersion.itemData(stack).getString(TYPE);
        for (var crystal : CrystalGrowth.values()) if (crystal.id().equals(id)) return crystal;
        return null;
    }
    public static ItemStack stack(CrystalGrowth crystal, int stage) {
        if (stage < 0 || stage > 3) throw new IllegalArgumentException("Invalid seed stage: " + stage);
        var stack = new ItemStack(ModNutrients.GROWTH_SEED);
        var data = GameVersion.itemData(stack);
        data.putString(TYPE, crystal.id());
        data.putInt(CrystalSeed.STAGE, stage);
        GameVersion.setItemData(stack, data);
        return stack;
    }
    public static CrystalGrowth craftingType(int size, IntFunction<ItemStack> input) {
        CrystalGrowth type = null;
        int substrate = 0;
        for (int i = 0; i < size; i++) {
            var stack = input.apply(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(ModIndustry.RAW_PHOTONIC_CRYSTAL)) { if (++substrate != 1) return null; continue; }
            if (type != null) return null;
            for (var crystal : CrystalGrowth.values()) if (stack.isOf(crystal.natural())) { type = crystal; break; }
            if (type == null) return null;
        }
        return substrate == 1 ? type : null;
    }
    @Override public Text getName(ItemStack stack) {
        var type = type(stack);
        return type == null ? Text.translatable("item.justifylasers.growth_seed.unconfigured")
                : Text.translatable("item.justifylasers.growth_seed", type.natural().getName());
    }
}
