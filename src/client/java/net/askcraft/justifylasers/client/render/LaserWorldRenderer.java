package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserBeamPath;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Map;

public final class LaserWorldRenderer {
    public static void render(LaserRenderFrame context) {
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null || IrisCompatibility.isRenderingShadowPass()) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        // Cull the optical path, not the emitter's chunk section: a long beam can cross the
        // camera while its source is far outside the terrain renderer's visible sections.
        for (Map.Entry<BlockPos, LaserBeamPath> entry : LaserBeamNetwork.paths(context.world(), context.tickDelta()).entrySet()) {
            if (!(context.world().getBlockEntity(entry.getKey()) instanceof LaserEmitterBlockEntity emitter)) {
                continue;
            }
            Vec3d origin = Vec3d.of(entry.getKey());
            float width = emitter.getBeamWidthScale();
            matrices.push();
            try {
                matrices.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
                for (int index = 0; index < entry.getValue().segments().size(); index++) {
                    LaserBeamTrace trace = entry.getValue().segments().get(index);
                    if (context.frustum() != null && !context.frustum().isVisible(new Box(trace.start(), trace.end()).expand(width * 0.35D + 0.25D))) {
                        continue;
                    }
                    LaserBeamRenderer.render(trace, origin, entry.getKey(), index,
                            emitter.getTicks() + context.tickDelta(), emitter.getColor().rgb(), width,
                            emitter.isLightEmissionEnabled(), matrices, consumers);
                }
            } finally {
                matrices.pop();
            }
        }
    }

    private LaserWorldRenderer() {
    }
}
