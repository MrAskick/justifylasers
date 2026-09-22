package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.laser.ScorchGeometry;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Trail geometry changes on contact, not while it cools. Batch uniforms carry the changing opacity and heat. */
final class GpuScorchRenderer {
    private static final int BATCH_SIZE = 64;
    private static final List<Batch> BATCHES = new ArrayList<>();
    private static final float[] OPACITY = new float[BATCH_SIZE], HEAT = new float[BATCH_SIZE];
    private static ShaderProgram shader;

    static void initialize() {
        ClientPlatform.registerShader("gpu_scorch", VertexFormats.POSITION_TEXTURE_COLOR, program -> {
            BATCHES.clear();
            shader = program;
        });
    }

    static boolean enabled() { return ClientSettings.get().gpuEffects && shader != null; }

    static void render(List<LaserScorchRenderer.VisibleMark> visible, LateEffectBatch fallback, Vec3d camera) {
        int count = (visible.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        while (BATCHES.size() > count) BATCHES.remove(BATCHES.size() - 1);
        for (int index = 0; index < count; index++) {
            int start = index * BATCH_SIZE, end = Math.min(start + BATCH_SIZE, visible.size());
            if (index == BATCHES.size()) BATCHES.add(new Batch(visible, start, end));
            else if (!BATCHES.get(index).matches(visible, start, end)) BATCHES.set(index, new Batch(visible, start, end));
        }
        // Keep every soot segment below every hot edge, including when a cache miss needs the CPU path.
        for (boolean hot : new boolean[]{false, true}) for (int index = 0; index < count; index++) {
            int start = index * BATCH_SIZE, end = Math.min(start + BATCH_SIZE, visible.size());
            var batch = BATCHES.get(index);
            int vertices = batch.vertices(hot);
            if (vertices == 0) continue;
            var mesh = GpuGeometryCache.get(hot ? batch.hotKey : batch.sootKey, vertices * 30L,
                    () -> RenderVersion.staticMesh(VertexFormats.POSITION_TEXTURE_COLOR, out -> batch.bake(out, hot)));
            if (mesh == null) {
                for (int i = start; i < end; i++) LaserScorchRenderer.renderMark(fallback.buffer(), visible.get(i), hot, camera);
                continue;
            }
            fallback.flush();
            for (int i = start; i < end; i++) {
                OPACITY[i - start] = visible.get(i).opacity();
                HEAT[i - start] = visible.get(i).heat();
            }
            shader.getUniform("MarkOpacity").set(OPACITY);
            shader.getUniform("MarkHeat").set(HEAT);
            shader.getUniform("Hot").set(hot ? 1 : 0);
            GpuLightEffects.vector(shader, "Origin", batch.origin.subtract(camera));
            GpuLightEffects.draw(mesh, shader);
        }
    }

    private static final class Batch {
        final List<ScorchGeometry.Patch> patches;
        final Object sootKey = new Object(), hotKey = new Object();
        final Vec3d origin;

        Batch(List<LaserScorchRenderer.VisibleMark> visible, int start, int end) {
            patches = visible.subList(start, end).stream().map(LaserScorchRenderer.VisibleMark::patch).toList();
            origin = Vec3d.of(patches.get(0).position());
        }

        boolean matches(List<LaserScorchRenderer.VisibleMark> visible, int start, int end) {
            if (patches.size() != end - start) return false;
            for (int i = start; i < end; i++) if (patches.get(i - start) != visible.get(i).patch()) return false;
            return true;
        }

        int vertices(boolean hot) { return patches.stream().mapToInt(p -> hot ? p.heat().size() : p.soot().size()).sum(); }

        void bake(VertexConsumer out, boolean hot) {
            for (int i = 0; i < patches.size(); i++) {
                var patch = patches.get(i);
                Vec3d offset = Vec3d.of(patch.position()).subtract(origin);
                for (var vertex : hot ? patch.heat() : patch.soot()) {
                    var p = vertex.position().add(offset);
                    int rgb = vertex.rgb();
                    RenderVersion.endVertex(out.vertex((float) p.x, (float) p.y, (float) p.z).texture(i, 0)
                            .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, vertex.alpha()));
                }
            }
        }
    }

    private GpuScorchRenderer() { }
}
