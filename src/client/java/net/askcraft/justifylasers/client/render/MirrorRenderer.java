package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.compat.IrisMirrorPass;
import net.askcraft.justifylasers.client.compat.MirrorClipPlane;
import net.askcraft.justifylasers.laser.MirrorGeometry;
import net.askcraft.justifylasers.laser.OpticalGeometry;
import net.askcraft.justifylasers.mixin.client.MirrorClientAccess;
import net.askcraft.justifylasers.mixin.client.MirrorGameRendererAccess;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Planar, non-recursive world captures. No player/entity position or game tick is changed. */
public final class MirrorRenderer {
    @FunctionalInterface public interface WorldPass { void render(Camera camera, MatrixStack view, Matrix4f projection); }
    private static final Map<BlockPos, Surface> VISIBLE = new LinkedHashMap<>();
    private static final Map<BlockPos, Capture> CAPTURES = new LinkedHashMap<>();
    private static net.minecraft.client.world.ClientWorld world;
    private static ShaderProgram program;
    private static Framebuffer worldDepth;
    private static Surface rendering;
    private static long frame;
    private record Surface(BlockPos pos, Vec3d normal, MirrorGeometry.Frame basis) { }
    private record Capture(Framebuffer target, Vec3d normal, Matrix4f depthToMain, long frame) { }

    public static void initialize() {
        ClientPlatform.registerShader("mirror", VertexFormats.POSITION_TEXTURE_COLOR, shader -> program = shader);
    }
    public static boolean rendering() { return rendering != null; }
    public static boolean hidden(BlockPos pos) { return rendering != null && rendering.pos.equals(pos); }

    static void queue(LaserOpticBlockEntity mirror) {
        if (rendering() || mirror.getWorld() == null || !ClientSettings.get().mirrorReflections || IrisCompatibility.isRenderingShadowPass()) return;
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d center = Vec3d.ofCenter(mirror.getPos());
        if (center.squaredDistanceTo(camera.getPos()) > ClientSettings.get().mirrorDistance * ClientSettings.get().mirrorDistance) return;
        Vec3d normal = mirror.normal();
        if (Math.abs(camera.getPos().subtract(center).dotProduct(normal)) < .04) return;
        VISIBLE.put(mirror.getPos(), new Surface(mirror.getPos(), normal, MirrorGeometry.frame(normal, mirror.facing())));
    }

    public static void prepare(Camera camera, MatrixStack view, Matrix4f projection, float delta, WorldPass pass) {
        if (rendering()) return;
        var client = MinecraftClient.getInstance();
        if (world != client.world) { clear(); world = client.world; }
        frame++;
        if (world == null || !ClientSettings.get().mirrorReflections || !projection.isFinite()) { clear(); return; }
        var wanted = VISIBLE.values().stream().filter(surface -> world.getBlockEntity(surface.pos) instanceof LaserOpticBlockEntity)
                .sorted(Comparator.comparingDouble(surface -> Vec3d.ofCenter(surface.pos).squaredDistanceTo(camera.getPos())))
                .limit(ClientSettings.get().maxMirrors).toList();
        CAPTURES.entrySet().removeIf(entry -> {
            boolean remove = wanted.stream().noneMatch(surface -> surface.pos.equals(entry.getKey()));
            if (remove) entry.getValue().target.delete();
            return remove;
        });
        IrisMirrorPass.retain(CAPTURES.keySet());
        VISIBLE.clear();
        // Each visible view uses THIS frame's eye position, never a stale image sliding across the pane.
        // The user-selected view count is a hard limit on additional world renders.
        for (Surface surface : wanted) capture(surface, camera, view, projection, delta, pass);
    }

