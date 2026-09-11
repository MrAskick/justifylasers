package net.askcraft.justifylasers.client.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.render.LaserDepthMerger;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflective bridge to Iris so the client can also run without it installed.
 */
public final class IrisCompatibility {
    private static final Object IRIS_API;
    private static final Method SHADER_PACK_IN_USE;
    private static final Method RENDERING_SHADOW_PASS;
    private static final Method GET_PIPELINE_MANAGER;
    private static final Method GET_PIPELINE_NULLABLE;
    private static final Field PIPELINE_RENDER_TARGETS;
    private static final Method GET_DEPTH_TEXTURE;
    private static final Method GET_DEPTH_TEXTURE_NO_TRANSLUCENTS;
    private static final Method GET_DEPTH_TEXTURE_NO_HAND;
    private static final Method GET_DEPTH_TEXTURE_ID;
    private static final Method GET_TARGET_WIDTH;
    private static final Method GET_TARGET_HEIGHT;
    private static final Method GET_CURRENT_PACK;
    private static final Method GET_SHADER_PACK_OPTIONS;
    private static final Method GET_OPTION_VALUES;
    private static final Method GET_STRING_VALUE_OR_DEFAULT;

    private static int depthReadFramebuffer = -1;
    private static boolean depthBridgeWarningLogged;
    private static boolean finalDepthCaptured;
    private static int capturedOpaqueDepthTexture;
    private static int capturedBeforeHandDepthTexture;
    private static int capturedAfterHandDepthTexture;
    private static int capturedDepthWidth;
    private static int capturedDepthHeight;
    private static float capturedRenderScale = 1.0F;
    private static Object cachedShaderPack;
    private static float cachedShaderRenderScale = 1.0F;

    static {
        Object api = null;
        Method shaderPackInUse = null;
        Method renderingShadowPass = null;
        Method getPipelineManager = null;
        Method getPipelineNullable = null;
        Field pipelineRenderTargets = null;
        Method getDepthTexture = null;
        Method getDepthTextureNoTranslucents = null;
        Method getDepthTextureNoHand = null;
        Method getDepthTextureId = null;
        Method getTargetWidth = null;
        Method getTargetHeight = null;
        Method getCurrentPack = null;
        Method getShaderPackOptions = null;
        Method getOptionValues = null;
        Method getStringValueOrDefault = null;

        if (Platform.isModLoaded("iris") || Platform.isModLoaded("oculus")) {
            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                shaderPackInUse = apiClass.getMethod("isShaderPackInUse");
                renderingShadowPass = apiClass.getMethod("isRenderingShadowPass");
                JustifyLasers.LOGGER.info("Iris detected: shader-aware laser rendering enabled");
            } catch (ReflectiveOperationException | LinkageError exception) {
                JustifyLasers.LOGGER.warn(
                        "Iris is installed, but its compatibility API could not be initialized; using vanilla rendering",
                        exception
                );
                api = null;
                shaderPackInUse = null;
                renderingShadowPass = null;
            }

            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                Class<?> pipelineManagerClass = Class.forName(
                        "net.irisshaders.iris.pipeline.PipelineManager"
                );
                Class<?> renderingPipelineClass = Class.forName(
                        "net.irisshaders.iris.pipeline.IrisRenderingPipeline"
                );
                Class<?> renderTargetsClass = Class.forName(
                        "net.irisshaders.iris.targets.RenderTargets"
                );

                getPipelineManager = irisClass.getMethod("getPipelineManager");
                getPipelineNullable = pipelineManagerClass.getMethod("getPipelineNullable");
                pipelineRenderTargets = renderingPipelineClass.getDeclaredField("renderTargets");
                pipelineRenderTargets.setAccessible(true);
                getDepthTexture = renderTargetsClass.getMethod("getDepthTexture");
                getDepthTextureNoTranslucents = renderTargetsClass.getMethod(
                        "getDepthTextureNoTranslucents"
                );
                getDepthTextureNoHand = renderTargetsClass.getMethod("getDepthTextureNoHand");
                getDepthTextureId = Class.forName("net.irisshaders.iris.targets.DepthTexture")
                        .getMethod("getTextureId");
                getTargetWidth = renderTargetsClass.getMethod("getCurrentWidth");
                getTargetHeight = renderTargetsClass.getMethod("getCurrentHeight");
            } catch (ReflectiveOperationException | LinkageError exception) {
                JustifyLasers.LOGGER.warn(
                        "Iris depth bridge is unavailable; shader laser falloff will be disabled",
                        exception
                );
                getPipelineManager = null;
                getPipelineNullable = null;
                pipelineRenderTargets = null;
                getDepthTexture = null;
                getDepthTextureNoTranslucents = null;
                getDepthTextureNoHand = null;
                getDepthTextureId = null;
                getTargetWidth = null;
                getTargetHeight = null;
            }

            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                Class<?> shaderPackClass = Class.forName(
                        "net.irisshaders.iris.shaderpack.ShaderPack"
                );
                Class<?> shaderPackOptionsClass = Class.forName(
                        "net.irisshaders.iris.shaderpack.option.ShaderPackOptions"
                );
                Class<?> optionValuesClass = Class.forName(
                        "net.irisshaders.iris.shaderpack.option.values.OptionValues"
                );

