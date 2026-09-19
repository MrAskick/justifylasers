package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.energy.AmplifierTier;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.IntFunction;

public final class LaserAmplifierItem extends Item {
    private static final String TIER = "AmplifierTier";

    public LaserAmplifierItem(Settings settings) { super(settings.maxCount(1)); }

    public static int tier(ItemStack stack) {
        if (!(stack.getItem() instanceof LaserAmplifierItem)) return 0;
        var data = GameVersion.itemData(stack);
        int tier = data.contains(TIER) ? data.getInt(TIER) : 1;
        return tier >= 1 && tier <= AmplifierTier.MAX ? tier : 0;
    }

    public static ItemStack stack(int tier) {
        if (tier < 1 || tier > AmplifierTier.MAX) throw new IllegalArgumentException("Invalid amplifier tier: " + tier);
        ItemStack stack = new ItemStack(ModLaserParts.AMPLIFIER);
        if (tier > 1) {
            var data = GameVersion.itemData(stack);
            data.putInt(TIER, tier);
            GameVersion.setItemData(stack, data);
        }
        return stack;
    }

    public static int upgradeTier(int width, int height, IntFunction<ItemStack> input) {
        if (width != 3 || height != 3 || !input.apply(4).isOf(ModLaserParts.CONTROL_CIRCUIT)) return 0;
        int tier = tier(input.apply(0));
        if (tier < 1 || tier >= AmplifierTier.MAX) return 0;
        for (int i = 0; i < 9; i++) if (i != 4 && tier(input.apply(i)) != tier) return 0;
        return tier + 1;
    }

    @Override public Text getName(ItemStack stack) {
        return Text.translatable("item.justifylasers.amplifier_module.level", tier(stack));
    }
}
