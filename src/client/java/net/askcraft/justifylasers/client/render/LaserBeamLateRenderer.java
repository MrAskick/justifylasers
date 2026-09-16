package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws the halo and white core after shader composition to preserve their colors through
 * tone mapping. Alpha blending keeps saturated colors over bright skies; the solid tube
 * rendered earlier supplies lighting and reflections.
 */
public final class LaserBeamLateRenderer {
    private static final Map<BeamKey, QueuedBeam> QUEUED_BEAMS = new LinkedHashMap<>();
    private static final Map<Integer, QueuedCore> QUEUED_CORES = new LinkedHashMap<>();
    private static final Map<BeamKey, VanillaBeam> VANILLA_BEAMS = new LinkedHashMap<>();
    private static final Map<BeamKey, VanillaBeam> SABERS = new LinkedHashMap<>();

    static void queueSaber(Object source, int segment, LaserBeamTrace trace, int rgb) {
        SABERS.put(new BeamKey(source, segment), new VanillaBeam(trace, 0, rgb, SaberBladeRenderer.WIDTH));
    }

    public static void queueVanilla(Object source, int segment, LaserBeamTrace trace, float time, int rgb, double width) {
        VANILLA_BEAMS.put(new BeamKey(source, segment), new VanillaBeam(trace, time, rgb, width));
    }

    public static void queueCore(int entityId, Vec3d center, int rgb, float time) {
        QUEUED_CORES.put(entityId, new QueuedCore(center, rgb, time));
    }

    public static void queue(
            Object sourceKey,
            int segment,
            Vec3d start,
            Vec3d end,
            Vec3d axis,
            int rgb,
            float intensity,
            double widthScale
    ) {
        QUEUED_BEAMS.put(new BeamKey(sourceKey, segment), new QueuedBeam(
                start,
                end,
                axis,
                rgb,
                intensity,
                widthScale,
                BeamEndpointClip.atMirror(start, axis),
                BeamEndpointClip.atMirror(end, axis.negate())
        ));
    }

    public static void render(
            Camera camera,
            MatrixStack worldMatrices,
            Matrix4f worldProjection
    ) {
        if (QUEUED_BEAMS.isEmpty() && QUEUED_CORES.isEmpty() && VANILLA_BEAMS.isEmpty() && SABERS.isEmpty()
                && !SolarLightShaftRenderer.queued() && !LaserScorchRenderer.hasVisibleMarks()) {
            return;
        }

        try {
            // Without a valid depth mask the halo would show through blocks and held items.
            if (IrisCompatibility.isShaderPackInUse() && !IrisCompatibility.prepareFinalDepthMask()) {
                return;
            }

            Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorter savedVertexSorting = RenderSystem.getVertexSorting();

            RenderSystem.setProjectionMatrix(new Matrix4f(worldProjection), VertexSorter.BY_DISTANCE);
            RenderVersion.pushModelView(worldMatrices.peek().getPositionMatrix());

            try {
                if (!IrisCompatibility.isShaderPackInUse() && LaserScorchRenderer.hasVisibleMarks()) {
                    LaserRenderLayers.SHADER_BEAM_HALO.startDrawing();
                    try {
                        BufferBuilder soot = RenderVersion.beginQuads(VertexFormats.POSITION_COLOR);
                        LaserScorchRenderer.renderLate(soot, camera.getPos());
                        BufferRenderer.drawWithGlobalProgram(soot.end());
                    } finally {
                        LaserRenderLayers.SHADER_BEAM_HALO.endDrawing();
                    }
                }
                if (!IrisCompatibility.isShaderPackInUse() && !VANILLA_BEAMS.isEmpty()) {
                    // Entity buffers can flush additive layers before nearer block-entity models.
                    // Submit beams only once every opaque model has populated world depth.
                    var consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
                    consumers.draw();
                    MatrixStack beamMatrices = new MatrixStack();
                    for (var entry : VANILLA_BEAMS.entrySet()) {
                        VanillaBeam beam = entry.getValue();
                        LaserBeamRenderer.renderResolved(beam.trace(), camera.getPos(), entry.getKey().source(),
                                entry.getKey().segment(), beam.time(), beam.rgb(), beam.width(), false, beamMatrices, consumers);
                    }
                    consumers.draw();
                }
                if (QUEUED_BEAMS.isEmpty() && QUEUED_CORES.isEmpty() && SABERS.isEmpty() && !SolarLightShaftRenderer.queued()
                        && (!IrisCompatibility.isShaderPackInUse() || !LaserScorchRenderer.hasVisibleMarks())) return;
                LaserRenderLayers.SHADER_BEAM_HALO.startDrawing();
                try {
                    BufferBuilder builder = RenderVersion.beginQuads(VertexFormats.POSITION_COLOR);
                    Vec3d cameraPos = camera.getPos();

                    if (IrisCompatibility.isShaderPackInUse()) LaserScorchRenderer.renderLate(builder, cameraPos);
                    SolarLightShaftRenderer.render(builder,cameraPos);
                    for (QueuedBeam beam : QUEUED_BEAMS.values()) {
                        Vec3d side = screenSide(beam, cameraPos);
                        renderGradientRibbon(builder, beam, side, cameraPos, false);
                        renderGradientRibbon(builder, beam, side, cameraPos, true);
                    }
                    for (QueuedCore core : QUEUED_CORES.values()) {
                        CubeCoreRenderer.renderLate(builder, core.center(), cameraPos, core.rgb(), core.time());
                    }
                    for (VanillaBeam saber : SABERS.values()) SaberBladeRenderer.renderLate(builder, saber.trace(), saber.rgb(), cameraPos);

                    BufferRenderer.drawWithGlobalProgram(builder.end());
                } finally {
                    LaserRenderLayers.SHADER_BEAM_HALO.endDrawing();
                }
            } finally {
                RenderVersion.popModelView();
                RenderSystem.setProjectionMatrix(savedProjection, savedVertexSorting);
            }
        } finally {
            QUEUED_BEAMS.clear();
            QUEUED_CORES.clear();
            VANILLA_BEAMS.clear();
            SABERS.clear();
            SolarLightShaftRenderer.clear();
            LaserScorchRenderer.endFrame();
        }
    }

