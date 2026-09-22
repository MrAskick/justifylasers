package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.item.AssemblyBlueprintItem;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModIndustry;
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

public final class BlueprintClearingRecipe extends SpecialCraftingRecipe {
    public static final RecipeSerializer<BlueprintClearingRecipe> SERIALIZER = new SpecialRecipeSerializer<>(BlueprintClearingRecipe::new);
    public BlueprintClearingRecipe(CraftingRecipeCategory category) { super(category); }
    public static void initialize() {
        Platform.onRegister(RegistryKeys.RECIPE_SERIALIZER, () -> Platform.register(Registries.RECIPE_SERIALIZER, JustifyLasers.id("clear_blueprint"), SERIALIZER));
    }
    @Override public boolean matches(CraftingRecipeInput input, World world) {
        int count = 0;
        for (int i = 0; i < input.getSize(); i++) {
            var stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (AssemblyBlueprintItem.recipe(stack).isEmpty() || ++count > 1) return false;
        }
        return count == 1;
    }
    @Override public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registry) {
        return matches(input, null) ? new ItemStack(ModIndustry.BLANK_SCHEMATIC) : ItemStack.EMPTY;
    }
    @Override public boolean fits(int width, int height) { return width * height >= 1; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
}
