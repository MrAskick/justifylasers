package net.askcraft.justifylasers.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.render.GpuGeometryCache;
import net.askcraft.justifylasers.client.render.PrintedModelRenderer;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.printing.PrintExamples;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class GpuModelSmoke {
    private static final int SIZE = 512;
    static void verify(MinecraftClient client) {
        // Shader-pack material parity is checked inside a real world pass, not a standalone GUI target.
        if (IrisCompatibility.isShaderPackInUse()) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix()); var sorter = RenderSystem.getVertexSorting();
        boolean models = ClientSettings.get().gpuModels, prints = ClientSettings.get().gpuPrintedModels;
        var target = new SimpleFramebuffer(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(1000); RenderSystem.setShaderFogEnd(2000);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(45), 1, .05F, 32), VertexSorter.BY_DISTANCE);
            RenderVersion.pushModelView(new Matrix4f());
            DiffuseLighting.enableGuiDepthLighting();
            try {
                GpuGeometryCache.clear();
                for (int test = 0; test < 9; test++) {
                    try (var cpu = render(target, test, false); var gpu = render(target, test, true)) {
                        compare(client, cpu, gpu, test);
                    }
                }
                if (GpuGeometryCache.meshCount() < 4) throw new AssertionError("Model verification silently fell back to the CPU");
                org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("GPU_MODELS_SMOKE_PASSED transforms=true textures=true lighting=true prints=true");
            } finally { RenderVersion.popModelView(); }
        } finally {
            target.delete(); ClientSettings.get().gpuModels = models; ClientSettings.get().gpuPrintedModels = prints;
            RenderSystem.setProjectionMatrix(projection, sorter);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
    private static NativeImage render(SimpleFramebuffer target, int test, boolean gpu) {
        ClientSettings.get().gpuModels = ClientSettings.get().gpuPrintedModels = gpu;
        GpuGeometryCache.beginFrame();
        RenderSystem.depthMask(true); target.setClearColor(.07F, .1F, .14F, 1);
        target.clear(MinecraftClient.IS_SYSTEM_MAC); target.beginWrite(true);
        var matrices = new MatrixStack(); matrices.translate(0, 0, -3.5);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18 + test * 4));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(test * 27));
        if (test >= 6) matrices.scale(.7F, 1.2F, .9F);
        var consumers = RenderVersion.immediateBuffer();
        if (test % 3 == 0) {
            matrices.translate(-.5, -.5, -.5);
            PrintedModelRenderer.render(PrintExamples.pedestal(), Direction.NORTH, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        } else try {
            var type = Class.forName(PrintedModelRenderer.class.getPackageName() + (test % 3 == 1 ? ".LaserMirrorModel" : ".ChemicalSynthesizerModel"));
            var field = type.getDeclaredField(test % 3 == 1 ? "PLATE" : "BODY"); field.setAccessible(true);
            var mesh = field.get(null);
            var render = mesh.getClass().getDeclaredMethod("render", MatrixStack.class, VertexConsumerProvider.class, int.class, int.class, boolean.class, boolean.class);
            render.setAccessible(true); render.invoke(mesh, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, 0xCE208F, true, true);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        consumers.draw();
        return ScreenshotRecorder.takeScreenshot(target);
    }
    private static void compare(MinecraftClient client, NativeImage cpu, NativeImage gpu, int test) {
        int visible = 0, bad = 0; long error = 0;
        int bg = cpu.getColor(0, 0);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int a = cpu.getColor(x, y), b = gpu.getColor(x, y), max = 0;
            if (a != bg || b != bg) visible++;
            for (int channel = 0; channel < 24; channel += 8) {
                int d = Math.abs((a >> channel & 255) - (b >> channel & 255)); error += d; max = Math.max(max, d);
            }
            if (max > 8) bad++;
        }
        double mean = error / (3D * Math.max(1, visible)), fraction = bad / (double)Math.max(1, visible);
        if (visible < 100 || mean > 1 || fraction > .015) {
            try { cpu.writeTo(client.runDirectory.toPath().resolve("models-cpu-" + test + ".png")); gpu.writeTo(client.runDirectory.toPath().resolve("models-gpu-" + test + ".png")); }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
            throw new AssertionError("Model " + test + " visible=" + visible + " mean=" + mean + " bad=" + fraction);
        }
    }
    private GpuModelSmoke() { }
}
