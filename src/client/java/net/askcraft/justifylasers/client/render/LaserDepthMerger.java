package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/** Combines Iris world and hand depth for the post-composite beam pass. */
public final class LaserDepthMerger {
    private static final ByteBuffer COLOR_MASK_STATE = BufferUtils.createByteBuffer(4);
    private static ShaderProgram program;

    public static void initialize() {
        ClientPlatform.registerDepthShader(loadedProgram -> program = loadedProgram);
    }

    /**
     * Adds only pixels changed by the hand pass to opaque depth, excluding unrelated translucency.
     */
    public static boolean merge(
            Framebuffer target,
            int opaqueDepthTexture,
            int beforeHandDepthTexture,
            int afterHandDepthTexture,
            int sourceWidth,
            int sourceHeight,
            float renderScale
    ) {
        ShaderProgram depthProgram = program;
        if (depthProgram == null
                || opaqueDepthTexture <= 0
                || beforeHandDepthTexture <= 0
                || afterHandDepthTexture <= 0) {
            return false;
        }

        RenderSystem.assertOnRenderThread();

        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthWriteEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int previousDepthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        COLOR_MASK_STATE.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, COLOR_MASK_STATE);
        ShaderProgram previousProgram = RenderSystem.getShader();

        try {
            target.beginWrite(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();

            depthProgram.addSampler("OpaqueDepth", opaqueDepthTexture);
            depthProgram.addSampler("BeforeHandDepth", beforeHandDepthTexture);
            depthProgram.addSampler("AfterHandDepth", afterHandDepthTexture);
            setUniform(depthProgram, "SourceSize", sourceWidth, sourceHeight);
            setUniform(depthProgram, "RenderScale", renderScale);
            RenderSystem.setShader(() -> depthProgram);

            BufferBuilder builder = RenderVersion.beginQuads(VertexFormats.POSITION);
            RenderVersion.endVertex(builder.vertex(-1.0F, -1.0F, 0.0F));
            RenderVersion.endVertex(builder.vertex(1.0F, -1.0F, 0.0F));
            RenderVersion.endVertex(builder.vertex(1.0F, 1.0F, 0.0F));
            RenderVersion.endVertex(builder.vertex(-1.0F, 1.0F, 0.0F));
            BufferRenderer.drawWithGlobalProgram(builder.end());
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } finally {
            RenderSystem.depthFunc(previousDepthFunction);
            RenderSystem.depthMask(depthWriteEnabled);
            RenderSystem.colorMask(
                    COLOR_MASK_STATE.get(0) != 0,
                    COLOR_MASK_STATE.get(1) != 0,
                    COLOR_MASK_STATE.get(2) != 0,
                    COLOR_MASK_STATE.get(3) != 0
            );
            if (depthTestEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (blendEnabled) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (cullEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.setShader(() -> previousProgram);
        }
    }

    private static void setUniform(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y);
        }
    }

    private static void setUniform(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private LaserDepthMerger() {
    }
}
