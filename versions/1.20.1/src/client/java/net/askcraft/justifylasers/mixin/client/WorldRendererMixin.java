package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.LaserBeamLateRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Priority 500 intentionally places this callback after Iris' level-finalization callback
 * (default priority 1000). At that point the shader pack's final sky image is already on-screen.
 */
@Mixin(value = WorldRenderer.class, priority = 500)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void justifylasers$renderFinalLaserHalos(
            MatrixStack matrices,
            float tickDelta,
            long limitTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightmapTextureManager lightmapTextureManager,
            Matrix4f projectionMatrix,
            CallbackInfo ci
    ) {
        LaserBeamLateRenderer.render(camera, matrices, projectionMatrix);
    }
}
