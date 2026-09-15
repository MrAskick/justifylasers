package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class RefocusingCubeModel {
    public static final Identifier METAL = GameVersion.id("justifylasers", "textures/component/refocusing_cube/base.png");
    private static final int SIDES = 32;

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                              int color, boolean active, boolean emission) {
        RefocusingCubeChassis.MESH.render(matrices, consumers, light, color, active, emission);
    }

    static void ring(VertexConsumer buffer, MatrixStack matrices, Vec3d axis, Vec3d right, Vec3d up,
                             double outer, double inner, double back, double front, int color, int light) {
        for (int segment = 0; segment < SIDES; segment++) {
            double a = Math.PI * 2 * segment / SIDES;
            double b = Math.PI * 2 * (segment + 1) / SIDES;
            Vec3d outerA = circle(axis, right, up, a, outer, front);
            Vec3d outerB = circle(axis, right, up, b, outer, front);
            Vec3d innerA = circle(axis, right, up, a, inner, front);
            Vec3d innerB = circle(axis, right, up, b, inner, front);
            quad(buffer, matrices, innerA, outerA, outerB, innerB, axis, color, 255, light);
            quad(buffer, matrices, circle(axis, right, up, b, inner, back),
                    circle(axis, right, up, b, outer, back), circle(axis, right, up, a, outer, back),
                    circle(axis, right, up, a, inner, back), axis.negate(), color, 255, light);
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

    static void box(VertexConsumer buffer, MatrixStack matrices, Vec3d center, Vec3d halfSize, int color, int light) {
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

    static void quad(VertexConsumer buffer, MatrixStack matrices, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                             Vec3d normal, int color, int alpha, int light) {
        vertex(buffer, matrices, a, normal, color, alpha, light, ModelTextureUV.u(a, normal, 1), ModelTextureUV.v(a, normal, 1));
        vertex(buffer, matrices, b, normal, color, alpha, light, ModelTextureUV.u(b, normal, 1), ModelTextureUV.v(b, normal, 1));
        vertex(buffer, matrices, c, normal, color, alpha, light, ModelTextureUV.u(c, normal, 1), ModelTextureUV.v(c, normal, 1));
        vertex(buffer, matrices, d, normal, color, alpha, light, ModelTextureUV.u(d, normal, 1), ModelTextureUV.v(d, normal, 1));
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d position, Vec3d normal,
                               int color, int alpha, int light, float u, float v) {
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) position.x, (float) position.y, (float) position.z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private RefocusingCubeModel() {
    }
}
