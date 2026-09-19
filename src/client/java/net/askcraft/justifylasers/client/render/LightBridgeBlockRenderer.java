package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.LightBridgeBlockEntity;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

public final class LightBridgeBlockRenderer implements BlockEntityRenderer<LightBridgeBlockEntity> {
    public LightBridgeBlockRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(LightBridgeBlockEntity bridge, float delta, MatrixStack matrices,
                                 VertexConsumerProvider consumers, int light, int overlay) {
        var span = LightBridgeNetwork.at(bridge.getWorld(), bridge.getPos());
        if (span != null && !span.origin().equals(bridge.getPos())) return;
        int width = span == null ? 1 : span.width();
        var orientation = bridge.orientation();
        var center = bridge.corner() ? orientation.cornerCenter(net.minecraft.util.math.BlockPos.ORIGIN)
                : orientation.center(net.minecraft.util.math.BlockPos.ORIGIN).add(orientation.acrossVector().multiply((width - 1) * .5));
        var forward = net.minecraft.util.math.Vec3d.of(bridge.facing().getVector());
        var up = orientation.normalVector();
        var right = forward.crossProduct(up);
        matrices.push();
        matrices.translate(center.x, center.y, center.z);
        matrices.multiply(new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(
                (float)right.x, (float)right.y, (float)right.z,
                (float)up.x, (float)up.y, (float)up.z,
                (float)-forward.x, (float)-forward.y, (float)-forward.z)));
        if (bridge.corner()) LightBridgeModel.renderCorner(right.dotProduct(orientation.acrossVector()) < 0,
                span == null ? 0x6A737C : span.rgb(), span != null && span.active(), matrices, consumers, light);
        else LightBridgeModel.render(width, span == null ? 0x6A737C : span.rgb(), span != null && span.active(), matrices, consumers, light);
        if (!bridge.corner() && orientation.hasFeed()) {
            matrices.push();
            if (up.dotProduct(net.minecraft.util.math.Vec3d.of(orientation.mount().getVector())) < 0)
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(180));
            LightBridgeModel.renderFeeds(width, span == null ? 0x6A737C : span.rgb(), span != null && span.active(), matrices, consumers, light);
            matrices.pop();
        }
        matrices.pop();
    }

    public static void renderItem(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        renderItem(matrices, consumers, light, false);
    }

    public static void renderItem(MatrixStack matrices, VertexConsumerProvider consumers, int light, boolean corner) {
        matrices.push();
        matrices.translate(.5, .5, .5);
        if (corner) LightBridgeModel.renderCorner(false, 0x55E6FF, true, matrices, consumers, light);
        else LightBridgeModel.render(1, 0x55E6FF, true, matrices, consumers, light);
        matrices.pop();
    }

    @Override public boolean rendersOutsideBoundingBox(LightBridgeBlockEntity bridge) { return true; }
    @Override public int getRenderDistance() { return 192; }
}
