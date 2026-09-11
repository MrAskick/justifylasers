package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.item.LaserPartItem;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class ModuleClientSmoke {
    private static int ticks;
    private static int stage;

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null || ++ticks < 35) return;
        ticks = 0;
        switch (stage++) {
            case 0 -> {
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1600, 1000);
                client.options.pauseOnLostFocus = false;
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                client.setScreen(new Gallery());
                for (var item : ModLaserParts.items()) {
                    if (!(item instanceof LaserPartItem) || !client.getItemRenderer().getModel(new ItemStack(item), null, null, 0).isBuiltin())
                        throw new AssertionError("Missing registered 3D part: " + item);
                }
            }
            case 1 -> {
                capture(client, "modules-gallery");
                client.reloadResources();
            }
            case 2 -> capture(client, "modules-reloaded");
            default -> {
                LoggerFactory.getLogger("justifylasers-client-smoke").info("MODULE_MODELS_SMOKE_PASSED");
                client.scheduleStop();
            }
        }
    }

    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), text -> { });
    }

    private static final class Gallery extends Screen {
        private final List<Item> items = List.of(
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.BLOCK_DESTRUCTION),
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.ENTITY_DAMAGE),
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.RANGE),
                ModLaserParts.ADVANCED_RANGE_MODULE,
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.THICKNESS),
                ModLaserParts.CONTROL_CIRCUIT,
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.SILK_TOUCH),
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.BLOCK_DROPS),
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.SCORCH_MARKS),
                ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.IGNITION));
        private final String[] labels = {"BLOCK DESTRUCTION", "ENTITY DAMAGE", "RANGE I", "RANGE II", "BEAM THICKNESS",
                "CONTROL CIRCUIT", "SILK TOUCH", "BLOCK DROPS", "SCORCH MARKS", "IGNITION"};

        private Gallery() { super(Text.literal("Module models")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fillGradient(0, 0, width, height, 0xFF172432, 0xFF090D14);
            context.drawCenteredTextWithShadow(textRenderer, "JUSTIFY LASERS / MODULES", width / 2, 15, 0xDFEFF8);
            int cellWidth = width / 5, cellHeight = (height - 50) / 2;
            for (int index = 0; index < items.size(); index++) {
                int x = (index % 5) * cellWidth + cellWidth / 2;
                int y = 48 + (index / 5) * cellHeight;
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(x - 64, y + 10, 0);
                matrices.scale(8, 8, 8);
                context.drawItem(new ItemStack(items.get(index)), 0, 0);
                context.draw();
                matrices.pop();
                context.drawCenteredTextWithShadow(textRenderer, labels[index], x, y + 161, 0x92CDE4);
            }
        }
    }

    private ModuleClientSmoke() { }
}
