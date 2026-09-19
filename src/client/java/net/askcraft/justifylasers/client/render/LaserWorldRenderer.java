package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.LaserBeamSource;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserBeamPath;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.BeamContributions;
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
        LightBridgeRenderer.queue(context);
        LaserGunRenderer.renderBeams(context);
        LaserSaberRenderer.renderWorld(context);
        LaserConfiguratorPreview.render(context);
        MatrixStack matrices = context.matrixStack();
        var combined = new java.util.ArrayList<BeamContributions.Beam>();
        // Cull the optical path, not the emitter's chunk section: a long beam can cross the
        // camera while its source is far outside the terrain renderer's visible sections.
        for (Map.Entry<BlockPos, LaserBeamPath> entry : LaserBeamNetwork.paths(context.world(), context.tickDelta()).entrySet()) {
            if (!(context.world().getBlockEntity(entry.getKey()) instanceof LaserBeamSource emitter)) {
                continue;
            }
            Vec3d origin = Vec3d.of(entry.getKey());
            if (emitter instanceof net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity solar)
                SolarLightShaftRenderer.queue(solar,camera,context.tickDelta());
            float width = emitter.getBeamWidthScale();
            matrices.push();
            try {
                matrices.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
                for (int index = 0; index < entry.getValue().segments().size(); index++) {
                    LaserBeamTrace trace = entry.getValue().segments().get(index);
                    float segmentWidth = width * trace.behavior().widthMultiplier();
                    if (trace.combinedBy() != null) {
                        combined.add(new BeamContributions.Beam(trace, entry.getKey(), segmentWidth * (float) Math.sqrt(trace.power()),
                                emitter.isLightEmissionEnabled(), Math.max(1, emitter.luminousFlux()) * trace.power(), emitter.getTicks()));
                        continue;
                    }
                    if (context.frustum() != null && !context.frustum().isVisible(new Box(trace.start(), trace.end()).expand(segmentWidth * 0.35D + 0.25D))) {
                        continue;
                    }
                    LaserBeamRenderer.render(trace, origin, entry.getKey(), index,
                            emitter.getTicks() + context.tickDelta(), trace.rgb() >= 0 ? trace.rgb() : emitter.beamRgb(),
                            segmentWidth * (float) Math.sqrt(trace.power()),
                            emitter.isLightEmissionEnabled(), matrices, consumers);
                }
            } finally {
                matrices.pop();
            }
        }
        int index = 256;
        for (var beam : BeamContributions.merge(combined)) {
            var trace = beam.trace();
            if (context.frustum() != null && !context.frustum().isVisible(new Box(trace.start(), trace.end()).expand(beam.width() * .35 + .25))) continue;
            Vec3d origin = Vec3d.of(beam.source());
            matrices.push();
            try {
                matrices.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
                LaserBeamRenderer.render(trace, origin, beam.source(), index++, beam.ticks() + context.tickDelta(), trace.rgb(),
                        beam.width(), beam.emission(), matrices, consumers);
            } finally { matrices.pop(); }
        }
    }

    private LaserWorldRenderer() {
    }
}
