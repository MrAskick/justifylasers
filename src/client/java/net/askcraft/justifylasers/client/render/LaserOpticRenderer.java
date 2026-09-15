package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.EnumMap;
import java.util.Map;

public final class LaserOpticRenderer implements BlockEntityRenderer<LaserOpticBlockEntity> {
    private static final Map<LaserOpticBlock.Kind, LaserOpticBlockEntity> ITEM_MODELS = new EnumMap<>(LaserOpticBlock.Kind.class);

    public LaserOpticRenderer(BlockEntityRendererFactory.Context context) { }

    @Override
    public void render(LaserOpticBlockEntity optic, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        renderModel(optic, matrices, consumers, light);
    }

    public static void renderItem(LaserOpticBlock block, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        var optic = ITEM_MODELS.computeIfAbsent(block.kind(), kind -> new LaserOpticBlockEntity(BlockPos.ORIGIN,
                block.getDefaultState().with(LaserOpticBlock.FACING, kind == LaserOpticBlock.Kind.MIRROR ? Direction.UP : Direction.NORTH)));
        renderModel(optic, matrices, consumers, light);
    }

    private static void renderModel(LaserOpticBlockEntity optic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        try {
            matrices.translate(0.5, 0.5, 0.5);
            switch (optic.kind()) {
                case MIRROR -> LaserMirrorModel.render(optic, matrices, consumers, light);
                case SPLITTER -> BeamSplitterModel.render(optic, matrices, consumers, light);
                case ENERGY_RECEIVER -> EnergyReceiverModel.render(optic, matrices, consumers, light);
            }
        } finally {
            matrices.pop();
        }
    }
}
