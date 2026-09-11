package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserScorchMarks;
import net.askcraft.justifylasers.laser.ScorchGeometry;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class LaserScorchRenderer {
    private static final LaserScorchMarks MARKS = new LaserScorchMarks();
    private static final List<VisibleMark> VISIBLE = new ArrayList<>();

    public static void tick(MinecraftClient client) {
        MARKS.tick(client.world);
    }

    public static void prepare(LaserRenderFrame context) {
        if (IrisCompatibility.isRenderingShadowPass()) {
            return;
        }
        VISIBLE.clear();
        if (!MARKS.belongsTo(context.world()) || context.consumers() == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        double now = context.world().getTime() + context.tickDelta();
        boolean shaders = IrisCompatibility.isShaderPackInUse();
        MatrixStack matrices = context.matrixStack();
        for (LaserScorchMarks.Mark mark : MARKS.marks()) {
            ScorchGeometry.Patch patch = mark.patch();
            Vec3d origin = Vec3d.of(patch.position());
            Vec3d normal = Vec3d.of(patch.face().getVector());
            Vec3d surface = origin.add(patch.soot().get(0).position());
            double distance = camera.distanceTo(surface);
            if (distance >= 96 || camera.subtract(surface).dotProduct(normal) <= 0
                    || context.world().getBlockState(patch.position()) != patch.state()
                    || context.frustum() != null && !context.frustum().isVisible(new Box(patch.position()).expand(0.01D))) {
                continue;
            }
            float opacity = LaserScorchMarks.opacity(mark.age(now)) * (float) MathHelper.clamp((96 - distance) / 24, 0, 1);
            float heat = LaserScorchMarks.heat(mark.age(now));
            VISIBLE.add(new VisibleMark(patch, opacity, heat));
            if (shaders && mark.emitsLight() && heat > 0.035F) {
                VertexConsumer buffer = context.consumers().getBuffer(LaserRenderLayers.SHADER_EMISSION);
                int rgb = temperature(0xFFD154, heat);
                Vec3d relative = origin.subtract(camera);
                for (ScorchGeometry.Vertex vertex : patch.emission()) {
                    Vec3d point = vertex.position();
                    RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) (point.x + relative.x),
                                    (float) (point.y + relative.y), (float) (point.z + relative.z))
                            .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).texture(0.5F, 0.5F)
                            .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
                }
            }
        }
    }

    public static boolean hasVisibleMarks() {
        return !VISIBLE.isEmpty();
    }

    public static void renderLate(VertexConsumer buffer, Vec3d camera) {
        // Draw all soot before any hot edges, so overlapping trail pieces cannot erase the glow.
        for (VisibleMark mark : VISIBLE) {
            Vec3d origin = Vec3d.of(mark.patch.position()).subtract(camera);
            for (ScorchGeometry.Vertex vertex : mark.patch.soot()) {
                vertex(buffer, origin, vertex, vertex.rgb(), mark.opacity);
            }
        }
        for (VisibleMark mark : VISIBLE) {
            if (mark.heat <= 0) {
                continue;
            }
            Vec3d origin = Vec3d.of(mark.patch.position()).subtract(camera);
            for (ScorchGeometry.Vertex vertex : mark.patch.heat()) {
                vertex(buffer, origin, vertex, temperature(vertex.rgb(), mark.heat), mark.opacity * Math.min(1, mark.heat * 2));
            }
        }
    }

    public static void endFrame() {
        VISIBLE.clear();
    }

    private static int temperature(int rgb, float heat) {
        int red = (int) ((rgb >> 16 & 255) * (0.3F + 0.7F * heat));
        int green = (int) ((rgb >> 8 & 255) * heat * heat);
        int blue = (int) ((rgb & 255) * heat * heat * heat);
        return red << 16 | green << 8 | blue;
    }

    private static void vertex(VertexConsumer buffer, Vec3d origin, ScorchGeometry.Vertex vertex, int rgb, float opacity) {
        Vec3d point = vertex.position();
        RenderVersion.endVertex(buffer.vertex((float) (point.x + origin.x), (float) (point.y + origin.y), (float) (point.z + origin.z)).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255,
                MathHelper.clamp(Math.round(vertex.alpha() * opacity), 0, 255)));
    }

    private record VisibleMark(ScorchGeometry.Patch patch, float opacity, float heat) {
    }

    private LaserScorchRenderer() {
    }
}
