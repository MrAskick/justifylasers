package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** A closed luminous tube with a rounded tip; the emitter beam's profile is left untouched. */
final class SaberBladeRenderer {
    static final double WIDTH = 0.5;

    static void render(LaserBeamTrace ray, Vec3d origin, Object source, int segment, int rgb,
                       MatrixStack matrices, VertexConsumerProvider consumers) {
        if (ray.length() < 0.005) return;
        boolean shaders = IrisCompatibility.isShaderPackInUse();
        var buffer = consumers.getBuffer(LaserRenderLayers.SHADER_EMISSION);
        Vec3d axis = ray.axis();
        Vec3d right = axis.crossProduct(Math.abs(axis.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0)).normalize();
        Vec3d up = axis.crossProduct(right);
        double width = WIDTH * Math.min(1, ray.length() / 0.3);
        double radius = shaders ? LaserBeamProfile.EMISSION_RADIUS * width : 0.01 * width;
        double cap = ray.hasBlockHit() ? 0 : Math.min(radius, ray.length() / 2);
        int color = shaders ? rgb : 0xFFFFFF;
        for (int band = 0; band < 5; band++) for (int side = 0; side < 24; side++) {
            double a = side * Math.PI / 12, b = (side + 1) * Math.PI / 12;
            for (int vertex = 0; vertex < 4; vertex++) {
                int ring = band + (vertex >= 2 ? 1 : 0);
                double angle = vertex == 0 || vertex == 3 ? a : b;
                double arc = Math.max(0, ring - 1) * Math.PI / 8;
                double r = ring == 0 ? radius : radius * Math.cos(arc);
                double distance = ring == 0 ? 0 : ray.length() - cap + cap * Math.sin(arc);
                Vec3d radial = right.multiply(Math.cos(angle)).add(up.multiply(Math.sin(angle)));
                Vec3d normal = ring < 2 ? radial : radial.multiply(Math.cos(arc)).add(axis.multiply(Math.sin(arc))).normalize();
                Vec3d point = ray.start().subtract(origin).add(axis.multiply(distance)).add(radial.multiply(r));
                RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) point.x, (float) point.y, (float) point.z)
                                .color(color >> 16 & 255, color >> 8 & 255, color & 255, 255).texture(0.5F, 0.5F)
                                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                        matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
            }
        }
        if (source != null) LaserBeamLateRenderer.queueSaber(source, segment, ray, rgb);
    }

    static void renderLate(VertexConsumer buffer, LaserBeamTrace ray, int rgb, Vec3d camera) {
        Vec3d axis = ray.axis();
        Vec3d side = axis.crossProduct(camera.subtract(ray.start().add(ray.end()).multiply(0.5)));
        if (side.lengthSquared() < 1E-8) side = axis.crossProduct(Math.abs(axis.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0));
        side = side.normalize();
        // Optical falloff is round in the image plane, even when looking almost along the blade.
        Vec3d view = camera.subtract(ray.start().add(ray.end()).multiply(.5)).normalize();
        Vec3d capAxis = view.crossProduct(side).normalize();
        double width = WIDTH * Math.min(1, ray.length() / 0.3);
        Vec3d surface = axis.crossProduct(side).multiply(-(LaserBeamProfile.EMISSION_RADIUS + 0.002) * width);
        Vec3d start = ray.start().add(surface), end = ray.end().add(surface);
        for (boolean core : new boolean[]{false, true}) {
            double[] radii = core ? LaserBeamProfile.CORE_RADII : LaserBeamProfile.COLOR_RADII;
            int[] alpha = core ? LaserBeamProfile.CORE_ALPHA : LaserBeamProfile.COLOR_ALPHA;
            for (int i = 0; i < radii.length - 1; i++) for (int sign : new int[]{-1, 1}) {
                double inner = radii[i] * width, outer = radii[i + 1] * width;
                int a = core ? alpha[i] : (int) (alpha[i] * LaserBeamProfile.COLOR_BRIGHTNESS[i]);
                int b = core ? alpha[i + 1] : (int) (alpha[i + 1] * LaserBeamProfile.COLOR_BRIGHTNESS[i + 1]);
                int color = core ? 0xFFFFFF : rgb;
                Vec3d innerEnd = end.add(side.multiply(inner * sign));
                Vec3d outerEnd = end.add(side.multiply(outer * sign));
                quad(buffer, start.add(side.multiply(inner * sign)), innerEnd, outerEnd, start.add(side.multiply(outer * sign)), camera, color, a, b);
                for (int tip : new int[]{-1, 1}) {
                    if (tip == 1 && ray.hasBlockHit()) continue;
                    Vec3d center = tip == 1 ? end : start;
                    Vec3d outward = capAxis.multiply(tip);
                    for (int arc = 0; arc < 8; arc++) {
                        double angle = arc * Math.PI / 16, next = (arc + 1) * Math.PI / 16;
                        quad(buffer, capPoint(center, outward, side, inner, sign, angle), capPoint(center, outward, side, inner, sign, next),
                                capPoint(center, outward, side, outer, sign, next), capPoint(center, outward, side, outer, sign, angle), camera, color, a, b);
                    }
                }
            }
        }
    }

    static Vec3d capPoint(Vec3d end, Vec3d axis, Vec3d side, double radius, int sign, double angle) {
        return end.add(side.multiply(radius * Math.cos(angle) * sign)).add(axis.multiply(radius * Math.sin(angle)));
    }

    private static void quad(VertexConsumer buffer, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d camera, int rgb, int inner, int outer) {
        vertex(buffer, a.subtract(camera), rgb, inner); vertex(buffer, b.subtract(camera), rgb, inner);
        vertex(buffer, c.subtract(camera), rgb, outer); vertex(buffer, d.subtract(camera), rgb, outer);
    }

    private static void vertex(VertexConsumer buffer, Vec3d point, int rgb, int alpha) {
        RenderVersion.endVertex(buffer.vertex((float) point.x, (float) point.y, (float) point.z).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha));
    }

    private SaberBladeRenderer() { }
}
