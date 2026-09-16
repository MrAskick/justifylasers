package net.askcraft.justifylasers.smoke;

import mezz.jei.api.runtime.IJeiRuntime;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.compat.MultiblockConstruction;
import net.askcraft.justifylasers.industry.MachineRecipeData;
import net.minecraft.client.util.InputUtil;

import java.util.List;

final class Alpha24JeiSmoke {
    static void verifyMachine(String id, int count) {
        var runtime = runtime();
        var type = runtime.getRecipeManager().getRecipeType(JustifyLasers.id(id), MachineRecipeData.class).orElseThrow();
        if (runtime.getRecipeManager().createRecipeLookup(type).get().count() != count) throw new AssertionError("Incomplete JEI machine recipes: " + id);
    }
    static void solar(boolean middle) {
        var runtime = runtime();
        var manager = runtime.getRecipeManager();
        var type = manager.getRecipeType(JustifyLasers.id("multiblock_construction"), MultiblockConstruction.class).orElseThrow();
        var recipes = manager.createRecipeLookup(type).get().toList();
        if (recipes.size() != 3) throw new AssertionError("Missing multiblock construction guides");
        var recipe = recipes.stream().filter(r -> r.id().equals("solar_concentrator")).findFirst().orElseThrow();
        if (recipe.cells().size() != 31 || recipe.ingredients().stream().mapToInt(net.minecraft.item.ItemStack::getCount).sum() != 31)
            throw new AssertionError("Construction guide disagrees with the server structure");
        var category = manager.getRecipeCategory(type);
        if (middle) for (int i = 0; i < 2; i++)
            if (!category.handleInput(recipe, 220, 105, InputUtil.Type.MOUSE.createFromCode(0))) throw new AssertionError("Layer selector is not interactive");
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
    }
    private static IJeiRuntime runtime() {
        try { return (IJeiRuntime) Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null); }
        catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect JEI runtime", failure); }
    }
}
