package net.askcraft.justifylasers.platform;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GameVersion {
    public static ItemStack replaceItem(ItemStack stack, net.minecraft.item.Item replacement) {
        ItemStack result = new ItemStack(replacement, stack.getCount());
        if (stack.hasNbt()) result.setNbt(stack.getNbt().copy());
        return result;
    }
    public static boolean canStack(ItemStack a, ItemStack b) { return ItemStack.canCombine(a, b); }
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

    public static NbtCompound itemData(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().copy() : new NbtCompound();
    }

    public static void setItemData(ItemStack stack, NbtCompound data) { stack.setNbt(data.copy()); }

    public static ItemStack smelt(ItemStack stack, ServerWorld world) {
        if (stack.hasNbt()) return stack;
        var input = new net.minecraft.inventory.SimpleInventory(stack);
        return world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.SMELTING, input, world)
                .map(recipe -> recipe.craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    private GameVersion() {
    }
}
