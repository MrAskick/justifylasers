package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.laser.CubeOptics;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class RefocusingCubeRenderer extends EntityRenderer<RefocusingCubeEntity> {
    public RefocusingCubeRenderer(EntityRendererFactory.Context context) {
        super(context);
        shadowRadius = 0.5F;
    }

    @Override
    public void render(RefocusingCubeEntity cube, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        CubeOptics.Frame frame = cube.opticalFrame(tickDelta);
        Vec3d origin = cube.getLerpedPos(tickDelta);
        Vec3d localCenter = frame.center().subtract(origin);
        matrices.push();
        matrices.translate(localCenter.x, localCenter.y, localCenter.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, cube.prevYaw, cube.getYaw())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.lerp(tickDelta, cube.prevPitch, cube.getPitch())));
        RefocusingCubeModel.render(matrices, consumers, light, cube.getColor().rgb(), cube.isLit(), cube.emitsLight());
        matrices.pop();
        if (cube.isLit()) {
            CubeCoreRenderer.render(cube.getId(), frame.center(), origin, cube.getColor().rgb(), cube.emitsLight(),
                    cube.age + tickDelta, matrices, consumers);
        }
        super.render(cube, yaw, tickDelta, matrices, consumers, light);
    }

    @Override
    public Identifier getTexture(RefocusingCubeEntity entity) {
        return RefocusingCubeModel.METAL;
    }
}
