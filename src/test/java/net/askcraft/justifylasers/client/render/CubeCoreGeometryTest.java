package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.LaserColor;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubeCoreGeometryTest {
    @Test
    void sphereHasFiniteDepthWhiteCenterAndTransparentColoredEdgeFromEveryView() {
        for (Vec3d camera : new Vec3d[]{new Vec3d(0, 0, 2), new Vec3d(0, 3, 0), new Vec3d(0, -3, 0),
                new Vec3d(-2, 1, 4), Vec3d.ZERO}) {
            for (LaserColor color : LaserColor.values()) {
                byte[] geometry = geometry(Vec3d.ZERO, camera, color.rgb(), 123.4F);
                ByteBuffer vertices = ByteBuffer.wrap(geometry).order(ByteOrder.nativeOrder());
                int whites = 0;
                int transparentEdge = 0;
                int partialColor = 0;
                double minZ = Double.POSITIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                while (vertices.remaining() >= 16) {
                    Vec3d point = new Vec3d(vertices.getFloat(), vertices.getFloat(), vertices.getFloat()).add(camera);
                    int red = Byte.toUnsignedInt(vertices.get());
                    int green = Byte.toUnsignedInt(vertices.get());
                    int blue = Byte.toUnsignedInt(vertices.get());
                    int alpha = Byte.toUnsignedInt(vertices.get());
                    assertTrue(Double.isFinite(point.lengthSquared()));
                    assertTrue(point.length() <= 0.313D, "Glow remains inside the cube's lens aperture");
                    if (red == 255 && green == 255 && blue == 255 && alpha == 255) {
                        whites++;
                    }
                    if (alpha == 0 && point.length() > 0.30D) {
                        transparentEdge++;
                    }
                    if ((red << 16 | green << 8 | blue) == color.rgb() && alpha > 0 && alpha < 255) {
                        partialColor++;
                    }
                    minZ = Math.min(minZ, point.z);
                    maxZ = Math.max(maxZ, point.z);
                }
                assertTrue(whites > 0, "White-hot center");
                assertTrue(transparentEdge > 0 && partialColor > 0, "Smooth colored falloff to full transparency");
                assertTrue(maxZ - minZ > 0.15D, "Core and filaments occupy a volume, not a plane");
            }
        }
    }

    @Test
    void plasmaRotatesAndWorldCoordinatesDoNotChangeItsShape() {
        Vec3d camera = new Vec3d(0, 0, 2);
        byte[] first = geometry(Vec3d.ZERO, camera, LaserColor.RED.rgb(), 0);
        byte[] rotated = geometry(Vec3d.ZERO, camera, LaserColor.RED.rgb(), 20);
        assertFalse(Arrays.equals(first, rotated), "Plasma filaments must animate");
        Vec3d offset = new Vec3d(300000, -40, -700000);
        assertTrue(Arrays.equals(first, geometry(offset, camera.add(offset), LaserColor.RED.rgb(), 0)),
                "Geometry stays camera-relative far from the world origin");
    }

    private static byte[] geometry(Vec3d center, Vec3d camera, int rgb, float time) {
        BufferBuilder buffer = new BufferBuilder(65536);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        CubeCoreRenderer.renderLate(buffer, center, camera, rgb, time);
        BufferBuilder.BuiltBuffer built = buffer.end();
        try {
            ByteBuffer vertices = built.getVertexBuffer();
            byte[] bytes = new byte[vertices.remaining()];
            vertices.get(bytes);
            return bytes;
        } finally {
            built.release();
        }
    }
}