                getCurrentPack = irisClass.getMethod("getCurrentPack");
                getShaderPackOptions = shaderPackClass.getMethod("getShaderPackOptions");
                getOptionValues = shaderPackOptionsClass.getMethod("getOptionValues");
                getStringValueOrDefault = optionValuesClass.getMethod(
                        "getStringValueOrDefault",
                        String.class
                );
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Pack render scaling is optional. A full-size depth copy remains valid.
            }
        }

        IRIS_API = api;
        SHADER_PACK_IN_USE = shaderPackInUse;
        RENDERING_SHADOW_PASS = renderingShadowPass;
        GET_PIPELINE_MANAGER = getPipelineManager;
        GET_PIPELINE_NULLABLE = getPipelineNullable;
        PIPELINE_RENDER_TARGETS = pipelineRenderTargets;
        GET_DEPTH_TEXTURE = getDepthTexture;
        GET_DEPTH_TEXTURE_NO_TRANSLUCENTS = getDepthTextureNoTranslucents;
        GET_DEPTH_TEXTURE_NO_HAND = getDepthTextureNoHand;
        GET_DEPTH_TEXTURE_ID = getDepthTextureId;
        GET_TARGET_WIDTH = getTargetWidth;
        GET_TARGET_HEIGHT = getTargetHeight;
        GET_CURRENT_PACK = getCurrentPack;
        GET_SHADER_PACK_OPTIONS = getShaderPackOptions;
        GET_OPTION_VALUES = getOptionValues;
        GET_STRING_VALUE_OR_DEFAULT = getStringValueOrDefault;
    }

    private IrisCompatibility() {
    }

    public static void initialize() {
        // Forces optional API discovery during client initialization.
    }

    public static boolean isShaderPackInUse() {
        return invokeBoolean(SHADER_PACK_IN_USE);
    }

    public static boolean isRenderingShadowPass() {
        return invokeBoolean(RENDERING_SHADOW_PASS);
    }

    /**
     * Captures world, pre-hand and post-hand depth before the pack's final composition.
     */
    public static void captureFinalDepthWithHand() {
        finalDepthCaptured = false;
        if (!isShaderPackInUse()
                || GET_PIPELINE_MANAGER == null
                || GET_PIPELINE_NULLABLE == null
                || PIPELINE_RENDER_TARGETS == null
                || GET_DEPTH_TEXTURE == null
                || GET_DEPTH_TEXTURE_NO_TRANSLUCENTS == null
                || GET_DEPTH_TEXTURE_NO_HAND == null
                || GET_DEPTH_TEXTURE_ID == null
                || GET_TARGET_WIDTH == null
                || GET_TARGET_HEIGHT == null) {
            return;
        }

        RenderSystem.assertOnRenderThread();
        try {
            Object renderTargets = getRenderTargets();
            if (renderTargets == null) {
                return;
            }

            int sourceWidth = (int) GET_TARGET_WIDTH.invoke(renderTargets);
            int sourceHeight = (int) GET_TARGET_HEIGHT.invoke(renderTargets);
            int afterHandDepthTexture = (int) GET_DEPTH_TEXTURE.invoke(renderTargets);
            int opaqueDepthTexture = depthTextureId(
                    GET_DEPTH_TEXTURE_NO_TRANSLUCENTS.invoke(renderTargets)
            );
            int beforeHandDepthTexture = depthTextureId(
                    GET_DEPTH_TEXTURE_NO_HAND.invoke(renderTargets)
            );
            if (sourceWidth <= 0
                    || sourceHeight <= 0
                    || afterHandDepthTexture <= 0
                    || opaqueDepthTexture <= 0
                    || beforeHandDepthTexture <= 0) {
                return;
            }

            capturedOpaqueDepthTexture = opaqueDepthTexture;
            capturedBeforeHandDepthTexture = beforeHandDepthTexture;
            capturedAfterHandDepthTexture = afterHandDepthTexture;
            capturedDepthWidth = sourceWidth;
            capturedDepthHeight = sourceHeight;
            capturedRenderScale = getShaderRenderScale();
            finalDepthCaptured = true;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError exception) {
            warnDepthBridgeOnce("Iris final depth merge failed", exception);
        }
    }

    /**
     * Prepares world and hand occlusion for the late beam pass; falls back to world depth
     * when the optional hand hook is unavailable.
     */
    public static boolean prepareFinalDepthMask() {
        if (finalDepthCaptured) {
            finalDepthCaptured = false;
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                // Drain errors so the merge result only reflects this pass.
            }
            boolean merged = LaserDepthMerger.merge(
                    MinecraftClient.getInstance().getFramebuffer(),
                    capturedOpaqueDepthTexture,
                    capturedBeforeHandDepthTexture,
                    capturedAfterHandDepthTexture,
                    capturedDepthWidth,
                    capturedDepthHeight,
                    capturedRenderScale
            );
            if (!merged) {
                warnDepthBridgeOnce("Iris final depth merge failed", null);
            }
            return merged;
        }
        return copyWorldDepthToMainFramebuffer();
    }

    /**
     * Copies opaque-world depth to the final framebuffer, accounting for pack render scaling.
     */
    private static boolean copyWorldDepthToMainFramebuffer() {
        if (!isShaderPackInUse()
                || GET_PIPELINE_MANAGER == null
                || GET_PIPELINE_NULLABLE == null
                || PIPELINE_RENDER_TARGETS == null
                || GET_DEPTH_TEXTURE_NO_TRANSLUCENTS == null
                || GET_DEPTH_TEXTURE_ID == null
                || GET_TARGET_WIDTH == null
                || GET_TARGET_HEIGHT == null) {
            return false;
        }

        RenderSystem.assertOnRenderThread();
        Framebuffer mainFramebuffer = MinecraftClient.getInstance().getFramebuffer();

        try {
            Object renderTargets = getRenderTargets();
            if (renderTargets == null) {
                return false;
            }
            int depthTexture = depthTextureId(
                    GET_DEPTH_TEXTURE_NO_TRANSLUCENTS.invoke(renderTargets)
            );
            int sourceWidth = (int) GET_TARGET_WIDTH.invoke(renderTargets);
            int sourceHeight = (int) GET_TARGET_HEIGHT.invoke(renderTargets);

            if (depthTexture <= 0
                    || sourceWidth <= 0
                    || sourceHeight <= 0
                    || mainFramebuffer.fbo <= 0
                    || !mainFramebuffer.useDepthAttachment) {
                return false;
            }

            if (depthReadFramebuffer < 0) {
                depthReadFramebuffer = GlStateManager.glGenFramebuffers();
            }

            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                // Ignore stale errors when checking this depth copy.
            }

            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, depthReadFramebuffer);
            GlStateManager._glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D,
                    depthTexture,
                    0
            );
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);

            if (GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                    != GL30.GL_FRAMEBUFFER_COMPLETE) {
                warnDepthBridgeOnce("Iris depth framebuffer is incomplete", null);
                return false;
            }

            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthReadFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFramebuffer.fbo);
            float renderScale = getShaderRenderScale();
            int viewportWidth = Math.max(1, Math.min(
                    sourceWidth,
                    (int) Math.ceil(sourceWidth * renderScale)
            ));
            int viewportHeight = Math.max(1, Math.min(
                    sourceHeight,
                    (int) Math.ceil(sourceHeight * renderScale)
            ));
            GlStateManager._glBlitFrameBuffer(
                    0,
                    0,
                    viewportWidth,
                    viewportHeight,
                    0,
                    0,
                    mainFramebuffer.textureWidth,
                    mainFramebuffer.textureHeight,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );

            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                warnDepthBridgeOnce("Iris depth copy failed with OpenGL error " + error, null);
                return false;
            }
            return true;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError exception) {
            warnDepthBridgeOnce("Iris depth copy failed; shader laser falloff was skipped", exception);
            return false;
        } finally {
            // Both successful and failed paths must leave the final color target active.
            mainFramebuffer.beginWrite(false);
        }
    }

    private static Object getRenderTargets() throws ReflectiveOperationException {
        Object pipelineManager = GET_PIPELINE_MANAGER.invoke(null);
        Object pipeline = GET_PIPELINE_NULLABLE.invoke(pipelineManager);
        if (pipeline == null || !PIPELINE_RENDER_TARGETS.getDeclaringClass().isInstance(pipeline)) {
            return null;
        }
        return PIPELINE_RENDER_TARGETS.get(pipeline);
    }

    private static int depthTextureId(Object depthTexture) throws ReflectiveOperationException {
        return (int) GET_DEPTH_TEXTURE_ID.invoke(depthTexture);
    }

    /**
     * Some shader packs render their G-buffer into a lower-left sub-viewport and upscale it in
     * the final pass. The depth attachment stays in that compact coordinate space, so it needs
     * the same viewport expansion before it can occlude a post-composite effect.
     */
    private static float getShaderRenderScale() {
        if (GET_CURRENT_PACK == null
                || GET_SHADER_PACK_OPTIONS == null
                || GET_OPTION_VALUES == null
                || GET_STRING_VALUE_OR_DEFAULT == null) {
            return 1.0F;
        }

        try {
            Object optionalPack = GET_CURRENT_PACK.invoke(null);
            if (!(optionalPack instanceof Optional<?> optional) || optional.isEmpty()) {
                cachedShaderPack = null;
                cachedShaderRenderScale = 1.0F;
                return 1.0F;
            }

            Object shaderPack = optional.get();
            if (shaderPack == cachedShaderPack) {
                return cachedShaderRenderScale;
            }

            Object options = GET_SHADER_PACK_OPTIONS.invoke(shaderPack);
            Object values = GET_OPTION_VALUES.invoke(options);
            String configuredScale = (String) GET_STRING_VALUE_OR_DEFAULT.invoke(
                    values,
                    "ResolutionScale"
            );
            float parsedScale = Float.parseFloat(configuredScale);
            cachedShaderPack = shaderPack;
            cachedShaderRenderScale = Math.max(0.1F, Math.min(1.0F, parsedScale));
            return cachedShaderRenderScale;
        } catch (ReflectiveOperationException | ClassCastException | NumberFormatException exception) {
            cachedShaderPack = null;
            cachedShaderRenderScale = 1.0F;
            return 1.0F;
        }
    }

    private static boolean invokeBoolean(Method method) {
        if (IRIS_API == null || method == null) {
            return false;
        }
        try {
            return (boolean) method.invoke(IRIS_API);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return false;
        }
    }

    private static void warnDepthBridgeOnce(String message, Throwable exception) {
        if (depthBridgeWarningLogged) {
            return;
        }
        depthBridgeWarningLogged = true;
        if (exception == null) {
            JustifyLasers.LOGGER.warn(message);
        } else {
            JustifyLasers.LOGGER.warn(message, exception);
        }
    }
}
