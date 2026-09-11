package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LaserPartItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Map;

public final class LaserPartRenderer implements BlockEntityRenderer<LaserPartBlockEntity> {
    private final Map<String, ItemStack> stacks = new HashMap<>();

    public LaserPartRenderer(BlockEntityRendererFactory.Context context) { }

    public static void renderItem(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                  VertexConsumerProvider consumers, int light, int overlay) {
        if (stack.getItem() instanceof LaserCrystalItem) {
            LaserCrystalModel.render(stack, mode, matrices, consumers, light, overlay);
        } else if (stack.getItem() instanceof LaserPartItem part) {
            LaserModuleModel.render(part.partId(), mode, matrices, consumers, light, overlay);
        }
    }

    @Override
    public void render(LaserPartBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        var state = entity.getCachedState();
        if (!(state.getBlock() instanceof LaserPartBlock block)) return;
        ItemStack stack = stacks.computeIfAbsent(block.partId(), id -> new ItemStack(block));
        matrices.push();
        try {
            matrices.translate(0.5, 0, 0.5);
            float angle = switch (state.get(LaserPartBlock.FACING)) {
                case EAST -> 270;
                case SOUTH -> 180;
                case WEST -> 90;
                default -> 0;
            };
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            matrices.translate(-0.5, 0, -0.5);
            renderItem(stack, ModelTransformationMode.NONE, matrices, consumers, light, overlay);
        } finally {
            matrices.pop();
        }
    }
}
