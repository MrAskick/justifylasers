package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserComponentBlock;
import net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public final class LaserComponentBlockRenderer implements BlockEntityRenderer<LaserComponentBlockEntity> {
    public LaserComponentBlockRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(LaserComponentBlockEntity part, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        if (part.formed() && (!(part instanceof SolarConcentratorBlockEntity source) || !source.operationalStructure())) return;
        var block = (LaserComponentBlock) part.getCachedState().getBlock();
        if (!part.formed() && block.solarMaterial()) return;
        matrices.push();
        try {
            matrices.translate(.5,.5,.5);
            if (part instanceof SolarConcentratorBlockEntity small && small.small()) {
                SmallSolarConcentratorModel.render(small,delta,matrices,consumers,light);
                return;
            }
            if (part instanceof SolarConcentratorBlockEntity source && source.formed()) {
                var origin = net.askcraft.justifylasers.industry.SolarStructure.origin(source);
                matrices.translate(origin.getX()+1-source.getPos().getX(),0,origin.getZ()+1-source.getPos().getZ());
                float tilt = SolarConcentratorModel.sunTilt(net.askcraft.justifylasers.industry.SolarExposure.sunDirection(source.getWorld(),delta));
                SolarConcentratorModel.render(matrices,consumers,light,source.isBeamActive(),source.outputSide(),tilt);
            } else {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-part.getCachedState().get(LaserComponentBlock.FACING).asRotation()));
                matrices.translate(-.5,-.5,-.5);
                LaserComponentRenderer.render(block.component(),ModelTransformationMode.NONE,matrices,consumers,light);
            }
        } finally { matrices.pop(); }
    }
    @Override public boolean rendersOutsideBoundingBox(LaserComponentBlockEntity part) { return part instanceof SolarConcentratorBlockEntity; }
    public Box getRenderBoundingBox(LaserComponentBlockEntity part) { return part.getRenderBoundingBox(); }
    @Override public int getRenderDistance() { return 128; }
}
