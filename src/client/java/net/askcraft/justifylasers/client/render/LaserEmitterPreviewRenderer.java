package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class LaserEmitterPreviewRenderer {
    public static void render(DrawContext context, LaserEmitterScreenHandler handler, int x, int y, float yaw) {
        context.draw();
        context.enableScissor(x + 61, y + 43, x + 147, y + 104);
        var matrices = context.getMatrices();
        matrices.push();
        try {
            matrices.translate(x + 88, y + 80, 160);
            matrices.scale(32, -32, 32);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            matrices.translate(-0.5, -0.5, -0.5);
            DiffuseLighting.enableGuiDepthLighting();
            RenderSystem.enableDepthTest();
            var state = ModBlocks.POWERED_LASER_EMITTER.getDefaultState()
                    .with(LaserEmitterBlock.FACING, Direction.EAST)
                    .with(LaserEmitterBlock.COLOR, handler.getColor())
                    .with(LaserEmitterBlock.LIT, handler.hasCrystal())
                    .with(LaserEmitterBlock.EMITTING_LIGHT, handler.hasCrystal() && handler.isLightEmissionEnabled());
            var consumers = context.getVertexConsumers();
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, consumers,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            consumers.draw();
            if (handler.hasCrystal()) {
                // GUI previews never enter the world's deferred/late render passes.
                LaserBeamRenderer.renderVanillaBody(consumers.getBuffer(LaserRenderLayers.BEAM_GLOW),
                        matrices.peek().getPositionMatrix(), new Vec3d(0.647, 0.5, 0.5), new Vec3d(2.7, 0.5, 0.5),
                        new Vec3d(0, 1, 0), new Vec3d(0, 0, 1), handler.getColor().rgb(), 1,
                        Math.max(0.25, Math.min(1.3, Math.sqrt(handler.getBeamWidthScale()))));
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

    private LaserEmitterPreviewRenderer() {
    }
}
