package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class CubeCoreRenderer {
    private static final Identifier WHITE = GameVersion.id("minecraft", "textures/misc/white.png");
    private static final double CORE_RADIUS = 0.11D;
    private static final double[] HALO_RADII = {0, 0.065D, 0.09D, 0.115D, 0.145D, 0.18D, 0.22D, 0.26D, 0.31D};
    private static final int[] HALO_ALPHA = {255, 255, 248, 220, 165, 94, 40, 10, 0};
    private static final double[] CORE_RADII = {0, 0.048D, 0.068D, 0.088D, 0.12D};
    private static final int[] CORE_ALPHA = {255, 255, 246, 132, 0};
    private static final int SIDES = 48;

    public static void render(int entityId, Vec3d center, Vec3d renderOrigin, int rgb, boolean emission,
                              float time, MatrixStack matrices, VertexConsumerProvider consumers) {
        if (IrisCompatibility.isRenderingShadowPass()) {
            return;
        }
        boolean shaders = IrisCompatibility.isShaderPackInUse();
        Vec3d localCenter = center.subtract(renderOrigin);
        RenderLayer material = shaders && emission
                ? LaserRenderLayers.SHADER_EMISSION : RenderLayer.getEntitySolid(WHITE);
        CoreVertex solid = worldVertex(consumers.getBuffer(material), matrices, localCenter);
        // As with the beam, opaque colored geometry supplies valid LabPBR emission and SSR depth.
        sphere(solid, rgb);

        if (shaders) {
            LaserBeamLateRenderer.queueCore(entityId, center, rgb, time);
        } else {
            Vec3d view = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().subtract(center);
            CoreVertex glow = worldVertex(consumers.getBuffer(LaserRenderLayers.BEAM_GLOW), matrices, localCenter);
            // The eyes layer adds RGB directly (ONE, ONE), so opacity must also scale its color.
            optics((position, normal, color, alpha) -> glow.accept(position, normal,
                    LaserBeamProfile.scaleRgb(color, alpha / 255.0D), alpha), view, rgb, time);
        }
    }

    static void renderLate(VertexConsumer buffer, Vec3d center, Vec3d camera, int rgb, float time) {
        Vec3d relativeCenter = center.subtract(camera);
        optics((position, normal, color, alpha) -> {
            Vec3d point = position.add(relativeCenter);
            RenderVersion.endVertex(buffer.vertex((float) point.x, (float) point.y, (float) point.z)
                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha));
        }, camera.subtract(center), rgb, time);
    }

    private static CoreVertex worldVertex(VertexConsumer buffer, MatrixStack matrices, Vec3d center) {
        return (position, normal, color, alpha) -> {
            Vec3d point = position.add(center);
            RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) point.x, (float) point.y, (float) point.z)
                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha).texture(0.5F, 0.5F)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
        };
    }

    private static void sphere(CoreVertex vertex, int rgb) {
        int rings = 16;
        for (int ring = 0; ring < rings; ring++) {
            double latitudeA = -Math.PI * 0.5D + Math.PI * ring / rings;
            double latitudeB = -Math.PI * 0.5D + Math.PI * (ring + 1) / rings;
            for (int side = 0; side < SIDES; side++) {
                double a = Math.PI * 2 * side / SIDES;
                double b = Math.PI * 2 * (side + 1) / SIDES;
                surfaceVertex(vertex, polar(a, latitudeA).multiply(CORE_RADIUS), rgb, 255);
                surfaceVertex(vertex, polar(a, latitudeB).multiply(CORE_RADIUS), rgb, 255);
                surfaceVertex(vertex, polar(b, latitudeB).multiply(CORE_RADIUS), rgb, 255);
                surfaceVertex(vertex, polar(b, latitudeA).multiply(CORE_RADIUS), rgb, 255);
            }
        }
    }

    private static void optics(CoreVertex vertex, Vec3d view, int rgb, float time) {
        Vec3d forward = view.lengthSquared() < 1.0E-8D ? new Vec3d(0, 0, 1) : view.normalize();
        Vec3d reference = Math.abs(forward.y) > 0.9D ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d right = reference.crossProduct(forward).normalize();
        Vec3d up = forward.crossProduct(right);
        envelope(vertex, right, up, forward, HALO_RADII, HALO_ALPHA, 0.312D, rgb);
        filaments(vertex, rgb, time, 0.12D, 42, 0.003D);
        filaments(vertex, hotColor(rgb), time, 0.026D, 218, 0.004D);
        envelope(vertex, right, up, forward, CORE_RADII, CORE_ALPHA, CORE_RADIUS + 0.004D, 0xFFFFFF);
    }

    private static void envelope(CoreVertex vertex, Vec3d right, Vec3d up, Vec3d forward,
                                 double[] radii, int[] alpha, double depthRadius, int rgb) {
        // Curved, viewer-facing volume samples: the edge fades to zero without a textured quad.
        for (int ring = radii.length - 2; ring >= 0; ring--) {
            for (int side = 0; side < SIDES; side++) {
                double a = Math.PI * 2 * side / SIDES;
                double b = Math.PI * 2 * (side + 1) / SIDES;
                envelopeVertex(vertex, right, up, forward, a, radii[ring], depthRadius, rgb, alpha[ring]);
                envelopeVertex(vertex, right, up, forward, a, radii[ring + 1], depthRadius, rgb, alpha[ring + 1]);
                envelopeVertex(vertex, right, up, forward, b, radii[ring + 1], depthRadius, rgb, alpha[ring + 1]);
                envelopeVertex(vertex, right, up, forward, b, radii[ring], depthRadius, rgb, alpha[ring]);
            }
        }
    }

    private static void envelopeVertex(CoreVertex vertex, Vec3d right, Vec3d up, Vec3d forward,
                                       double angle, double radius, double depthRadius, int rgb, int alpha) {
        double depth = Math.sqrt(Math.max(0, depthRadius * depthRadius - radius * radius));
        Vec3d point = right.multiply(Math.cos(angle) * radius).add(up.multiply(Math.sin(angle) * radius))
                .add(forward.multiply(depth));
        surfaceVertex(vertex, point, rgb, alpha);
    }

    private static void filaments(CoreVertex vertex, int rgb, float time, double halfWidth, int alpha, double offset) {
        for (int strand = 0; strand < 3; strand++) {
            for (int side = 0; side < SIDES; side++) {
                double a = Math.PI * 2 * side / SIDES;
                double b = Math.PI * 2 * (side + 1) / SIDES;
                surfaceVertex(vertex, filamentPoint(a, strand, -halfWidth, time, offset), rgb, alpha);
                surfaceVertex(vertex, filamentPoint(a, strand, halfWidth, time, offset), rgb, alpha);
                surfaceVertex(vertex, filamentPoint(b, strand, halfWidth, time, offset), rgb, alpha);
                surfaceVertex(vertex, filamentPoint(b, strand, -halfWidth, time, offset), rgb, alpha);
            }
        }
    }

    private static Vec3d filamentPoint(double angle, int strand, double width, float time, double offset) {
        double phase = strand * Math.PI * 2 / 3;
        double latitude = 0.48D * Math.sin(angle * 2 + phase) + 0.26D * Math.sin(angle + phase + time * 0.012D);
        double radius = 0.183D + 0.012D * Math.sin(angle * 3 + phase + time * 0.018D) + offset;
        return polar(angle, latitude + width).multiply(radius)
                .rotateX(time * 0.009F + 0.4F).rotateY(time * 0.025F);
    }

    private static Vec3d polar(double longitude, double latitude) {
        double horizontal = Math.cos(latitude);
        return new Vec3d(Math.cos(longitude) * horizontal, Math.sin(latitude), Math.sin(longitude) * horizontal);
    }

    private static int hotColor(int rgb) {
        int red = (rgb >> 16 & 255) + (int) ((255 - (rgb >> 16 & 255)) * 0.32D);
        int green = (rgb >> 8 & 255) + (int) ((255 - (rgb >> 8 & 255)) * 0.32D);
        int blue = (rgb & 255) + (int) ((255 - (rgb & 255)) * 0.32D);
        return red << 16 | green << 8 | blue;
    }

    private static void surfaceVertex(CoreVertex vertex, Vec3d point, int rgb, int alpha) {
        vertex.accept(point, point.normalize(), rgb, alpha);
    }

    @FunctionalInterface
    private interface CoreVertex {
        void accept(Vec3d position, Vec3d normal, int rgb, int alpha);
    }

    private CubeCoreRenderer() {
    }
}
