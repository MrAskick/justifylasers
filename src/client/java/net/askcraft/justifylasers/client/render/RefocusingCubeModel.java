package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class RefocusingCubeModel {
    public static final Identifier METAL = new Identifier("minecraft", "textures/block/iron_block.png");
    private static final Identifier DARK = new Identifier("minecraft", "textures/block/polished_deepslate.png");
    private static final Identifier WHITE = new Identifier("minecraft", "textures/misc/white.png");
    private static final int SIDES = 32;

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                              int color, boolean active, boolean emission) {
        RenderLayer metal = RenderLayer.getEntitySolid(METAL);
        RenderLayer dark = RenderLayer.getEntitySolid(DARK);
        for (int x : new int[]{-1, 1}) {
            for (int y : new int[]{-1, 1}) {
                for (int z : new int[]{-1, 1}) {
                    box(consumers.getBuffer(metal), matrices, new Vec3d(x * 0.355D, y * 0.355D, z * 0.355D),
                            new Vec3d(0.095D, 0.095D, 0.095D), 0xD4D9E0, light);
                    box(consumers.getBuffer(dark), matrices, new Vec3d(x * 0.29D, y * 0.29D, z * 0.29D),
                            new Vec3d(0.045D, 0.045D, 0.045D), 0x8090A0, light);
                }
            }
        }
        for (int a : new int[]{-1, 1}) {
            for (int b : new int[]{-1, 1}) {
                box(consumers.getBuffer(dark), matrices, new Vec3d(0, a * 0.385D, b * 0.385D), new Vec3d(0.34D, 0.041D, 0.041D), 0xB0BAC6, light);
                box(consumers.getBuffer(dark), matrices, new Vec3d(a * 0.385D, 0, b * 0.385D), new Vec3d(0.041D, 0.34D, 0.041D), 0xB0BAC6, light);
                box(consumers.getBuffer(dark), matrices, new Vec3d(a * 0.385D, b * 0.385D, 0), new Vec3d(0.041D, 0.041D, 0.34D), 0xB0BAC6, light);
            }
        }
        boolean shaderEmission = active && emission && IrisCompatibility.isShaderPackInUse();
        RenderLayer indicators = shaderEmission ? LaserRenderLayers.SHADER_EMISSION : RenderLayer.getEntitySolid(WHITE);
        int indicatorLight = active ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
        int indicatorColor = active ? color : LaserBeamProfile.scaleRgb(color, 0.38D);
        for (Direction face : Direction.values()) {
            Vec3d axis = Vec3d.of(face.getVector());
            Vec3d right = Math.abs(axis.y) > 0.9D ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            Vec3d up = axis.crossProduct(right);
            ring(consumers.getBuffer(dark), matrices, axis, right, up, 0.345D, 0.306D, 0.395D, 0.435D, 0x485565, light);
            ring(consumers.getBuffer(metal), matrices, axis, right, up, 0.342D, 0.320D, 0.435D, 0.444D, 0xD8DEE5, light);
            ring(consumers.getBuffer(indicators), matrices, axis, right, up, 0.312D, 0.302D, 0.434D, 0.442D, indicatorColor, indicatorLight);
            if (face == Direction.SOUTH) {
                ring(consumers.getBuffer(metal), matrices, axis, right, up, 0.365D, 0.353D, 0.431D, 0.446D, 0xF0C782, light);
                for (int sign : new int[]{-1, 1}) {
                    box(consumers.getBuffer(metal), matrices, new Vec3d(sign * 0.245D, 0.27D, 0.44D),
                            new Vec3d(0.032D, 0.015D, 0.009D), 0xF0C782, light);
                }
            }
        }
        if (!IrisCompatibility.isRenderingShadowPass()) {
            VertexConsumer glass = consumers.getBuffer(LaserRenderLayers.CUBE_LENS);
            for (Direction face : Direction.values()) {
                Vec3d axis = Vec3d.of(face.getVector());
                Vec3d right = Math.abs(axis.y) > 0.9D ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
                Vec3d up = axis.crossProduct(right);
                lens(glass, matrices, axis, right, up, active ? 0xB3D1E9 : 0x7C9BB0, light);
            }
        }
    }

    private static void lens(VertexConsumer buffer, MatrixStack matrices, Vec3d axis, Vec3d right, Vec3d up, int color, int light) {
        for (int segment = 0; segment < SIDES; segment++) {
            double a = Math.PI * 2 * segment / SIDES;
            double b = Math.PI * 2 * (segment + 1) / SIDES;
            Vec3d center = axis.multiply(0.438D);
            Vec3d outerA = circle(axis, right, up, a, 0.299D, 0.420D);
            Vec3d outerB = circle(axis, right, up, b, 0.299D, 0.420D);
            vertex(buffer, matrices, center, axis, color, 10, light, 0.5F, 0.5F);
            vertex(buffer, matrices, outerA, axis, color, 50, light, 0, 0);
            vertex(buffer, matrices, outerB, axis, color, 50, light, 1, 0);
            vertex(buffer, matrices, center, axis, color, 10, light, 0.5F, 0.5F);
        }
    }

    private static void ring(VertexConsumer buffer, MatrixStack matrices, Vec3d axis, Vec3d right, Vec3d up,
                             double outer, double inner, double back, double front, int color, int light) {
        for (int segment = 0; segment < SIDES; segment++) {
            double a = Math.PI * 2 * segment / SIDES;
            double b = Math.PI * 2 * (segment + 1) / SIDES;
            Vec3d outerA = circle(axis, right, up, a, outer, front);
            Vec3d outerB = circle(axis, right, up, b, outer, front);
            Vec3d innerA = circle(axis, right, up, a, inner, front);
            Vec3d innerB = circle(axis, right, up, b, inner, front);
            quad(buffer, matrices, innerA, outerA, outerB, innerB, axis, color, 255, light);
            Vec3d radial = right.multiply(Math.cos((a + b) * 0.5D)).add(up.multiply(Math.sin((a + b) * 0.5D)));
            quad(buffer, matrices, outerA, circle(axis, right, up, a, outer, back),
                    circle(axis, right, up, b, outer, back), outerB, radial, color, 255, light);
            quad(buffer, matrices, innerB, circle(axis, right, up, b, inner, back),
                    circle(axis, right, up, a, inner, back), innerA, radial.negate(), color, 255, light);
        }
    }

    private static Vec3d circle(Vec3d axis, Vec3d right, Vec3d up, double angle, double radius, double depth) {
        return axis.multiply(depth).add(right.multiply(Math.cos(angle) * radius)).add(up.multiply(Math.sin(angle) * radius));
    }

    private static void box(VertexConsumer buffer, MatrixStack matrices, Vec3d center, Vec3d halfSize, int color, int light) {
        for (Direction direction : Direction.values()) {
            Vec3d normal = Vec3d.of(direction.getVector());
            Vec3d right = Math.abs(normal.y) > 0.9D ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            Vec3d up = normal.crossProduct(right);
            Vec3d face = center.add(normal.multiply(halfSize.x, halfSize.y, halfSize.z));
            right = right.multiply(halfSize.x, halfSize.y, halfSize.z);
            up = up.multiply(halfSize.x, halfSize.y, halfSize.z);
            quad(buffer, matrices, face.subtract(right).subtract(up), face.add(right).subtract(up),
                    face.add(right).add(up), face.subtract(right).add(up), normal, color, 255, light);
        }
    }

    private static void quad(VertexConsumer buffer, MatrixStack matrices, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                             Vec3d normal, int color, int alpha, int light) {
        vertex(buffer, matrices, a, normal, color, alpha, light, 0, 0);
        vertex(buffer, matrices, b, normal, color, alpha, light, 1, 0);
        vertex(buffer, matrices, c, normal, color, alpha, light, 1, 1);
        vertex(buffer, matrices, d, normal, color, alpha, light, 0, 1);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d position, Vec3d normal,
                               int color, int alpha, int light, float u, float v) {
        buffer.vertex(matrices.peek().getPositionMatrix(), (float) position.x, (float) position.y, (float) position.z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z).next();
    }

    private RefocusingCubeModel() {
    }
}
