package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Shared, bounded GPU templates; color, orientation, time and viewing side are per-draw uniforms. */
public final class LightBridgeGpuRenderer {
    private static ShaderProgram program;

    private record Key(int width, double length) { }

    public static void initialize() {
        ClientPlatform.registerShader("light_bridge", VertexFormats.POSITION_TEXTURE_COLOR, shader -> {
            GpuGeometryCache.clear();
            program = shader;
        });
    }

    static boolean ready() { return program != null && net.askcraft.justifylasers.client.ClientSettings.get().gpuEffects; }

    static boolean draw(LightBridgeSpan span, double time, Vec3d camera, Matrix4f view, Matrix4f projection) {
        VertexBuffer mesh = mesh(new Key(span.width(), span.length()));
        if (mesh == null) return false;
        var transform = LightBridgeTransform.of(span, camera);
        var modelView = new Matrix4f(view).mul(transform.matrix());
        boolean above = camera.subtract(span.point(0, 0, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS / 2))
                .dotProduct(span.normal()) >= 0;
        program.getUniform("SurfaceY").set((float) (above ? LightBridgeSpan.HEIGHT + .001
                : LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS - .001));
        program.getUniform("Size").set((float) span.width(), (float) span.length());
        // Small phases retain sub-tick precision even after hours in the same world.
        program.getUniform("PhaseA").set(phase(time * .006), phase(-time * .027), phase(time * .016));
        program.getUniform("PhaseB").set(phase(-time * .019), phase(time * .009));
        color("BeamColor", span.rgb());
        color("FilamentColor", pale(span.rgb(), .22));
        color("EdgeColor", pale(span.rgb(), .5));
        mesh.bind();
        try {
            mesh.draw(modelView, projection, program);
        } finally {
            VertexBuffer.unbind();
        }
        return true;
    }

    private static VertexBuffer mesh(Key key) {
        // A conservative bound includes shared quad indices and fractional final segments.
        long vertices = 16L * key.width * ((long)Math.ceil(key.length / .5) + 1)
                + 8L * (key.width * 3 + 3) * ((long)Math.ceil(key.length / .2) + 1)
                + 8L * ((long)Math.ceil(key.width / .16) + 1) * ((long)Math.ceil(key.length / 1.13) + 1)
                + 32L * ((long)Math.ceil(key.length / 2) + 1) + 12;
        return GpuGeometryCache.get(key, vertices * 30L, () -> upload(key));
    }

    private static VertexBuffer upload(Key key) {
        var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_TEXTURE_COLOR);
        LightBridgeMesh.emit(key.width, key.length, (x, y, z, kind, palette, alpha) -> {
            RenderVersion.endVertex(buffer.vertex((float) x, (float) y, (float) z).texture(kind, 0).color(palette, 0, 0, alpha));
        });
        var gpu = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            gpu.bind();
            gpu.upload(buffer.end());
        } catch (RuntimeException failure) {
            gpu.close();
            throw failure;
        } finally {
            VertexBuffer.unbind();
        }
        return gpu;
    }

    private static void color(String uniform, int rgb) {
        program.getUniform(uniform).set((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
    }

    private static float phase(double value) { return (float) (value % (Math.PI * 2)); }
    private static int pale(int rgb, double amount) {
        return channel(rgb >> 16 & 255, amount) << 16 | channel(rgb >> 8 & 255, amount) << 8 | channel(rgb & 255, amount);
    }
    private static int channel(int value, double amount) { return value + (int) ((255 - value) * amount); }
    private LightBridgeGpuRenderer() { }
}
