package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.LaserColor;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaserGeometryTest {
    // Recorded before the render-path cleanup: all colors, directions, width extremes and flicker levels.
    private static final String EMITTER_DIGEST = "ee697df0411f15e13872b8eb9b363e16d04ac7728c112b1d6a5d4a7ce202d7c7";
    private static final String LATE_PASS_DIGEST = "3fac8a2941c19011dc7a1b62141674e53b90136653fa78d91c9b24f023418771";

    @Test
    void vanillaAndEmissionGeometryMatchesApprovedBuild() throws Exception {
        assertEquals(EMITTER_DIGEST, rendererDigest(LaserBeamRenderer.class));
    }

    @Test
    void lateShaderProfileMatchesApprovedBuild() throws Exception {
        assertEquals(LATE_PASS_DIGEST, lateDigest(LaserBeamLateRenderer.class));
    }

    @Test
    void shortBeamBodyKeepsTheOriginalVertexStream() throws Exception {
        BufferBuilder expected = new BufferBuilder(8192);
        BufferBuilder actual = new BufferBuilder(8192);
        Matrix4f matrix = new Matrix4f().translation(1.5F, -2, 7);
        Vec3d start = new Vec3d(1, 3, 2);
        Vec3d side = new Vec3d(1, 0, 0);
        Vec3d depth = new Vec3d(0, 0, 1);
        for (double length : new double[]{1, 32, 64}) {
            Vec3d end = start.add(0, length, 0);
            expected.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            invoke(LaserBeamRenderer.class, "renderGradientRibbon", expected, matrix, start, end, side,
                    LaserBeamProfile.COLOR_RADII, LaserBeamProfile.COLOR_ALPHA, LaserBeamProfile.COLOR_BRIGHTNESS,
                    LaserColor.CYAN.rgb(), 0.985F, 1.0D);
            invoke(LaserBeamRenderer.class, "renderGradientRibbon", expected, matrix, start, end, side,
                    LaserBeamProfile.CORE_RADII, LaserBeamProfile.CORE_ALPHA, new double[]{1, 1, 1, 1, 1},
                    0xFFFFFF, 0.985F, 1.0D);
            invoke(LaserBeamRenderer.class, "renderCorePrism", expected, matrix, start, end, side, depth, 0.0105D);
            actual.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            LaserBeamRenderer.renderVanillaBody(actual, matrix, start, end, side, depth, LaserColor.CYAN.rgb(), 0.985F, 1);
            assertArrayEquals(vertices(expected), vertices(actual));
        }
    }

    @Test
    void longVanillaBeamSamplesFogNearTheCameraInsteadOfOnlyAtItsEnds() {
        BufferBuilder buffer = new BufferBuilder(65536);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        LaserBeamRenderer.renderVanillaBody(buffer, new Matrix4f(), new Vec3d(0, -150, 10), new Vec3d(0, 362, 10),
                new Vec3d(1, 0, 0), new Vec3d(0, 0, 1), LaserColor.RED.rgb(), 1, 1);
        ByteBuffer data = ByteBuffer.wrap(vertices(buffer)).order(ByteOrder.nativeOrder());
        int stride = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSizeByte();
        double nearest = Double.POSITIVE_INFINITY;
        for (int quad = 0; quad < data.limit(); quad += stride * 4) {
            double low = Double.POSITIVE_INFINITY;
            double high = Double.NEGATIVE_INFINITY;
            for (int vertex = 0; vertex < 4; vertex++) {
                double y = data.getFloat(quad + vertex * stride + 4);
                low = Math.min(low, y);
                high = Math.max(high, y);
                nearest = Math.min(nearest, Math.abs(y));
            }
            assertTrue(high - low <= 16.001D, "Long-beam fog samples must be at most one chunk apart");
        }
        assertTrue(nearest <= 8, "The visible portion must have nearby fog samples");
    }

    private static byte[] vertices(BufferBuilder buffer) {
        BufferBuilder.BuiltBuffer built = buffer.end();
        try {
            ByteBuffer data = built.getVertexBuffer();
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            return bytes;
        } finally {
            built.release();
        }
    }

    private static String rendererDigest(Class<?> renderer) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        BufferBuilder buffer = new BufferBuilder(8192);
        Matrix4f matrix = new Matrix4f().translation(2.5F, 0.3F, -4.0F).rotateXYZ(0.2F, -0.5F, 0.8F);
        for (Direction direction : Direction.values()) {
            Vec3d axis = Vec3d.of(direction.getVector());
            Vec3d side = Math.abs(axis.y) > 0.9D ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            Vec3d depth = axis.crossProduct(side);
            Vec3d start = new Vec3d(12.5D, 40.2D, -65.3D);
            Vec3d end = start.add(axis.multiply(64.0D));
            for (double width : new double[]{0.1D, 1.0D, 10.0D}) {
                for (LaserColor color : LaserColor.values()) {
                    for (float intensity : new float[]{0.97F, 1.0F}) {
                        buffer.begin(VertexFormat.DrawMode.QUADS,
                                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                        invoke(renderer, "renderGradientRibbon", buffer, matrix, start, end, side,
                                LaserBeamProfile.COLOR_RADII, LaserBeamProfile.COLOR_ALPHA,
                                LaserBeamProfile.COLOR_BRIGHTNESS, color.rgb(), intensity, width);
                        invoke(renderer, "renderGradientRibbon", buffer, matrix, start, end, side,
                                LaserBeamProfile.CORE_RADII, LaserBeamProfile.CORE_ALPHA,
                                new double[]{1, 1, 1, 1, 1}, 0xFFFFFF, intensity, width);
                        invoke(renderer, "renderCorePrism", buffer, matrix, start, end, side, depth,
                                0.0105D * width);
                        invoke(renderer, "renderEmissionTube", buffer, matrix, new Matrix3f(matrix),
                                start, end, axis, LaserBeamProfile.EMISSION_RADIUS * width, color.rgb());
                        append(digest, buffer);
                        buffer.begin(VertexFormat.DrawMode.TRIANGLES,
                                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                        invoke(renderer, "renderFlare", buffer, matrix, start, side, depth,
                                0.155D * width, color.rgb(), 92);
                        invoke(renderer, "renderFlare", buffer, matrix, end, side, depth,
                                0.068D * width, 0xFFFFFF, 235);
                        append(digest, buffer);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String lateDigest(Class<?> renderer) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        BufferBuilder buffer = new BufferBuilder(8192);
        Class<?> beamClass = Arrays.stream(renderer.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("QueuedBeam")).findFirst().orElseThrow();
        Constructor<?> constructor = beamClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Vec3d camera = new Vec3d(18.7D, 44.0D, -62.4D);
        for (Direction direction : Direction.values()) {
            Vec3d axis = Vec3d.of(direction.getVector());
            Vec3d start = new Vec3d(12.5D, 40.2D, -65.3D);
            Vec3d end = start.add(axis.multiply(64.0D));
            for (double width : new double[]{0.1D, 1.0D, 10.0D}) {
                for (LaserColor color : LaserColor.values()) {
                    for (float intensity : new float[]{0.97F, 1.0F}) {
                        Object beam = constructor.newInstance(start, end, axis, color.rgb(), intensity, width);
                        Vec3d side = (Vec3d) invoke(renderer, "screenSide", beam, camera);
                        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                        invoke(renderer, "renderGradientRibbon", buffer, beam, side, camera, false);
                        invoke(renderer, "renderGradientRibbon", buffer, beam, side, camera, true);
                        append(digest, buffer);
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Object invoke(Class<?> owner, String name, Object... arguments) throws Exception {
        Method method = Arrays.stream(owner.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    private static void append(MessageDigest digest, BufferBuilder buffer) {
        BufferBuilder.BuiltBuffer built = buffer.end();
        ByteBuffer vertices = built.getVertexBuffer();
        VertexFormat format = built.getParameters().format();
        // Entity vertices contain an unused padding byte after the normal.
        if (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) {
            int stride = format.getVertexSizeByte();
            for (int offset = stride - 1; offset < vertices.limit(); offset += stride) {
                vertices.put(offset, (byte) 0);
            }
        }
        digest.update(vertices);
        built.release();
    }
}