    private static void capture(Surface surface, Camera camera, MatrixStack view, Matrix4f projection, float delta, WorldPass pass) {
        var client = MinecraftClient.getInstance();
        Framebuffer main = client.getFramebuffer();
        Capture previous = CAPTURES.get(surface.pos);
        Framebuffer target = previous == null ? null : previous.target;
        Vec3d center = Vec3d.ofCenter(surface.pos), normal = surface.normal;
        var reflection = PlanarReflection.create(camera.getPos(), view.peek().getPositionMatrix(), projection, center, normal);
        Camera reflected = new ReflectedCamera(camera, reflection.eye(), normal, delta);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int winding = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        boolean clipEnabled = GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0);
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var oldSorting = RenderSystem.getVertexSorting();
        var oldProgram = RenderSystem.getShader();
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        var crosshair = client.crosshairTarget;
        var targetedEntity = client.targetedEntity;
        boolean shaders = IrisCompatibility.isShaderPackInUse();
        IrisMirrorPass isolated = null;
        rendering = surface;
        RenderVersion.pushModelView(new Matrix4f());
        try {
            // Creating/resizing a framebuffer changes GL bindings too, so it belongs inside the saved-state scope.
            if (target == null) target = new SimpleFramebuffer(main.textureWidth,main.textureHeight,true,MinecraftClient.IS_SYSTEM_MAC);
            else if (target.textureWidth!=main.textureWidth || target.textureHeight!=main.textureHeight)
                target.resize(main.textureWidth,main.textureHeight,MinecraftClient.IS_SYSTEM_MAC);
            ((MirrorClientAccess)client).justifylasers$framebuffer(target);
            ((MirrorGameRendererAccess)client.gameRenderer).justifylasers$camera(reflected);
            if (shaders) {
                isolated = IrisMirrorPass.begin(surface.pos, ClientSettings.get().mirrorShaders);
                if (isolated == null) return;
            }
            target.setClearColor(0,0,0,1); target.clear(MinecraftClient.IS_SYSTEM_MAC); target.beginWrite(true);
            Matrix4f captureProjection = reflection.projection();
            Matrix4f captureDepth = reflection.depthToMain();
            if (IrisMirrorPass.shaders()) {
                captureProjection = projection;
                captureDepth = new Matrix4f();
                Vec3d towardsViewer = camera.getPos().subtract(center).dotProduct(normal) > 0 ? normal : normal.negate();
                MirrorClipPlane.set(PlanarReflection.shaderPlane(projection, reflection.view(), reflection.eye(), center, towardsViewer));
                GL11.glEnable(GL30.GL_CLIP_DISTANCE0);
            }
            RenderSystem.setProjectionMatrix(captureProjection, VertexSorter.BY_DISTANCE);
            GL11.glFrontFace(winding == GL11.GL_CCW ? GL11.GL_CW : GL11.GL_CCW);
            var stack = new MatrixStack(); stack.multiplyPositionMatrix(reflection.view());
            stack.peek().getNormalMatrix().set(reflection.view());
            pass.render(reflected, stack, captureProjection);
            CAPTURES.put(surface.pos, new Capture(target, normal, captureDepth, frame));
        } finally {
            MirrorClipPlane.clear();
            if (clipEnabled) GL11.glEnable(GL30.GL_CLIP_DISTANCE0); else GL11.glDisable(GL30.GL_CLIP_DISTANCE0);
            rendering = null;
            if (isolated != null) isolated.close();
            ((MirrorClientAccess)client).justifylasers$framebuffer(main);
            ((MirrorGameRendererAccess)client.gameRenderer).justifylasers$camera(camera);
            client.crosshairTarget = crosshair; client.targetedEntity = targetedEntity;
            RenderVersion.popModelView();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting); RenderSystem.setShader(() -> oldProgram);
            GL11.glFrontFace(winding);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            IrisCompatibility.beginFrame();
            CubeLensRenderer.beginFrame();
        }
    }

    public static void composite(Camera camera, MatrixStack matrices, Matrix4f projection) {
        if (rendering() || program == null || CAPTURES.isEmpty() || !ClientSettings.get().mirrorReflections) return;
        if (IrisCompatibility.isShaderPackInUse() && !IrisCompatibility.prepareFinalDepthMask()) return;
        var main = MinecraftClient.getInstance().getFramebuffer();
        Matrix4f transform = new Matrix4f(projection).mul(matrices.peek().getPositionMatrix());
        var oldProgram = RenderSystem.getShader();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] textures = new int[3], shaderTextures = new int[3];
        for(int unit=0;unit<3;unit++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0+unit);
            textures[unit]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); shaderTextures[unit]=RenderSystem.getShaderTexture(unit);
        }
        boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            if(worldDepth==null) worldDepth=new SimpleFramebuffer(main.textureWidth,main.textureHeight,true,MinecraftClient.IS_SYSTEM_MAC);
            else if(worldDepth.textureWidth!=main.textureWidth || worldDepth.textureHeight!=main.textureHeight)
                worldDepth.resize(main.textureWidth,main.textureHeight,MinecraftClient.IS_SYSTEM_MAC);
            worldDepth.copyDepthFrom(main);
            main.beginWrite(true);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            // Compare the aperture against a separate world-depth texture, then write the reflected
            // objects' virtual depth. Sampling the live attachment would cause a GPU feedback loop.
            RenderSystem.enableDepthTest(); RenderSystem.depthFunc(GL11.GL_ALWAYS); RenderSystem.depthMask(true);
            RenderSystem.disableCull(); RenderSystem.disableBlend(); RenderSystem.setShader(() -> program);
            program.getUniform("MainTransform").set(transform);
            program.addSampler("WorldDepth",worldDepth.getDepthAttachment());
            for (var surface : VISIBLE.values().stream().sorted(Comparator.comparingDouble(
                    (Surface surface)->Vec3d.ofCenter(surface.pos).squaredDistanceTo(camera.getPos())).reversed()).toList()) {
                Capture capture = CAPTURES.get(surface.pos);
                if (capture == null || capture.frame!=frame || Math.abs(capture.normal.dotProduct(surface.normal)) < .999) continue;
                program.addSampler("Scene", capture.target.getColorAttachment());
                program.addSampler("SceneDepth",capture.target.getDepthAttachment());
                program.getUniform("DepthToMain").set(capture.depthToMain);
                var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_TEXTURE_COLOR);
                double s=MirrorGeometry.HALF,c=MirrorGeometry.CUT;
                double[][] edges={{-s,-s+c},{-s,s-c},{-s+c,s},{s-c,s},{s,s-c},{s,-s+c},{s-c,-s},{-s+c,-s}};
                Vec3d center = Vec3d.ofCenter(surface.pos);
                for(int i=0;i<8;i++) {
                    double[][] tri={{0,0},edges[i],edges[(i+1)%8],{0,0}};
                    for(int j=0;j<4;j++) {
                        Vec3d point=center.add(surface.basis.right().multiply(tri[j][0])).add(surface.basis.up().multiply(tri[j][1])).subtract(camera.getPos());
                        RenderVersion.endVertex(buffer.vertex((float)point.x,(float)point.y,(float)point.z).texture(0,0).color(255,255,255,255));
                    }
                }
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            for(int unit=0;unit<3;unit++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0+unit); RenderSystem.bindTexture(textures[unit]); RenderSystem.setShaderTexture(unit,shaderTextures[unit]);
            }
            RenderSystem.activeTexture(activeTexture);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw); GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            if(srgb) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB); else GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            RenderSystem.setShader(() -> oldProgram); RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(write);
            if(depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if(cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if(blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        }
    }

    public static void clear() {
        IrisMirrorPass.clearShaders();
        CAPTURES.values().forEach(capture -> capture.target.delete()); CAPTURES.clear(); VISIBLE.clear();
        if(worldDepth!=null) { worldDepth.delete(); worldDepth=null; }
    }

    private static final class ReflectedCamera extends Camera {
        ReflectedCamera(Camera camera, Vec3d eye, Vec3d normal, float delta) {
            var client = MinecraftClient.getInstance();
            update(client.world, camera.getFocusedEntity(), true, false, delta);
            Vec3d forward=OpticalGeometry.reflect(new Vec3d(camera.getHorizontalPlane()),normal);
            setRotation((float)Math.toDegrees(Math.atan2(-forward.x,forward.z)),(float)-Math.toDegrees(Math.asin(forward.y)));
            setPos(eye);
        }
    }
    private MirrorRenderer() { }
}
