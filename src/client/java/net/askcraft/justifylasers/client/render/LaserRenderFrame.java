package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;

public record LaserRenderFrame(ClientWorld world, float tickDelta, Camera camera, MatrixStack matrixStack,
                               VertexConsumerProvider consumers, Frustum frustum) {
}
