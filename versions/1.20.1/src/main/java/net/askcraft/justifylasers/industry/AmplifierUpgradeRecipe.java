package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class AmplifierUpgradeRecipe extends SpecialCraftingRecipe {
    public static final RecipeSerializer<AmplifierUpgradeRecipe> SERIALIZER = new SpecialRecipeSerializer<>(AmplifierUpgradeRecipe::new);

    public AmplifierUpgradeRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }
    public static void initialize() {
        Platform.onRegister(RegistryKeys.RECIPE_SERIALIZER, () -> Platform.register(Registries.RECIPE_SERIALIZER, JustifyLasers.id("amplifier_upgrade"), SERIALIZER));
    }
    @Override public boolean matches(RecipeInputInventory input, World world) { return tier(input) > 0; }
    @Override public ItemStack craft(RecipeInputInventory input, DynamicRegistryManager registry) {
        int tier = tier(input);
        return tier > 0 ? LaserAmplifierItem.stack(tier) : ItemStack.EMPTY;
    }
    private static int tier(RecipeInputInventory input) { return LaserAmplifierItem.upgradeTier(input.getWidth(), input.getHeight(), input::getStack); }
    @Override public boolean fits(int width, int height) { return width == 3 && height == 3; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
}