    private static Vec3d screenSide(QueuedBeam beam, Vec3d cameraPos) {
        Vec3d midpoint = beam.start().add(beam.end()).multiply(0.5D);
        Vec3d side = beam.axis().crossProduct(cameraPos.subtract(midpoint));
        if (side.lengthSquared() < 1.0E-8D) {
            Vec3d fallback = Math.abs(beam.axis().y) > 0.9D
                    ? new Vec3d(1.0D, 0.0D, 0.0D)
                    : new Vec3d(0.0D, 1.0D, 0.0D);
            side = beam.axis().crossProduct(fallback);
        }
        return side.normalize();
    }

    private static void renderGradientRibbon(
            BufferBuilder buffer,
            QueuedBeam beam,
            Vec3d side,
            Vec3d cameraPos,
            boolean core
    ) {
        float intensity = core ? 1.0F : beam.intensity();
        double[] radii = core ? LaserBeamProfile.CORE_RADII : LaserBeamProfile.COLOR_RADII;
        int[] alpha = core ? LaserBeamProfile.CORE_ALPHA : LaserBeamProfile.COLOR_ALPHA;
        // Place the optical profile on the viewer-facing surface of the opaque emitter tube.
        // Its own depth must not clip the white center after the world-depth resolve.
        Vec3d surfaceOffset = beam.axis().crossProduct(side)
                .multiply(-(LaserBeamProfile.EMISSION_RADIUS + 0.002D) * beam.widthScale());
        Vec3d start = beam.start().add(surfaceOffset);
        Vec3d end = beam.end().add(surfaceOffset);

        for (int i = 0; i < radii.length - 1; i++) {
            double innerRadius = radii[i] * beam.widthScale();
            double outerRadius = radii[i + 1] * beam.widthScale();
            int innerAlpha = LaserBeamProfile.scaleAlpha(
                    alpha[i], intensity * (core ? 1.0F : (float) LaserBeamProfile.COLOR_BRIGHTNESS[i])
            );
            int outerAlpha = LaserBeamProfile.scaleAlpha(
                    alpha[i + 1], intensity * (core ? 1.0F : (float) LaserBeamProfile.COLOR_BRIGHTNESS[i + 1])
            );
            int rgb = core ? 0xFFFFFF : beam.rgb();

            drawBand(buffer, beam, start, end, side,
                    innerRadius, outerRadius, rgb,
                    innerAlpha, outerAlpha, cameraPos);
            drawBand(buffer, beam, start, end, side,
                    -innerRadius, -outerRadius, rgb,
                    innerAlpha, outerAlpha, cameraPos);
        }
    }

    private static void drawBand(
            BufferBuilder buffer,
            QueuedBeam beam,
            Vec3d start,
            Vec3d end,
            Vec3d side,
            double innerOffset,
            double outerOffset,
            int rgb,
            int innerAlpha,
            int outerAlpha,
            Vec3d cameraPos
    ) {
        finalVertex(buffer, clipped(start.add(side.multiply(innerOffset)), beam.startClip()), cameraPos, rgb, innerAlpha);
        finalVertex(buffer, clipped(end.add(side.multiply(innerOffset)), beam.endClip()), cameraPos, rgb, innerAlpha);
        finalVertex(buffer, clipped(end.add(side.multiply(outerOffset)), beam.endClip()), cameraPos, rgb, outerAlpha);
        finalVertex(buffer, clipped(start.add(side.multiply(outerOffset)), beam.startClip()), cameraPos, rgb, outerAlpha);
    }

    private static Vec3d clipped(Vec3d point, BeamEndpointClip clip) { return clip == null ? point : clip.clip(point); }

    private static void finalVertex(
            BufferBuilder buffer,
            Vec3d worldPosition,
            Vec3d cameraPos,
            int rgb,
            int alpha
    ) {
        Vec3d position = worldPosition.subtract(cameraPos);
        RenderVersion.endVertex(buffer.vertex((float) position.x, (float) position.y, (float) position.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha));
    }

    private record QueuedBeam(
            Vec3d start,
            Vec3d end,
            Vec3d axis,
            int rgb,
            float intensity,
            double widthScale,
            BeamEndpointClip startClip,
            BeamEndpointClip endClip
    ) {
    }

    private record BeamKey(Object source, int segment) {
    }

    private record QueuedCore(Vec3d center, int rgb, float time) {
    }

    private record VanillaBeam(LaserBeamTrace trace, float time, int rgb, double width) { }

    private LaserBeamLateRenderer() {
    }
}
