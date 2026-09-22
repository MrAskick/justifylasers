package net.askcraft.justifylasers.smoke;

import mezz.jei.api.runtime.IJeiRuntime;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.MachineRecipeData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.slf4j.LoggerFactory;

import java.util.List;

final class IndustryJeiSmoke {
    static void tick(MinecraftClient client, int tick) {
        if (tick == 835) show("crystal_growth_chamber", 1);
        if (tick == 842) capture(client, "grower");
        if (tick == 846) show("assembly_chamber", 30);
        if (tick == 855) capture(client, "assembly");
        if (tick == 860) {
            client.setScreen(null);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("INDUSTRY_JEI_SMOKE_PASSED growth=1 assembly=30 visibleCategories=true");
        }
    }

    static void show(String id, int count) {
        show(id, MachineRecipeData.class, count);
    }

    static void showAmplifiers() {
        try {
            show("amplifier_upgrades", Class.forName("net.askcraft.justifylasers.client.compat.AmplifierJeiCategory$Upgrade"), net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE.size() - 1);
        } catch (ClassNotFoundException failure) { throw new AssertionError("Missing amplifier recipe category", failure); }
    }

    static void verifyPrompt8Variants() {
        try {
            var runtime = (IJeiRuntime) Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            var stacks = runtime.getIngredientManager().getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
            var blueprints = stacks.stream().filter(stack -> stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.ASSEMBLY_BLUEPRINT)).toList();
            var recipes = blueprints.stream().map(net.askcraft.justifylasers.item.AssemblyBlueprintItem::recipe).collect(java.util.stream.Collectors.toSet());
            if (!recipes.equals(net.askcraft.justifylasers.registry.ModIndustry.BLUEPRINTS.keySet()))
                throw new AssertionError("JEI lost configured schematic variants: " + recipes.size());
            if (stacks.stream().anyMatch(stack -> stack.getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem old && !old.recipe().isEmpty()))
                throw new AssertionError("Legacy schematic aliases are visible in JEI");
            long seeds = stacks.stream().filter(stack -> net.askcraft.justifylasers.item.GrowthSeedItem.type(stack) != null).count();
            var amplifiers = stacks.stream().filter(stack -> stack.isOf(net.askcraft.justifylasers.registry.ModLaserParts.AMPLIFIER))
                    .map(net.askcraft.justifylasers.item.LaserAmplifierItem::tier).sorted().toList();
            if (seeds != 4 || !amplifiers.equals(net.askcraft.justifylasers.energy.AmplifierTier.AVAILABLE))
                throw new AssertionError("Wrong JEI variants: seeds=" + seeds + ", amplifiers=" + amplifiers);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect JEI item variants", failure); }
    }

    static <T> void show(String id, Class<T> recipeClass, int count) {
        try {
            var runtime = (IJeiRuntime) Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            var manager = runtime.getRecipeManager();
            var type = manager.getRecipeType(JustifyLasers.id(id), recipeClass).orElseThrow();
            var recipes = manager.createRecipeLookup(type).get().toList();
            if (recipes.size() != count) throw new AssertionError("Wrong JEI recipe count for " + id + ": " + recipes.size());
            runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(type), recipes, List.of());
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect JEI runtime", failure); }
    }

    private static void capture(MinecraftClient client, String name) {
        if (client.currentScreen == null) throw new AssertionError("JEI did not open recipes");
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "industry-jei-" + name + ".png", client.getFramebuffer(), text -> { });
    }
}
