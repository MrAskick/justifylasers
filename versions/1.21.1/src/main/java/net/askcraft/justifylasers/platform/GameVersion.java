package net.askcraft.justifylasers.platform;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class GameVersion {
    public static ItemStack replaceItem(ItemStack stack, net.minecraft.item.Item replacement) {
        return stack.copyComponentsToNewStack(replacement, stack.getCount());
    }
    public static boolean canStack(ItemStack a, ItemStack b) { return ItemStack.areItemsAndComponentsEqual(a, b); }
    public static Identifier id(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    public static void applySilkTouch(ItemStack stack, ServerWorld world) {
        stack.addEnchantment(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.SILK_TOUCH), 1);
    }

    public static int cubeColor(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getInt("Color");
    }

    public static void setCubeColor(ItemStack stack, int color) {
        NbtCompound data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        data.putInt("Color", color);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }

    public static void setCustomName(ItemStack stack, Text name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, name);
    }

    public static NbtCompound itemData(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }

    public static void setItemData(ItemStack stack, NbtCompound data) { stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data)); }

    public static ItemStack smelt(ItemStack stack, ServerWorld world) {
        if (stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_DATA)
                || stack.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) return stack;
        var input = new net.minecraft.recipe.input.SingleStackRecipeInput(stack);
        return world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.SMELTING, input, world)
                .map(entry -> entry.value().craft(input, world.getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    private GameVersion() {
    }
}
