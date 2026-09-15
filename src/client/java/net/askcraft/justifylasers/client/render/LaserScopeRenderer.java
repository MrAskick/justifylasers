package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** A modest ADS camera zoom plus optical magnification inside the projected lens aperture. */
public final class LaserScopeRenderer {
    private static final float[][] APERTURE = {{-1.63F,14.24F},{1.63F,14.24F},{1.63F,17.16F},{-1.63F,17.16F}};
    private static List<Vector2f> lens;
    private static int texture, width, height;
    private static ShaderProgram scopeProgram;

    public static void initialize() {
        ClientPlatform.registerShader("scope", VertexFormats.POSITION_TEXTURE_COLOR, shader -> scopeProgram = shader);
    }

    public static void beginFrame() { lens = null; }

    public static boolean isScopeHeld() {
        var client = MinecraftClient.getInstance();
        return client.player != null && !net.askcraft.justifylasers.laser.WeaponHands.dual(client.player) && client.options.getPerspective().isFirstPerson()
                && (client.player.getMainHandStack().getItem() instanceof LaserGunItem
                || client.player.getOffHandStack().getItem() instanceof LaserGunItem);
    }

    public static double cameraMagnification(float aim) { return 1 + 0.5 * Math.max(0, Math.min(1, aim)); }

