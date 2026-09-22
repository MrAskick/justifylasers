package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.item.GrowthSeedItem;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

public final class GrowthSeedRecipe extends SpecialCraftingRecipe {
    public static final RecipeSerializer<GrowthSeedRecipe> SERIALIZER = new SpecialRecipeSerializer<>(GrowthSeedRecipe::new);
    public GrowthSeedRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }
    public static void initialize() {
        Platform.onRegister(RegistryKeys.RECIPE_SERIALIZER, () -> Platform.register(Registries.RECIPE_SERIALIZER, JustifyLasers.id("growth_seed"), SERIALIZER));
    }
    private static CrystalGrowth type(RecipeInputInventory input) {
        return GrowthSeedItem.craftingType(input.size(), input::getStack);
    }
    @Override public boolean matches(RecipeInputInventory input, World world) { return type(input) != null; }
    @Override public ItemStack craft(RecipeInputInventory input, DynamicRegistryManager registry) {
        var type = type(input);
        return type == null ? ItemStack.EMPTY : type.seed(0);
    }
    @Override public boolean fits(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
}
