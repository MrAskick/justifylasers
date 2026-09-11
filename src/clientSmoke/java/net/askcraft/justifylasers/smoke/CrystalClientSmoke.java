package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class CrystalClientSmoke {
    private static int ticks;
    private static int stage;

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null || ++ticks < 35) return;
        ticks = 0;
        switch (stage++) {
            case 0 -> {
                GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1200, 900);
                client.options.pauseOnLostFocus = false;
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                client.setScreen(new Gallery());
                for (var item : ModLaserParts.CRYSTALS.values()) {
                    if (!client.getItemRenderer().getModel(new ItemStack(item), null, null, 0).isBuiltin()) {
                        throw new AssertionError("Crystal did not resolve to its 3D model: " + item);
                    }
                }
            }
            case 1 -> {
                ScreenshotRecorder.saveScreenshot(client.runDirectory, "crystals-gallery.png", client.getFramebuffer(), text -> { });
                client.reloadResources();
            }
            case 2 -> ScreenshotRecorder.saveScreenshot(client.runDirectory, "crystals-reloaded.png", client.getFramebuffer(), text -> { });
            default -> {
                LoggerFactory.getLogger("justifylasers-client-smoke").info("CRYSTAL_MODELS_SMOKE_PASSED");
                client.scheduleStop();
            }
        }
    }

    private static final class Gallery extends Screen {
        private Gallery() { super(Text.literal("Crystal models")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fillGradient(0, 0, width, height, 0xFF172432, 0xFF090D14);
            context.drawCenteredTextWithShadow(textRenderer, "JUSTIFY LASERS / CRYSTALS", width / 2, 12, 0xDFEFF8);
            int cellWidth = width / 3;
            int cellHeight = (height - 45) / 3;
            for (LaserColor color : LaserColor.values()) {
                int x = (color.ordinal() % 3) * cellWidth + cellWidth / 2;
                int y = 34 + (color.ordinal() / 3) * cellHeight;
                var matrices = context.getMatrices();
                matrices.push();
                matrices.translate(x - 48, y, 0);
                matrices.scale(6, 6, 6);
                context.drawItem(new ItemStack(ModLaserParts.CRYSTALS.get(color)), 0, 0);
                context.draw();
                matrices.pop();
                context.drawCenteredTextWithShadow(textRenderer, color.asString().toUpperCase(java.util.Locale.ROOT), x, y + 105, color.rgb());
            }
        }
    }

    private CrystalClientSmoke() { }
}
