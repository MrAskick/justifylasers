package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Local VBOs use the current material shader; only vertex preparation changes. */
public final class GpuModelRenderer {
    private record MeshKey(Object geometry, boolean extended) { }
    private record Uniforms(int active, int model, int normal, int tint, int light, int overlay, int entity) {
        Uniforms(int program) {
            this(location(program,"jl_ModelActive"), location(program,"jl_ModelMatrix"),
                    location(program,"jl_NormalMatrix"), location(program,"jl_Tint"),
                    location(program,"jl_LightUV"), location(program,"jl_OverlayUV"), location(program,"jl_Entity"));
        }
        private static int location(int program, String name) { return GL20.glGetUniformLocation(program, name); }
    }
    private static final Map<ShaderProgram, Uniforms> UNIFORMS = new WeakHashMap<>();
    private static ShaderProgram vanilla;
    private static Uniforms drawing;
    private static MatrixStack.Entry pose;
    private static int tint, light, overlay;
    private static Object irisState;
    private static Method entityId, blockId, itemId;
    private static boolean inspectedIris;

    public static void initialize() {
        GpuLightEffects.initialize();
        GpuScorchRenderer.initialize();
        ClientPlatform.registerShader("gpu_entity", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, shader -> {
            GpuGeometryCache.clear();
            UNIFORMS.clear();
            vanilla = shader;
        });
    }

    static boolean draw(Object key, int vertices, Consumer<VertexConsumer> bake, MatrixStack matrices,
                        RenderLayer layer, int packedLight, int packedOverlay, int rgb, boolean cutout) {
        if (vertices == 0) return true;
        if (vanilla == null || drawing != null) return false;
        boolean shaders = IrisCompatibility.isShaderPackInUse();
        var previous = RenderSystem.getShader();
        layer.startDrawing();
        try {
            ShaderProgram shader = shaders ? RenderSystem.getShader() : vanilla;
            if (shader == null) return false;
            Uniforms uniforms = UNIFORMS.computeIfAbsent(shader, p -> new Uniforms(p.getGlRef()));
            if (uniforms.active < 0 || shaders && uniforms.entity >= 0 && !readEntityIds()) return false;
            var buffer = GpuGeometryCache.get(new MeshKey(key, shaders), vertices * (shaders ? 64L : 42L),
                    () -> RenderVersion.staticEntityMesh(bake));
            if (buffer == null) return false;
            if (!shaders) shader.getUniform("AlphaCutoff").set(cutout ? .1F : 0F);
            drawing = uniforms;
            pose = matrices.peek();
            tint = rgb; light = packedLight; overlay = packedOverlay;
            buffer.bind();
            try {
                buffer.draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            } finally {
                VertexBuffer.unbind();
                drawing = null;
                pose = null;
            }
            return true;
        } finally {
            layer.endDrawing();
            RenderSystem.setShader(() -> previous);
        }
    }

    public static void bindUniforms() {
        if (drawing == null) return;
        var u = drawing;
        GL20.glUniform1i(u.active, 1);
        try (var stack = MemoryStack.stackPush()) {
            GL20.glUniformMatrix4fv(u.model, false, pose.getPositionMatrix().get(stack.mallocFloat(16)));
            GL20.glUniformMatrix3fv(u.normal, false, pose.getNormalMatrix().get(stack.mallocFloat(9)));
        }
        GL20.glUniform4f(u.tint, (tint >> 16 & 255)/255F, (tint >> 8 & 255)/255F, (tint & 255)/255F, 1);
        GL20.glUniform2i(u.light, light & 65535, light >>> 16);
        GL20.glUniform2i(u.overlay, overlay & 65535, overlay >>> 16);
        if (u.entity >= 0) GL20.glUniform3i(u.entity, currentEntity, currentBlock, currentItem);
    }

    public static void unbindUniforms() {
        if (drawing != null) GL20.glUniform1i(drawing.active, 0);
    }

    private static int currentEntity, currentBlock, currentItem;

    private static boolean readEntityIds() {
        if (!inspectedIris) {
            inspectedIris = true;
            try {
                Class<?> type = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState");
                irisState = type.getField("INSTANCE").get(null);
                entityId = type.getMethod("getCurrentRenderedEntity");
                blockId = type.getMethod("getCurrentRenderedBlockEntity");
                itemId = type.getMethod("getCurrentRenderedItem");
            } catch (ReflectiveOperationException failure) {
                irisState = null;
                JustifyLasers.LOGGER.warn("Cannot read Iris material IDs for cached geometry", failure);
            }
        }
        if (irisState == null) return false;
        try {
            currentEntity = (int) entityId.invoke(irisState);
            currentBlock = (int) blockId.invoke(irisState);
            currentItem = (int) itemId.invoke(irisState);
            return true;
        } catch (ReflectiveOperationException failure) {
            irisState = null;
            JustifyLasers.LOGGER.warn("Using uncached geometry: Iris material IDs are unavailable", failure);
            return false;
        }
    }

    private GpuModelRenderer() { }
}
