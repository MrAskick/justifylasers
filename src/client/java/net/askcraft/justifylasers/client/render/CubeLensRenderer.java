package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.CubeOptics;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Screen-space convex lenses sample the tone-mapped scene, never an unlit shader G-buffer. */
public final class CubeLensRenderer {
    static final double RADIUS = 4.74 / 16;
    private static final Map<Integer, CubeOptics.Frame> CUBES = new LinkedHashMap<>();
    private static ShaderProgram program;
    private static int scene, depth, width, height;

    private record Lens(Vec3d center, Vec3d normal, Vec3d right, Vec3d up) {
        Vec3d point(double u, double v) {
            double r = Math.hypot(u, v);
            return center.add(right.multiply(u * RADIUS)).add(up.multiply(v * RADIUS))
                    .add(normal.multiply((6.92 - 0.41 * r * r) / 16));
        }
    }

    public static void initialize() {
        ClientPlatform.registerShader("cube_lens", VertexFormats.POSITION_TEXTURE_COLOR, shader -> program = shader);
    }

    public static void beginFrame() { CUBES.clear(); }

    static void queue(int id, CubeOptics.Frame frame) {
        if (net.askcraft.justifylasers.client.ClientSettings.get().cubeLenses && !IrisCompatibility.isRenderingShadowPass()) CUBES.put(id, frame);
    }

    public static void render(Camera camera, MatrixStack matrices, Matrix4f projection) {
        if (CUBES.isEmpty()) return;
        try {
            var settings = net.askcraft.justifylasers.client.ClientSettings.get();
            if (!settings.cubeLenses || program == null || IrisCompatibility.isShaderPackInUse() && !IrisCompatibility.prepareFinalDepthMask()) return;
            var client = MinecraftClient.getInstance();
            var target = client.getFramebuffer();
            if (target.getDepthAttachment() <= 0) return;
            Matrix4f transform = new Matrix4f(projection).mul(matrices.peek().getPositionMatrix());
            List<Lens> lenses = new ArrayList<>();
            CUBES.values().stream().filter(frame -> frame.center().squaredDistanceTo(camera.getPos()) < settings.cubeLensDistance * settings.cubeLensDistance)
                    .sorted(Comparator.comparingDouble(frame -> frame.center().squaredDistanceTo(camera.getPos())))
                    .limit(settings.maxLensCubes).forEach(frame -> {
                        for (Direction side : Direction.values()) {
                            Vec3d local = Vec3d.of(side.getVector());
                            Vec3d normal = frame.toWorld(local).subtract(frame.center());
                            if (camera.getPos().subtract(frame.center()).dotProduct(normal) <= 0.45) continue;
                            Vec3d right = Math.abs(local.y) > 0.9 ? frame.right() : frame.up().crossProduct(normal);
                            Lens lens = new Lens(frame.center(), normal, right, normal.crossProduct(right));
                            if (project(transform, camera.getPos(), lens.point(0, 0)) != null) lenses.add(lens);
                        }
                    });
            if (lenses.isEmpty()) return;
            lenses.sort(Comparator.comparingDouble((Lens lens) -> lens.center.squaredDistanceTo(camera.getPos())).reversed());
            composite(target, transform, camera.getPos(), lenses);
        } finally { CUBES.clear(); }
    }

    private static void composite(net.minecraft.client.gl.Framebuffer target, Matrix4f transform, Vec3d camera, List<Lens> lenses) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), shaderTexture = RenderSystem.getShaderTexture(0);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB), depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ShaderProgram previousProgram = RenderSystem.getShader();
        try {
            boolean resize = width != target.textureWidth || height != target.textureHeight;
            width = target.textureWidth; height = target.textureHeight;
            scene = copy(scene, target, resize, false);
            depth = copy(depth, target, resize, true);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
            RenderSystem.viewport(0, 0, width, height);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_LEQUAL); RenderSystem.depthMask(false);
            RenderSystem.disableCull(); RenderSystem.disableBlend();
            RenderSystem.setShader(() -> program);
            program.addSampler("Scene", scene);
            program.addSampler("SceneDepth", depth);
            program.getUniform("Viewport").set((float) width, (float) height);
            program.getUniform("Magnification").set((float) net.askcraft.justifylasers.client.ClientSettings.get().cubeMagnification);
            for (Lens lens : lenses) {
                var center = project(transform, camera, lens.point(0, 0));
                if (center == null) continue;
                program.getUniform("Center").set(center.x * 0.5F + 0.5F, center.y * 0.5F + 0.5F);
                var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_TEXTURE_COLOR);
                for (int band = 0; band < 4; band++) for (int segment = 0; segment < 48; segment++) {
                    double a = segment * Math.PI / 24, b = (segment + 1) * Math.PI / 24;
                    double lo = band / 4d, hi = (band + 1) / 4d;
                    double[][] uv = {{lo * Math.cos(a), lo * Math.sin(a)}, {hi * Math.cos(a), hi * Math.sin(a)},
                            {hi * Math.cos(b), hi * Math.sin(b)}, {lo * Math.cos(b), lo * Math.sin(b)}};
                    Vector4f[] points = new Vector4f[4];
                    boolean visible = true;
                    for (int i = 0; i < 4; i++) {
                        points[i] = project(transform, camera, lens.point(uv[i][0], uv[i][1]));
                        visible &= points[i] != null;
                    }
                    if (!visible) continue;
                    for (int i = 0; i < 4; i++) RenderVersion.endVertex(buffer.vertex(points[i].x, points[i].y, points[i].z)
                            .texture((float) uv[i][0], (float) uv[i][1]).color(255, 255, 255, 255));
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (srgb) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.setShaderTexture(0, shaderTexture); RenderSystem.bindTexture(binding); RenderSystem.activeTexture(activeTexture);
            RenderSystem.setShader(() -> previousProgram);
            RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(write);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    private static int copy(int texture, net.minecraft.client.gl.Framebuffer target, boolean resize, boolean depthImage) {
        if (texture == 0) { texture = GL11.glGenTextures(); resize = true; }
        RenderSystem.bindTexture(texture);
        if (resize) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depthImage ? GL30.GL_DEPTH_COMPONENT32F : GL11.GL_RGBA8,
                    width, height, 0, depthImage ? GL11.GL_DEPTH_COMPONENT : GL11.GL_RGBA,
                    depthImage ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, depthImage ? GL11.GL_NEAREST : GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, depthImage ? GL11.GL_NEAREST : GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        }
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.fbo);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        return texture;
    }

    private static Vector4f project(Matrix4f transform, Vec3d camera, Vec3d world) {
        Vec3d p = world.subtract(camera);
        Vector4f clip = transform.transform(new Vector4f((float) p.x, (float) p.y, (float) p.z, 1));
        return clip.w <= 0.05F ? null : clip.div(clip.w);
    }

    public static void clear() {
        beginFrame();
        if (scene != 0) RenderSystem.deleteTexture(scene);
        if (depth != 0) RenderSystem.deleteTexture(depth);
        scene = depth = width = height = 0;
    }

    private CubeLensRenderer() { }
}
