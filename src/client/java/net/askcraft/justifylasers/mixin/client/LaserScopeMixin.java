package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.LaserGunRenderer;
import net.askcraft.justifylasers.client.render.LaserScopeRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 500)
public abstract class LaserScopeMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void justifylasers$prepareSchematicIcons(CallbackInfo ci) {
        net.askcraft.justifylasers.client.render.SchematicIcons.prepare();
    }
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void justifylasers$zoomCamera(Camera camera, float delta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        // Zoom world geometry, not the hand projection. Iris uses this same camera for its world passes.
        if (changingFov && LaserScopeRenderer.isScopeHeld())
            cir.setReturnValue(LaserScopeRenderer.magnifiedFov(cir.getReturnValue(), LaserGunRenderer.aim(delta)));
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void justifylasers$steadyAim(CallbackInfo ci) {
        if (LaserScopeRenderer.isScopeHeld() && LaserGunRenderer.aim(1) > 0.01F) ci.cancel();
    }

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void justifylasers$beginScopeFrame(CallbackInfo ci) {
        net.askcraft.justifylasers.client.compat.IrisCompatibility.beginFrame();
        LaserScopeRenderer.beginFrame();
        net.askcraft.justifylasers.client.render.SaberHandRenderer.beginFrame();
        net.askcraft.justifylasers.client.render.TabletTextRenderer.beginFrame();
        net.askcraft.justifylasers.client.render.CubeLensRenderer.beginFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void justifylasers$releaseScopeTexture(CallbackInfo ci) {
        LaserScopeRenderer.clear();
        net.askcraft.justifylasers.client.render.MirrorRenderer.clear();
        net.askcraft.justifylasers.client.render.CubeLensRenderer.clear();
        net.askcraft.justifylasers.client.render.SchematicIcons.clear();
    }
}
