package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.screen.TabletScreen;
import net.askcraft.justifylasers.industry.IndustryRecipe;
import net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

import java.util.List;
import java.util.Locale;

final class TabletDisplay {
    static void render(ItemStack tablet, int charge, MatrixStack matrices, VertexConsumerProvider consumers, boolean held) {
        var client = MinecraftClient.getInstance();
        TabletScreen screen = held && client.currentScreen instanceof TabletScreen current ? current : null;
        rect(matrices, consumers, 0, 0, 480, 300, 0, charge > 0 ? 0x031E30 : 0x090D13);
        if (!held) {
            text(matrices, consumers, Text.literal("LASER OS"), 38, 52, 410, charge > 0 ? 0x5EEBFF : 0x46545D, 4);
            text(matrices, consumers, Text.literal(charge * 100 / ExtraterrestrialTabletItem.CAPACITY + "%"), 38, 187, 410, 0x669CAC, 3);
            return;
        }
        if (charge <= 0) {
            text(matrices, consumers, Text.literal("LASER OS"), 18, 16, 240, 0x46545D, 1.4F);
            text(matrices, consumers, label("discharged"), 104, 133, 292, 0xADBBC7, 1.2F);
            text(matrices, consumers, label("charge_hint"), 42, 157, 405, 0x627480, .85F);
            return;
        }
        for (int x = 0; x < 480; x += 24) rect(matrices, consumers, x, 0, 1, 300, -.02, 0x083044);
        for (int y = 0; y < 300; y += 24) rect(matrices, consumers, 0, y, 480, 1, -.02, 0x083044);
        text(matrices, consumers, Text.literal("LASER OS"), 13, 13, 275, 0x5EEBFF, 1.75F);
        long minutes = client.world == null ? 0 : Math.floorMod(client.world.getTimeOfDay() + 6000, 24000) * 60 / 1000;
        text(matrices, consumers, Text.literal(charge * 100 / ExtraterrestrialTabletItem.CAPACITY + "%"), 353, 17, 52, 0x8EEEDD, 1);
        text(matrices, consumers, Text.literal(String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)), 426, 17, 48, 0x5EEBFF, 1);
        rect(matrices, consumers, 329, 17, 17, 8, -.04, 0x136582);
        rect(matrices, consumers, 331, 19, 13d * charge / ExtraterrestrialTabletItem.CAPACITY, 4, -.06, 0x88F4DC);
        rect(matrices, consumers, 10, 39, 460, 1, -.04, 0x146781);
        var blueprints = screen == null ? List.copyOf(ModIndustry.BLUEPRINTS.values()) : screen.getScreenHandler().blueprints();
        int selected = screen == null ? 0 : screen.getScreenHandler().selected(), first = screen == null ? 0 : screen.firstVisible();
        for (int row = 0; row < 6 && first + row < blueprints.size(); row++) {
            int index = first + row, y = 50 + row * 31;
            panel(matrices, consumers, 12, y, 156, 27, index == selected);
            var output = blueprints.get(index).output(client.world);
            item(matrices, consumers, output, 28, y + 13, 31, 0, 0);
            text(matrices, consumers, output.getName(), 44, y + 10, 118, index == selected ? 0xC8FAFF : 0x6DCDE3, .87F);
        }
        panel(matrices, consumers, 12, 259, 74, 28, false);
        panel(matrices, consumers, 94, 259, 74, 28, false);
        text(matrices, consumers, Text.literal("<"), 43, 268, 24, 0x85E8FA, 1.1F);
        text(matrices, consumers, Text.literal(">"), 125, 268, 24, 0x85E8FA, 1.1F);
        var blueprint = blueprints.get(selected);
        var output = blueprint.output(client.world);
        panel(matrices, consumers, 180, 49, 288, 180, false);
        text(matrices, consumers, output.getName(), 189, 57, 270, 0x8DF0FF, 1);
        item(matrices, consumers, output, 326, 144, 150, screen == null ? -28 : screen.previewYaw(), screen == null ? 18 : screen.previewPitch());
        if (screen != null && screen.getScreenHandler().assemblyTicks() > 0)
            text(matrices, consumers, Text.literal(screen.getScreenHandler().assemblyTicks() / 20F + " s  /  " + screen.getScreenHandler().assemblyRate()
                    + " " + net.askcraft.justifylasers.platform.Platform.ENERGY_UNIT + "/t"), 190, 215, 267, 0x669CAC, .85F);
        else if (screen == null && client.world != null) IndustryRecipe.all(client.world).stream().filter(recipe -> blueprint.recipe().equals(recipe.blueprint())).findFirst().ifPresent(recipe ->
                text(matrices, consumers, Text.literal(recipe.duration() / 20F + " s  /  " + recipe.rate() + " " + net.askcraft.justifylasers.platform.Platform.ENERGY_UNIT + "/t"),
                        190, 215, 267, 0x669CAC, .85F));
        text(matrices, consumers, screen == null ? label("interact") : label("status." + screen.getScreenHandler().status().name().toLowerCase(Locale.ROOT)),
                184, 239, 282, 0x89D7E1, .85F);
        text(matrices, consumers, Text.translatable("gui.justifylasers.tablet.blanks", screen == null ? 0 : screen.getScreenHandler().blanks()),
                184, 263, 150, 0x8DBBC8, .9F);
        text(matrices, consumers, Text.literal(ExtraterrestrialTabletItem.WRITE_COST + " " + net.askcraft.justifylasers.platform.Platform.ENERGY_UNIT),
                184, 277, 146, 0x4B8BA4, .8F);
        panel(matrices, consumers, 344, 259, 124, 28, screen != null && screen.getScreenHandler().blanks() > 0 && charge >= ExtraterrestrialTabletItem.WRITE_COST);
        text(matrices, consumers, label("record"), 354, 269, 106, 0xA2F2F8, 1);
    }

    private static Text label(String key) { return Text.translatable("gui.justifylasers.tablet." + key); }
    private static void rect(MatrixStack matrices, VertexConsumerProvider consumers, double x, double y, double w, double h, double depth, int color) {
        TabletRenderer.rectangle(matrices, consumers, x, y, w, h, depth, color);
    }
    private static void panel(MatrixStack matrices, VertexConsumerProvider consumers, int x, int y, int w, int h, boolean selected) {
        rect(matrices, consumers, x, y, w, h, -.1, selected ? 0x31DDF4 : 0x126680);
        rect(matrices, consumers, x + 1, y + 1, w - 2, h - 2, -.12, selected ? 0x06566D : 0x04263A);
    }
    private static void text(MatrixStack matrices, VertexConsumerProvider consumers, Text text, float x, float y, int width, int color, float size) {
        var renderer = MinecraftClient.getInstance().textRenderer;
        float scale = Math.min(size, width / (float)Math.max(1, renderer.getWidth(text)));
        matrices.push(); matrices.translate(x, y, -3); matrices.scale(scale, scale, scale);
        if (!TabletTextRenderer.queue(text, color, matrices.peek().getPositionMatrix()))
            renderer.draw(text, 0, 0, color, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL,
                    0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }
    private static void item(MatrixStack matrices, VertexConsumerProvider consumers, ItemStack item, float x, float y, float size, float yaw, float pitch) {
        // Both Y and Z reverse in screen space. Reversing Y alone makes solid models inside-out and reverses drag rotation.
        matrices.push(); matrices.translate(x, y, -4); matrices.scale(size, -size, -.15F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        var client = MinecraftClient.getInstance();
        client.getItemRenderer().renderItem(item, ModelTransformationMode.GUI, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, matrices, consumers, client.world, 0);
        matrices.pop();
    }
    private TabletDisplay() { }
}
