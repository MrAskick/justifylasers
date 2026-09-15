package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.askcraft.justifylasers.laser.LaserBeamPath;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class LaserConfiguratorPreview {
    public static void render(LaserRenderFrame context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || context.consumers() == null) return;
        ItemStack stack = client.player.getMainHandStack();
        if (!(stack.getItem() instanceof LaserConfiguratorItem)) stack = client.player.getOffHandStack();
        if (!(stack.getItem() instanceof LaserConfiguratorItem)) return;
        BlockPos pos = client.crosshairTarget instanceof BlockHitResult hit ? hit.getBlockPos() : null;
        var data = GameVersion.itemData(stack);
        if ((pos == null || !(context.world().getBlockEntity(pos) instanceof LaserEmitterBlockEntity)) && data.contains("Preview")
                && data.getString("Dimension").equals(context.world().getRegistryKey().getValue().toString())) pos = BlockPos.fromLong(data.getLong("Preview"));
        if (pos == null || !context.world().isChunkLoaded(pos)
                || !(context.world().getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter) || !emitter.canAccess(client.player)
                || client.player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64 * 64) return;
        var matrices = context.matrixStack();
        matrices.push();
        try {
            Vec3d camera = context.camera().getPos();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
            for (var ray : LaserBeamPath.preview(emitter, context.tickDelta()).segments()) {
                if (ray.length() < 1e-5) continue;
                line(lines, matrices, ray.start(), ray.end(), 0xFF80EEFF);
                if (ray.hasBlockHit()) {
                    Vec3d side = ray.axis().crossProduct(new Vec3d(0, 1, 0));
                    if (side.lengthSquared() < 1e-5) side = new Vec3d(1, 0, 0);
                    side = side.normalize().multiply(0.09);
                    Vec3d up = ray.axis().crossProduct(side);
                    Vec3d end = ray.end().subtract(ray.axis().multiply(0.006));
                    line(lines, matrices, end.subtract(side), end.add(side), 0xFFFFCF70);
                    line(lines, matrices, end.subtract(up), end.add(up), 0xFFFFCF70);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    private static void line(VertexConsumer buffer, MatrixStack matrices, Vec3d start, Vec3d end, int color) {
        Vec3d normal = end.subtract(start).normalize();
        for (Vec3d point : new Vec3d[]{start, end}) {
            RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                    (float) point.x, (float) point.y, (float) point.z).color(color), matrices.peek().getNormalMatrix(),
                    (float) normal.x, (float) normal.y, (float) normal.z));
        }
    }

    private LaserConfiguratorPreview() { }
}
