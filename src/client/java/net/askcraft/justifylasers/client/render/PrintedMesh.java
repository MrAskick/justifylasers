package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.PrintSlice;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Resource-pack-dependent geometry; rebuilt only when a design, print layer or resources change. */
final class PrintedMesh {
    record Face(PrintTextures.Material material, int tint, float nx, float ny, float nz, float[] vertices) { }
    private final List<Face> faces;
    private record Batch(Object key, RenderLayer layer, List<Face> faces, int vertices) { }
    private List<Batch> opaque;

    private PrintedMesh(List<Face> faces) { this.faces = List.copyOf(faces); }
    List<Face> faces() { return faces; }

    void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, int overlay, boolean hologram) {
        if (!hologram && net.askcraft.justifylasers.client.ClientSettings.get().gpuPrintedModels) {
            if (opaque == null) {
                var groups = new java.util.LinkedHashMap<RenderLayer, List<Face>>();
                for (var face : faces) if (!face.material.translucent())
                    groups.computeIfAbsent(face.material.layer(), layer -> new ArrayList<>()).add(face);
                opaque = groups.entrySet().stream().map(entry -> new Batch(new Object(), entry.getKey(), List.copyOf(entry.getValue()),
                        entry.getValue().stream().mapToInt(face -> face.vertices.length / 5).sum())).toList();
            }
            for (var batch : opaque) {
                if (!GpuModelRenderer.draw(batch.key, batch.vertices,
                        buffer -> draw(batch.faces, new MatrixStack(), buffer, light, overlay, false),
                        matrices, batch.layer, light, overlay, 0xFFFFFF, true))
                    draw(batch.faces, matrices, buffers.getBuffer(batch.layer), light, overlay, false);
            }
            // Translucent surfaces stay in Minecraft's sorted buffer alongside glass from other mods.
            for (var face : faces) if (face.material.translucent())
                draw(List.of(face), matrices, buffers.getBuffer(face.material.layer()), light, overlay, false);
            return;
        }
        var entry = matrices.peek();
        var matrix = entry.getPositionMatrix();
        var normal = entry.getNormalMatrix();
        PrintTextures.Material previous = null;
        VertexConsumer consumer = null;
        for (Face face : faces) {
            var material = face.material;
            if (previous == null || !previous.image().equals(material.image())
                    || !hologram && previous.translucent() != material.translucent()) {
                consumer = buffers.getBuffer(hologram ? RenderLayer.getEntityTranslucentEmissive(material.image()) : material.layer());
                previous = material;
            }
            float nx = normal.m00()*face.nx + normal.m10()*face.ny + normal.m20()*face.nz;
            float ny = normal.m01()*face.nx + normal.m11()*face.ny + normal.m21()*face.nz;
            float nz = normal.m02()*face.nx + normal.m12()*face.ny + normal.m22()*face.nz;
            int argb = (hologram ? 150 : 255) << 24 | face.tint & 0xFFFFFF;
            float[] v = face.vertices;
            for (int i = 0; i < v.length; i += 5)
                RenderVersion.entityVertex(consumer, matrix, v[i], v[i+1], v[i+2], argb, v[i+3], v[i+4], overlay, light, nx, ny, nz);
        }
    }

    private static void draw(List<Face> faces, MatrixStack matrices, VertexConsumer buffer, int light, int overlay, boolean hologram) {
        var entry = matrices.peek();
        var normal = entry.getNormalMatrix();
        for (var face : faces) {
            float nx = normal.m00()*face.nx + normal.m10()*face.ny + normal.m20()*face.nz;
            float ny = normal.m01()*face.nx + normal.m11()*face.ny + normal.m21()*face.nz;
            float nz = normal.m02()*face.nx + normal.m12()*face.ny + normal.m22()*face.nz;
            int argb = (hologram ? 150 : 255) << 24 | face.tint & 0xFFFFFF;
            float[] v = face.vertices;
            for (int i = 0; i < v.length; i += 5)
                RenderVersion.entityVertex(buffer, entry.getPositionMatrix(), v[i], v[i+1], v[i+2], argb,
                        v[i+3], v[i+4], overlay, light, nx, ny, nz);
        }
    }

    static final class Builder {
        private final List<Face> faces = new ArrayList<>();

        void add(List<PrintSlice.Vertex> polygon, PrintDesign.Face face, PrintTextures.Material material) {
            int count = polygon.size();
            if (count < 3) return;
            Vec3d a = polygon.get(0).position();
            Vec3d normal = polygon.get(1).position().subtract(a).crossProduct(polygon.get(2).position().subtract(a)).normalize();
            // Unclipped faces are quads already. Only clipped polygons need a triangle fan.
            float[] packed = new float[(count == 4 ? 4 : (count-2)*4) * 5];
            int offset = 0;
            if (count == 4) {
                for (var vertex : polygon) offset = pack(packed, offset, vertex, material);
            } else {
                for (int triangle = 1; triangle < count-1; triangle++) {
                    offset = pack(packed, offset, polygon.get(0), material);
                    offset = pack(packed, offset, polygon.get(triangle), material);
                    offset = pack(packed, offset, polygon.get(triangle+1), material);
                    offset = pack(packed, offset, polygon.get(triangle+1), material);
                }
            }
            faces.add(new Face(material, face.tint(), (float)normal.x, (float)normal.y, (float)normal.z, packed));
        }

        private static int pack(float[] packed, int offset, PrintSlice.Vertex vertex, PrintTextures.Material material) {
            var p = vertex.position();
            packed[offset++] = (float)(p.x/16);
            packed[offset++] = (float)(p.y/16);
            packed[offset++] = (float)(p.z/16);
            packed[offset++] = material.u(vertex.u());
            packed[offset++] = material.v(vertex.v());
            return offset;
        }

        PrintedMesh build() { return new PrintedMesh(faces); }
    }
}
