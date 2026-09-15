package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserWeapon;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class LaserTurretRenderer implements BlockEntityRenderer<LaserTurretBlockEntity> {
    public LaserTurretRenderer(BlockEntityRendererFactory.Context context) { }

    public static void renderItem(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        renderStand(matrices, consumers, light, 0xFC1717, 0);
    }

    private static void renderStand(MatrixStack matrices, VertexConsumerProvider consumers, int light, int color, float yaw) {
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.scale(1 / 16F, 1 / 16F, 1 / 16F);
        LaserGunModel.renderStand(matrices, consumers, light, color, yaw);
        matrices.pop();
    }

    @Override public void render(LaserTurretBlockEntity turret, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        renderStand(matrices, consumers, light, turret.color(), 180 - turret.yaw(delta));
        if (!turret.hasGun()) return;
        matrices.push();
        try {
            matrices.translate(0.5, 1.2, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - turret.yaw(delta)));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-turret.pitch(delta)));
            matrices.scale(1 / 32F, 1 / 32F, 1 / 32F);
            matrices.translate(0, -4.5, 0);
            LaserGunModel.render(matrices, consumers, light, turret.color());
        } finally { matrices.pop(); }
        if (turret.firing()) {
            net.askcraft.justifylasers.client.LaserSoundController.weapon(turret.getPos(), turret.pivot());
            Vec3d origin = Vec3d.of(turret.getPos()), muzzle = turret.muzzle(delta);
            var hit = LaserWeapon.trace(turret.getWorld(), turret.pivot(), turret.direction(delta), turret.beamRange(), null);
            if (hit.beam().length() < 0.8) return;
            LaserBeamTrace beam = new LaserBeamTrace(muzzle, hit.beam().end(), hit.beam().direction(), hit.beam().hitBlock(), hit.beam().hitSide());
            LaserBeamRenderer.render(beam, origin, turret.getPos(), 0, turret.getWorld().getTime() + delta,
                    turret.color(), 0.8, true, matrices, consumers);
        }
    }

    @Override public boolean rendersOutsideBoundingBox(LaserTurretBlockEntity turret) { return true; }
    @Override public int getRenderDistance() { return 256; }
}
