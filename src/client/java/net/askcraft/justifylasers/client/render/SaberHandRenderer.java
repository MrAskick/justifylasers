package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/** Hand-space bloom uses the exact native hand projection/depth range, after shader tone mapping. */
public final class SaberHandRenderer {
    private record Blade(LaserBeamTrace ray, int rgb, Matrix4f projection, Matrix4f modelView, double near, double far) { }
    private static final List<Blade> BLADES = new ArrayList<>(2);

    public static void beginFrame() { BLADES.clear(); }

    static void queue(LaserBeamTrace ray, int rgb, MatrixStack matrices) {
        if (IrisCompatibility.isRenderingShadowPass()) return;
        Matrix4f transform = matrices.peek().getPositionMatrix();
        Vector3f a = transform.transformPosition(new Vector3f((float) ray.start().x, (float) ray.start().y, (float) ray.start().z));
        Vector3f b = transform.transformPosition(new Vector3f((float) ray.end().x, (float) ray.end().y, (float) ray.end().z));
        double[] range = new double[2];
        GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, range);
        BLADES.add(new Blade(new LaserBeamTrace(new Vec3d(a.x, a.y, a.z), new Vec3d(b.x, b.y, b.z), Direction.UP, null),
                rgb, new Matrix4f(RenderSystem.getProjectionMatrix()), new Matrix4f(RenderSystem.getModelViewMatrix()), range[0], range[1]));
    }

    public static void render() {
        var client = MinecraftClient.getInstance();
        if (BLADES.isEmpty()) return;
        try {
            if (client.options.hudHidden || client.player == null || !client.options.getPerspective().isFirstPerson()) return;
            if (IrisCompatibility.isShaderPackInUse() && !IrisCompatibility.prepareFinalDepthMask()) return;
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorter sorting = RenderSystem.getVertexSorting();
            var shader = RenderSystem.getShader();
            double[] range = new double[2];
            GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, range);
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            try {
                for (Blade blade : BLADES) {
                    RenderSystem.setProjectionMatrix(blade.projection, VertexSorter.BY_DISTANCE);
                    RenderVersion.pushModelView(blade.modelView);
                    GL11.glDepthRange(blade.near, blade.far);
                    LaserRenderLayers.SHADER_BEAM_HALO.startDrawing();
                    try {
                        var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_COLOR);
                        SaberBladeRenderer.renderLate(buffer, blade.ray, blade.rgb, Vec3d.ZERO);
                        BufferRenderer.drawWithGlobalProgram(buffer.end());
                    } finally {
                        LaserRenderLayers.SHADER_BEAM_HALO.endDrawing();
                        RenderVersion.popModelView();
                    }
                }
            } finally {
                GL11.glDepthRange(range[0], range[1]);
                RenderSystem.setProjectionMatrix(projection, sorting);
                RenderSystem.setShader(() -> shader);
                RenderSystem.depthMask(write);
                if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            }
        } finally { BLADES.clear(); }
    }

    private SaberHandRenderer() { }
}