    public static double magnifiedFov(double fov, float aim) {
        return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(fov) * 0.5) / cameraMagnification(aim)));
    }

    public static void queue(MatrixStack matrices) {
        if (!isScopeHeld()) return;
        if (!net.askcraft.justifylasers.client.ClientSettings.get().scopeLens) return;
        var client = MinecraftClient.getInstance();
        if (!client.options.getPerspective().isFirstPerson() || IrisCompatibility.isRenderingShadowPass()) return;
        Matrix4f transform = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                .mul(matrices.peek().getPositionMatrix());
        List<Vector2f> rear = project(transform, 12.43F);
        List<Vector2f> front = project(transform, 7.51F);
        // Both ends of the sight must remain open along the viewing ray, including at hip height.
        lens = clip(rear, front);
    }

    public static void render() {
        if (!net.askcraft.justifylasers.client.ClientSettings.get().scopeLens) { lens = null; return; }
        List<Vector2f> aperture = lens;
        lens = null;
        if (aperture == null || aperture.size() < 3 || scopeProgram == null) return;
        var client = MinecraftClient.getInstance();
        if (!client.options.getPerspective().isFirstPerson() || client.player == null || client.player.isInvisible()) return;
        var target = client.getFramebuffer();
        float cx = 0, cy = 0;
        for (Vector2f p : aperture) { cx += p.x / aperture.size(); cy += p.y / aperture.size(); }
        if (cx < -1.2F || cx > 1.2F || cy < -1.2F || cy > 1.2F) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int shaderTexture = RenderSystem.getShaderTexture(0);
        var program = RenderSystem.getShader();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorting = RenderSystem.getVertexSorting();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            if (texture == 0) texture = GL11.glGenTextures();
            RenderSystem.bindTexture(texture);
            if (width != target.textureWidth || height != target.textureHeight) {
                width = target.textureWidth; height = target.textureHeight;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
            RenderSystem.viewport(0, 0, width, height);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorter.BY_Z);
            RenderVersion.pushModelView(new Matrix4f());
            try {
                RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.disableBlend();
                // A dedicated post-process avoids Iris replacing the vanilla shader with a lit G-buffer program.
                RenderSystem.setShader(() -> scopeProgram);
                scopeProgram.addSampler("Scene", texture);
                scopeProgram.getUniform("Textured").set(1);
                var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_TEXTURE_COLOR);
                float opticalScale = (float) (2.5 / cameraMagnification(LaserGunRenderer.aim(1)));
                for (int i = 1; i + 1 < aperture.size(); i++) {
                    for (Vector2f point : new Vector2f[]{aperture.get(0), aperture.get(i), aperture.get(i + 1), aperture.get(i + 1)}) {
                        // Sample around the camera's aim, not the off-axis gun body below it.
                        float u = ((point.x - cx) / opticalScale + 1) * 0.5F;
                        float v = ((point.y - cy) / opticalScale + 1) * 0.5F;
                        RenderVersion.endVertex(buffer.vertex(point.x, point.y, 0).texture(u, v).color(255, 255, 255, 255));
                    }
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
                if (LaserGunRenderer.aim(1) > 0.85F) {
                    scopeProgram.getUniform("Textured").set(0);
                    var reticle = RenderVersion.beginQuads(VertexFormats.POSITION_TEXTURE_COLOR);
                    float px = 2F / width, py = 2F / height;
                    rect(reticle, cx - px * 2, cy - py * 2, cx + px * 2, cy + py * 2, 0x27110A);
                    rect(reticle, cx - px, cy - py, cx + px, cy + py, 0xFFF6CF);
                    rect(reticle, cx - px * 9, cy - py, cx - px * 4, cy + py, 0xFF5728);
                    rect(reticle, cx + px * 4, cy - py, cx + px * 9, cy + py, 0xFF5728);
                    rect(reticle, cx - px, cy - py * 9, cx + px, cy - py * 4, 0xFF5728);
                    BufferRenderer.drawWithGlobalProgram(reticle.end());
                }
            } finally { RenderVersion.popModelView(); }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (srgb) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.setShaderTexture(0, shaderTexture); RenderSystem.bindTexture(binding); RenderSystem.activeTexture(activeTexture);
            RenderSystem.setProjectionMatrix(projection, sorting); RenderSystem.setShader(() -> program);
            RenderSystem.depthMask(write);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static void rect(net.minecraft.client.render.BufferBuilder buffer, float x1, float y1, float x2, float y2, int rgb) {
        for (float[] p : new float[][]{{x1,y1},{x2,y1},{x2,y2},{x1,y2}})
            RenderVersion.endVertex(buffer.vertex(p[0], p[1], 0).texture(0, 0).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255));
    }

    private static List<Vector2f> project(Matrix4f transform, float z) {
        List<Vector2f> result = new ArrayList<>(4);
        for (float[] corner : APERTURE) {
            Vector4f p = transform.transform(new Vector4f(corner[0], corner[1], z, 1));
            if (p.w <= 0.01F) return List.of();
            result.add(new Vector2f(p.x / p.w, p.y / p.w));
        }
        return result;
    }

    static List<Vector2f> clip(List<Vector2f> subject, List<Vector2f> window) {
        if (subject.isEmpty() || window.isEmpty()) return List.of();
        float area = 0;
        for (int i = 0; i < window.size(); i++) area += window.get(i).x * window.get((i + 1) % window.size()).y
                - window.get(i).y * window.get((i + 1) % window.size()).x;
        float winding = Math.signum(area);
        if (winding == 0) return List.of();
        for (int i = 0; i < window.size() && !subject.isEmpty(); i++) {
            Vector2f a = window.get(i), b = window.get((i + 1) % window.size());
            List<Vector2f> output = new ArrayList<>();
            Vector2f previous = subject.get(subject.size() - 1);
            float before = side(a, b, previous) * winding;
            for (Vector2f current : subject) {
                float after = side(a, b, current) * winding;
                if ((before >= 0) != (after >= 0)) {
                    float t = before / (before - after);
                    output.add(new Vector2f(previous).lerp(current, t));
                }
                if (after >= 0) output.add(current);
                previous = current; before = after;
            }
            subject = output;
        }
        return subject;
    }

    private static float side(Vector2f a, Vector2f b, Vector2f p) {
        return (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x);
    }

    public static void clear() {
        beginFrame();
        if (texture != 0) { RenderSystem.deleteTexture(texture); texture = width = height = 0; }
    }

    private LaserScopeRenderer() { }
}
