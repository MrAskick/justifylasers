package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class SolarConcentratorPreviewRenderer {
    public static void render(DrawContext context, int x, int y, boolean active, boolean small) {
        context.draw();
        context.enableScissor(x + 61, y + 43, x + 147, y + 110);
        var matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(x + 107, y + 76, 160);
            float scale=small?44:14;
            matrices.scale(scale, -scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(148));
            DiffuseLighting.enableGuiDepthLighting();
            RenderSystem.enableDepthTest();
            var consumers = context.getVertexConsumers();
            if(small) SmallSolarConcentratorModel.render(null,0,matrices,consumers,LightmapTextureManager.MAX_LIGHT_COORDINATE);
            else {
                matrices.translate(0,-1.2,0);
                SolarConcentratorModel.render(matrices,consumers,LightmapTextureManager.MAX_LIGHT_COORDINATE,active,
                        net.minecraft.util.math.Direction.NORTH,0);
            }
            consumers.draw();
            if (active && !small) {
                LaserBeamRenderer.renderVanillaBody(consumers.getBuffer(LaserRenderLayers.BEAM_GLOW), matrices.peek().getPositionMatrix(),
                        new Vec3d(0, 0, -2.5), new Vec3d(0, 0, -3.7), new Vec3d(1, 0, 0), new Vec3d(0, 1, 0),
                        SolarConcentratorBlockEntity.BEAM_RGB, 1, 2);
                consumers.draw();
            }
        } finally {
            matrices.pop();
            context.disableScissor();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.defaultBlendFunc();
            DiffuseLighting.enableGuiDepthLighting();
        }
    }
    private SolarConcentratorPreviewRenderer() { }
}
