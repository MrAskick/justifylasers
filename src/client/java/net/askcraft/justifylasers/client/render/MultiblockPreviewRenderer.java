package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.compat.MultiblockConstruction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

public final class MultiblockPreviewRenderer {
    public static void render(DrawContext context, MultiblockConstruction recipe, int layer, int x, int y) {
        context.draw();
        var matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(x, y, 160);
            float size = recipe.width() == 3 ? 16 : 23;
            matrices.scale(size, -size, size);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(27));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(145));
            DiffuseLighting.enableGuiDepthLighting();
            RenderSystem.enableDepthTest();
            var client = MinecraftClient.getInstance();
            for (var cell : recipe.cells()) {
                if (layer >= 0 && cell.pos().getY() != layer) continue;
                matrices.push();
                matrices.translate(cell.pos().getX() - (recipe.width() - 1) * .5,
                        cell.pos().getY() - (recipe.layers() - 1) * .5, cell.pos().getZ() - (recipe.width() - 1) * .5);
                matrices.scale(.86F, .86F, .86F);
                client.getItemRenderer().renderItem(new ItemStack(cell.block()), ModelTransformationMode.NONE,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                        matrices, context.getVertexConsumers(), client.world, 0);
                matrices.pop();
            }
            context.draw();
        } finally {
            matrices.pop();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            DiffuseLighting.enableGuiDepthLighting();
        }
    }
    private MultiblockPreviewRenderer() { }
}
