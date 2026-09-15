package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/** Item thumbnails are baked between frames, never recursively inside another item's renderer. */
public final class SchematicIcons {
    private static final int SIZE = 128, LIMIT = 64;
    private static final VertexConsumerProvider.Immediate CONSUMERS = RenderVersion.immediateBuffer();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static int nextId;
    private static long frame;
    private static boolean baking;
    private static SimpleFramebuffer target;

    private static final class Entry {
        final ItemStack stack;
        final Identifier texture = JustifyLasers.id("dynamic/schematic/" + nextId++);
        boolean ready;
        long used;
        Entry(ItemStack stack) { this.stack = stack.copy(); this.stack.setCount(1); used = frame; }
    }

    static Identifier request(ItemStack stack) {
        if (stack.isEmpty() || baking) return null;
        for (var entry : ENTRIES) if (GameVersion.canStack(entry.stack, stack)) {
            entry.used = frame;
            return entry.ready ? entry.texture : null;
        }
        if (ENTRIES.size() < LIMIT + 8) ENTRIES.add(new Entry(stack));
        return null;
    }

    public static void prepare() {
        frame++;
        var client = MinecraftClient.getInstance();
        if (client.world == null) return;
        while (ENTRIES.size() > LIMIT) {
            var oldest = ENTRIES.stream().min(java.util.Comparator.comparingLong(entry -> entry.used)).orElseThrow();
            client.getTextureManager().destroyTexture(oldest.texture);
            ENTRIES.remove(oldest);
        }
        int remaining = 4;
        for (var entry : ENTRIES) if (!entry.ready && remaining-- > 0) bake(entry);
    }

    private static void bake(Entry entry) {
        var client = MinecraftClient.getInstance();
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4], scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorter = RenderSystem.getVertexSorting();
        var shader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor().clone();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int shaderTexture = RenderSystem.getShaderTexture(0);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        baking = true;
        RenderVersion.pushModelView(new Matrix4f());
        try {
            if (target == null) target = new SimpleFramebuffer(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
            target.setClearColor(0, 0, 0, 0);
            RenderSystem.disableScissor();
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            target.clear(MinecraftClient.IS_SYSTEM_MAC);
            target.beginWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, 1000, 3000), VertexSorter.BY_Z);
            RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); RenderSystem.enableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(Float.MAX_VALUE); RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            DiffuseLighting.enableGuiDepthLighting();
            MatrixStack matrices = new MatrixStack();
            matrices.translate(SIZE / 2F, SIZE / 2F, -2000);
            matrices.scale(112, -112, 112);
            client.getItemRenderer().renderItem(entry.stack, ModelTransformationMode.GUI, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV, matrices, CONSUMERS, client.world, 0);
            CONSUMERS.draw();
            RenderSystem.bindTexture(target.getColorAttachment());
            NativeImage pixels = new NativeImage(SIZE, SIZE, false);
            pixels.loadFromTextureImage(0, false);
            pixels.mirrorVertically();
            client.getTextureManager().registerTexture(entry.texture, new NativeImageBackedTexture(pixels));
            entry.ready = true;
        } finally {
            RenderVersion.popModelView();
            RenderSystem.setProjectionMatrix(projection, sorter);
            RenderSystem.setShader(() -> shader); RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderTexture(0, shaderTexture);
            RenderSystem.activeTexture(activeTexture); RenderSystem.bindTexture(texture);
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read); GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            RenderSystem.depthMask(write);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            if (srgb) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            baking = false;
        }
    }

    public static void clear() {
        for (var entry : ENTRIES) MinecraftClient.getInstance().getTextureManager().destroyTexture(entry.texture);
        ENTRIES.clear();
        nextId = 0;
        if (target != null) { target.delete(); target = null; }
    }

    private SchematicIcons() { }
}
