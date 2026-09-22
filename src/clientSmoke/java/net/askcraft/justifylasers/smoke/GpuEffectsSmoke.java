package net.askcraft.justifylasers.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.render.GpuGeometryCache;
import net.askcraft.justifylasers.client.render.LaserBeamLateRenderer;
import net.askcraft.justifylasers.client.render.LaserRenderLayers;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.ScorchGeometry;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class GpuEffectsSmoke {
    private static final int SIZE = 512;

    public static void verify(MinecraftClient client) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorter = RenderSystem.getVertexSorting();
        var oldShader = RenderSystem.getShader();
        boolean enabled = ClientSettings.get().gpuEffects;
        var target = new SimpleFramebuffer(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
        try {
            ClientSettings.get().gpuEffects = true;
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(55), 1, .05F, 2048), VertexSorter.BY_DISTANCE);
            RenderVersion.pushModelView(new Matrix4f());
            try {
                for (int i = 0; i < 12; i++) {
                    Vec3d origin = i < 6 ? Vec3d.ZERO : new Vec3d(29_999_990, 90, -29_999_990);
                    Vec3d start = origin.add(-.9, -.6, -4), end = origin.add(.8, .7, -5);
                    Vec3d axis = end.subtract(start).normalize();
                    int rgb = new int[]{0xFF1429, 0x09CCFF, 0xA821ED, 0xFFFFFF}[i % 4];
                    var ray = new LaserBeamTrace(start, end, Direction.NORTH, i % 2 == 0 ? null : BlockPos.ORIGIN);
                    int test = i;
                    compare(client, target, "saber-" + i, out -> invoke("SaberBladeRenderer", "renderLate", out, ray, rgb, origin),
                            () -> (boolean) invoke("GpuLightEffects", "saber", ray, rgb, origin));
                    Vec3d center = origin.add(0, 0, -1.4);
                    float time = i * 393.7F;
                    compare(client, target, "core-" + i, out -> invoke("CubeCoreRenderer", "renderLate", out, center, origin, rgb, time),
                            () -> (boolean) invoke("GpuLightEffects", "core", center, origin, rgb, time));
                    Vec3d side = axis.crossProduct(origin.subtract(start.add(end).multiply(.5))).normalize();
                    Object clip = i % 2 == 0 ? null : construct("BeamEndpointClip", end, axis.negate().add(.25, 0, 0).normalize(), axis.negate());
                    compare(client, target, "beam-" + i, out -> referenceBeam(out, start, end, axis, side, origin, rgb, test, clip),
                            () -> (boolean) invoke("GpuLightEffects", "beam", start, end, axis, side, origin, rgb, .7F, 1 + test * .17, null, clip));
                }
                scorch(client, target);
                sunlight(client, target);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error after GPU effects");
            } finally { RenderVersion.popModelView(); }
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("GPU_EFFECTS_SMOKE_PASSED beam=true saber=true core=true scorch=true sunlight=true occlusion=true");
        } finally {
            ClientSettings.get().gpuEffects = enabled;
            target.delete();
            GL11.glClearDepth(1);
            RenderSystem.depthMask(true);
            RenderSystem.setProjectionMatrix(projection, sorter);
            RenderSystem.setShader(() -> oldShader);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static void referenceBeam(VertexConsumer out, Vec3d start, Vec3d end, Vec3d axis, Vec3d side,
                                      Vec3d camera, int rgb, int test, Object clip) {
        try {
            var type = Class.forName(LaserBeamLateRenderer.class.getName() + "$QueuedBeam");
            var ctor = type.getDeclaredConstructors()[0]; ctor.setAccessible(true);
            Object beam = ctor.newInstance(start, end, axis, rgb, .7F, 1 + test * .17, null, clip);
            var method = LaserBeamLateRenderer.class.getDeclaredMethod("renderGradientRibbon", BufferBuilder.class, type, Vec3d.class, Vec3d.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, out, beam, side, camera, false);
            method.invoke(null, out, beam, side, camera, true);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void scorch(MinecraftClient client, SimpleFramebuffer target) {
        var marks = new ArrayList<Object>();
        for (int i = 0; i < 90; i++) {
            double x = -.8 + (i % 10) * .16, y = -.7 + (i / 10) * .16;
            var vertices = List.of(new ScorchGeometry.Vertex(new Vec3d(x, y, -2), 0xFFF4BD, 255),
                    new ScorchGeometry.Vertex(new Vec3d(x + .15, y, -2), 0xFFB72C, 180),
                    new ScorchGeometry.Vertex(new Vec3d(x + .15, y + .15, -2), 0xCB2603, 25),
                    new ScorchGeometry.Vertex(new Vec3d(x, y + .15, -2), 0x34241A, 0));
            var patch = new ScorchGeometry.Patch(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), Direction.SOUTH, vertices, vertices, List.of());
            marks.add(construct("LaserScorchRenderer$VisibleMark", patch, (i % 7) / 6F, (i % 11) / 10F));
        }
        compare(client, target, "scorch", out -> {
            for (boolean hot : new boolean[]{false, true}) for (var mark : marks) invoke("LaserScorchRenderer", "renderMark", out, mark, hot, Vec3d.ZERO);
        }, () -> {
            try (var batch = (AutoCloseable) construct("LateEffectBatch")) { invoke("GpuScorchRenderer", "render", marks, batch, Vec3d.ZERO); }
            catch (Exception failure) { throw new AssertionError(failure); }
            return true;
        });
    }

    private static void sunlight(MinecraftClient client, SimpleFramebuffer target) {
        try {
            var sources = type("SolarLightShaftRenderer").getDeclaredField("SOURCES"); sources.setAccessible(true);
            @SuppressWarnings("unchecked") var queue = (java.util.Map<Object, Object>) sources.get(null);
            queue.clear();
            queue.put(BlockPos.ORIGIN, construct("SolarLightShaftRenderer$Shaft", new Vec3d(0, -.6, -2), new Vec3d(0, .6, -.8), 1L, 1620.75F, true));
            try {
                compare(client, target, "sunlight", out -> {
                    // The CPU path writes into the same builder so the existing comparison owns its flush.
                    ClientSettings.get().gpuEffects = false;
                    Object batch = construct("LateEffectBatch");
                    try {
                        var buffer = batch.getClass().getDeclaredField("buffer"); buffer.setAccessible(true); buffer.set(batch, out);
                    } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                    invoke("SolarLightShaftRenderer", "render", batch, Vec3d.ZERO);
                }, () -> {
                    ClientSettings.get().gpuEffects = true;
                    try (var batch = (AutoCloseable) construct("LateEffectBatch")) { invoke("SolarLightShaftRenderer", "render", batch, Vec3d.ZERO); }
                    catch (Exception failure) { throw new AssertionError(failure); }
                    return true;
                });
            } finally { queue.clear(); ClientSettings.get().gpuEffects = true; }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void compare(MinecraftClient client, SimpleFramebuffer target, String name, Consumer<VertexConsumer> cpu, BooleanSupplier gpu) {
        for (boolean occluded : new boolean[]{false, true}) {
            try (var a = render(target, cpu, null, occluded); var b = render(target, null, gpu, occluded)) {
                int visible = 0, bad = 0; long error = 0;
                int bg = a.getColor(0, 0);
                for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                    int p = a.getColor(x, y), q = b.getColor(x, y), max = 0;
                    if (p != bg || q != bg) visible++;
                    for (int channel = 0; channel < 24; channel += 8) {
                        int d = Math.abs((p >> channel & 255) - (q >> channel & 255));
                        error += d; max = Math.max(max, d);
                    }
                    if (max > 8) bad++;
                }
                double mean = error / (3D * Math.max(1, visible)), fraction = bad / (double)Math.max(1, visible);
                if (mean > .6 || fraction > .007 || (occluded ? visible != 0 : visible < 20)) {
                    try { a.writeTo(client.runDirectory.toPath().resolve("effects-cpu-" + name + ".png")); b.writeTo(client.runDirectory.toPath().resolve("effects-gpu-" + name + ".png")); }
                    catch (java.io.IOException failure) { throw new AssertionError(failure); }
                    throw new AssertionError(name + " occluded=" + occluded + " visible=" + visible + " mean=" + mean + " bad=" + fraction);
                }
            }
        }
    }

    private static NativeImage render(SimpleFramebuffer target, Consumer<VertexConsumer> cpu, BooleanSupplier gpu, boolean occluded) {
        GpuGeometryCache.beginFrame();
        target.setClearColor(.07F, .1F, .14F, 1);
        RenderSystem.depthMask(true);
        target.clear(MinecraftClient.IS_SYSTEM_MAC); target.beginWrite(true);
        GL11.glClearDepth(occluded ? 0 : 1); GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        LaserRenderLayers.SHADER_BEAM_HALO.startDrawing();
        try {
            if (gpu != null) { if (!gpu.getAsBoolean()) throw new AssertionError("GPU effect did not render"); }
            else {
                var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_COLOR);
                cpu.accept(buffer); BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally { LaserRenderLayers.SHADER_BEAM_HALO.endDrawing(); }
        return ScreenshotRecorder.takeScreenshot(target);
    }

    private GpuEffectsSmoke() { }

    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(LaserBeamLateRenderer.class.getPackageName() + "." + name);
    }
    private static Object construct(String name, Object... args) {
        try {
            for (var ctor : type(name).getDeclaredConstructors()) if (ctor.getParameterCount() == args.length) {
                ctor.setAccessible(true); return ctor.newInstance(args);
            }
            throw new NoSuchMethodException(name);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object invoke(String name, String method, Object... args) {
        try {
            for (var candidate : type(name).getDeclaredMethods()) if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
                candidate.setAccessible(true); return candidate.invoke(null, args);
            }
            throw new NoSuchMethodException(name + "." + method);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
