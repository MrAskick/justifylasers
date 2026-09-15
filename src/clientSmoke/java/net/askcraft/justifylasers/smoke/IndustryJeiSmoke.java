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
        if (tick == 846) show("assembly_chamber", 26);
        if (tick == 855) capture(client, "assembly");
        if (tick == 860) {
            client.setScreen(null);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("INDUSTRY_JEI_SMOKE_PASSED growth=1 assembly=26 visibleCategories=true");
        }
    }

    private static void show(String id, int count) {
        try {
            var runtime = (IJeiRuntime) Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
            var manager = runtime.getRecipeManager();
            var type = manager.getRecipeType(JustifyLasers.id(id), MachineRecipeData.class).orElseThrow();
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
