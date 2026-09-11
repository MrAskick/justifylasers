package net.askcraft.justifylasers.platform;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GameVersion {
    public static Identifier id(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static void applySilkTouch(ItemStack stack, ServerWorld world) {
        stack.addEnchantment(Enchantments.SILK_TOUCH, 1);
    }

    public static int cubeColor(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getInt("Color") : 0;
    }

    public static void setCubeColor(ItemStack stack, int color) {
        stack.getOrCreateNbt().putInt("Color", color);
    }

    public static void setCustomName(ItemStack stack, Text name) {
        stack.setCustomName(name);
    }

    private GameVersion() {
    }
}
