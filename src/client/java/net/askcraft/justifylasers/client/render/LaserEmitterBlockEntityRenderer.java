package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LaserEmitterBlockEntityRenderer implements BlockEntityRenderer<LaserEmitterBlockEntity> {
    private static final double[] CORE_BRIGHTNESS = {1.0D, 1.0D, 1.0D, 1.0D, 1.0D};
    private static final int FLARE_SEGMENTS = 24;

    public LaserEmitterBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(
            LaserEmitterBlockEntity emitter,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            int overlay
    ) {
        if (emitter.getWorld() == null || IrisCompatibility.isRenderingShadowPass()) {
            return;
        }

        boolean shaderPack = IrisCompatibility.isShaderPackInUse();
        LaserColor laserColor = emitter.getColor();
        int colorRgb = laserColor.rgb();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (!emitter.isBeamActive()) {
            return;
        }

        LaserBeamTrace trace = emitter.getBeamTrace();
        if (trace.length() <= 0.002D) {
            return;
        }

        Vec3d blockOrigin = Vec3d.of(emitter.getPos());
        Vec3d localStart = trace.start().subtract(blockOrigin);
        Vec3d axis = Vec3d.of(trace.direction().getVector()).normalize();
        Vec3d localEnd = trace.end().subtract(blockOrigin).subtract(axis.multiply(0.008D));
        Vec3d midpoint = trace.start().add(trace.end()).multiply(0.5D);

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d viewVector = camera.getPos().subtract(midpoint);
        Vec3d screenSide = axis.crossProduct(viewVector);
        if (screenSide.lengthSquared() < 1.0E-8D) {
            Vec3d fallback = Math.abs(axis.y) > 0.9D
                    ? new Vec3d(1.0D, 0.0D, 0.0D)
                    : new Vec3d(0.0D, 1.0D, 0.0D);
            screenSide = axis.crossProduct(fallback);
        }
        screenSide = screenSide.normalize();
        Vec3d depthSide = axis.crossProduct(screenSide).normalize();

        float time = emitter.getTicks() + tickDelta;
        float flicker = 0.985F
                + MathHelper.sin(time * 0.41F) * 0.010F
                + MathHelper.sin(time * 1.17F + 1.8F) * 0.005F;
        double widthScale = emitter.getBeamWidthScale();

        if (emitter.isLightEmissionEnabled() && shaderPack) {
            VertexConsumer emissionBuffer = vertexConsumers.getBuffer(LaserRenderLayers.SHADER_EMISSION);
            // Opaque, properly mapped geometry gives deferred lighting and SSR a real position.
            renderEmissionTube(
                    emissionBuffer, matrix, matrices.peek().getNormalMatrix(),
                    localStart, localEnd, axis,
                    LaserBeamProfile.EMISSION_RADIUS * widthScale, colorRgb
            );
        }

        // Vanilla draws the halo in the world pass; shader packs need it after composition.
        if (!shaderPack) {
            VertexConsumer haloBuffer = vertexConsumers.getBuffer(LaserRenderLayers.BEAM_GLOW);
            renderGradientRibbon(
                    haloBuffer, matrix, localStart, localEnd, screenSide,
                    LaserBeamProfile.COLOR_RADII,
                    LaserBeamProfile.COLOR_ALPHA,
                    LaserBeamProfile.COLOR_BRIGHTNESS,
                    colorRgb, flicker, widthScale
            );
        }

        if (shaderPack) {
            // Keep the halo out of the G-buffer to avoid sky occlusion and invalid material data.
            LaserBeamLateRenderer.queue(
                    emitter.getPos().asLong(),
                    trace.start(),
                    trace.end().subtract(axis.multiply(0.008D)),
                    axis,
                    colorRgb,
                    flicker,
                    widthScale
            );
            return;
        }

        VertexConsumer coreBuffer = vertexConsumers.getBuffer(LaserRenderLayers.BEAM_GLOW);

        renderGradientRibbon(
                coreBuffer, matrix, localStart, localEnd, screenSide,
                LaserBeamProfile.CORE_RADII, LaserBeamProfile.CORE_ALPHA, CORE_BRIGHTNESS,
                0xFFFFFF, flicker, widthScale
        );
        renderCorePrism(
                coreBuffer,
                matrix,
                localStart,
                localEnd,
                screenSide,
                depthSide,
                0.0105D * widthScale
        );

        VertexConsumer flareBuffer = vertexConsumers.getBuffer(LaserRenderLayers.FLARE_GLOW);
        Vec3d normalizedView = viewVector.lengthSquared() < 1.0E-8D
                ? axis.multiply(-1.0D)
                : viewVector.normalize();
        Vec3d flareUp = normalizedView.crossProduct(screenSide).normalize();

        renderFlare(flareBuffer, matrix, localStart, screenSide, flareUp,
                0.155D * widthScale, colorRgb, 92);
        renderFlare(flareBuffer, matrix, localStart, screenSide, flareUp,
                0.052D * widthScale, 0xFFFFFF, 205);

        if (trace.hasBlockHit()) {
            renderFlare(flareBuffer, matrix, localEnd, screenSide, flareUp,
                    0.205D * widthScale, colorRgb, 118);
            renderFlare(flareBuffer, matrix, localEnd, screenSide, flareUp,
                    0.068D * widthScale, 0xFFFFFF, 235);
        }
    }

    private static void renderGradientRibbon(
            VertexConsumer buffer,
            Matrix4f matrix,
            Vec3d start,
            Vec3d end,
            Vec3d side,
            double[] radii,
            int[] alphas,
            double[] brightness,
            int rgb,
            float intensity,
            double radiusScale
    ) {
        for (int i = 0; i < radii.length - 1; i++) {
            int innerAlpha = LaserBeamProfile.scaleAlpha(alphas[i], intensity);
            int outerAlpha = LaserBeamProfile.scaleAlpha(alphas[i + 1], intensity);
            int innerRgb = LaserBeamProfile.scaleRgb(rgb, brightness[i]);
            int outerRgb = LaserBeamProfile.scaleRgb(rgb, brightness[i + 1]);
            double innerRadius = radii[i] * radiusScale;
            double outerRadius = radii[i + 1] * radiusScale;
            drawBand(buffer, matrix, start, end, side,
                    innerRadius, outerRadius, innerRgb, outerRgb,
                    innerAlpha, outerAlpha);
            drawBand(buffer, matrix, start, end, side,
                    -innerRadius, -outerRadius, innerRgb, outerRgb,
                    innerAlpha, outerAlpha);
        }
    }

    private static void drawBand(
            VertexConsumer buffer,
            Matrix4f matrix,
            Vec3d start,
            Vec3d end,
            Vec3d side,
            double innerOffset,
            double outerOffset,
            int innerRgb,
            int outerRgb,
            int innerAlpha,
            int outerAlpha
    ) {
        Vec3d startInner = start.add(side.multiply(innerOffset));
        Vec3d endInner = end.add(side.multiply(innerOffset));
        Vec3d endOuter = end.add(side.multiply(outerOffset));
        Vec3d startOuter = start.add(side.multiply(outerOffset));

        glowVertex(buffer, matrix, startInner, innerRgb, innerAlpha);
        glowVertex(buffer, matrix, endInner, innerRgb, innerAlpha);
        glowVertex(buffer, matrix, endOuter, outerRgb, outerAlpha);
        glowVertex(buffer, matrix, startOuter, outerRgb, outerAlpha);
    }

    private static void renderCorePrism(
            VertexConsumer buffer,
            Matrix4f matrix,
            Vec3d start,
            Vec3d end,
            Vec3d side,
            Vec3d depthSide,
            double radius
    ) {
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            double angleA = Math.PI * 2.0D * i / segments;
            double angleB = Math.PI * 2.0D * (i + 1) / segments;
            Vec3d offsetA = side.multiply(Math.cos(angleA) * radius)
                    .add(depthSide.multiply(Math.sin(angleA) * radius));
            Vec3d offsetB = side.multiply(Math.cos(angleB) * radius)
                    .add(depthSide.multiply(Math.sin(angleB) * radius));

            glowVertex(buffer, matrix, start.add(offsetA), 0xFFFFFF, 138);
            glowVertex(buffer, matrix, end.add(offsetA), 0xFFFFFF, 138);
            glowVertex(buffer, matrix, end.add(offsetB), 0xFFFFFF, 138);
            glowVertex(buffer, matrix, start.add(offsetB), 0xFFFFFF, 138);
        }
    }

    private static void renderEmissionTube(
            VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix,
            Vec3d start, Vec3d end, Vec3d axis, double radius, int rgb
    ) {
        // World-locked cross-section: rotating the camera must not move reflective geometry.
        Vec3d side = Math.abs(axis.y) > 0.9D ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d depth = axis.crossProduct(side);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0D * i / segments;
            double b = Math.PI * 2.0D * (i + 1) / segments;
            Vec3d offsetA = side.multiply(Math.cos(a) * radius).add(depth.multiply(Math.sin(a) * radius));
            Vec3d offsetB = side.multiply(Math.cos(b) * radius).add(depth.multiply(Math.sin(b) * radius));
            Vec3d normal = offsetA.add(offsetB).normalize();
            emissionVertex(buffer, matrix, normalMatrix, start.add(offsetA), normal, rgb, 0, 0);
            emissionVertex(buffer, matrix, normalMatrix, start.add(offsetB), normal, rgb, 1, 0);
            emissionVertex(buffer, matrix, normalMatrix, end.add(offsetB), normal, rgb, 1, 1);
            emissionVertex(buffer, matrix, normalMatrix, end.add(offsetA), normal, rgb, 0, 1);
        }
    }

    private static void renderFlare(
            VertexConsumer buffer,
            Matrix4f matrix,
            Vec3d center,
            Vec3d right,
            Vec3d up,
            double radius,
            int rgb,
            int centerAlpha
    ) {
        for (int i = 0; i < FLARE_SEGMENTS; i++) {
            double angleA = Math.PI * 2.0D * i / FLARE_SEGMENTS;
            double angleB = Math.PI * 2.0D * (i + 1) / FLARE_SEGMENTS;
            Vec3d edgeA = center
                    .add(right.multiply(Math.cos(angleA) * radius))
                    .add(up.multiply(Math.sin(angleA) * radius));
            Vec3d edgeB = center
                    .add(right.multiply(Math.cos(angleB) * radius))
                    .add(up.multiply(Math.sin(angleB) * radius));

            glowVertex(buffer, matrix, center, rgb, centerAlpha);
            glowVertex(buffer, matrix, edgeA, 0x000000, 0);
            glowVertex(buffer, matrix, edgeB, 0x000000, 0);
        }
    }

    private static void glowVertex(VertexConsumer buffer, Matrix4f matrix, Vec3d position, int rgb, int alpha) {
        buffer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha)
                .texture(0.0F, 0.0F)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0.0F, 1.0F, 0.0F)
                .next();
    }

    private static void emissionVertex(
            VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix,
            Vec3d position, Vec3d normal, int rgb, float u, float v
    ) {
        buffer.vertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(normalMatrix, (float) normal.x, (float) normal.y, (float) normal.z)
                .next();
    }

    @Override
    public boolean rendersOutsideBoundingBox(LaserEmitterBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
