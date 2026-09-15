package net.askcraft.justifylasers.smoke;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.common.Internal;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.laser.LaserColor;
import net.minecraft.registry.Registries;
import net.minecraft.text.StringVisitable;

import java.util.List;

final class TooltipSmoke {
    static void verifyAndOpenGuide() {
        var runtime = Internal.getJeiRuntime();
        var recipes = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).includeHidden().get()
                .filter(recipe -> recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.getItemStack()
                        .map(stack -> Registries.ITEM.getId(stack.getItem()).getNamespace().equals(JustifyLasers.MOD_ID)).orElse(false)))
                .toList();
        if (recipes.isEmpty()) throw new AssertionError("JEI did not register the Justify Lasers guide");
        var crystal = recipes.stream().filter(recipe -> recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.getItemStack()
                .map(stack -> stack.isOf(ModLaserParts.CRYSTALS.get(LaserColor.BLUE))).orElse(false))).toList();
        String description = crystal.stream().flatMap(recipe -> recipe.getDescription().stream()).map(StringVisitable::getString)
                .collect(java.util.stream.Collectors.joining(" "));
        if (!description.contains("mixing") && !description.contains("смешив")) throw new AssertionError("JEI guide is not translated or is missing crystal mechanics");
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(RecipeTypes.INFORMATION), crystal, List.of());
    }

    private TooltipSmoke() { }
}
