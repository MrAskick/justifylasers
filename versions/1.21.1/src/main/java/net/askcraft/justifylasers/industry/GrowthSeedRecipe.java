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
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;

public final class GrowthSeedRecipe extends SpecialCraftingRecipe {
    public static final RecipeSerializer<GrowthSeedRecipe> SERIALIZER = new SpecialRecipeSerializer<>(GrowthSeedRecipe::new);
    public GrowthSeedRecipe(CraftingRecipeCategory category) { super(category); }
    public static void initialize() {
        Platform.onRegister(RegistryKeys.RECIPE_SERIALIZER, () -> Platform.register(Registries.RECIPE_SERIALIZER, JustifyLasers.id("growth_seed"), SERIALIZER));
    }
    private static CrystalGrowth type(CraftingRecipeInput input) {
        return GrowthSeedItem.craftingType(input.getSize(), input::getStackInSlot);
    }
    @Override public boolean matches(CraftingRecipeInput input, World world) { return type(input) != null; }
    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registry) {
        var type = type(input);
        return type == null ? ItemStack.EMPTY : type.seed(0);
    }
    @Override public boolean fits(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
}
