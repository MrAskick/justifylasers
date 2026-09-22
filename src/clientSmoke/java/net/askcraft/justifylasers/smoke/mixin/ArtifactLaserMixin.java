package net.askcraft.justifylasers.smoke.mixin;

import net.askcraft.justifylasers.smoke.ArtifactWorldSmoke;
import net.askcraft.justifylasers.client.render.LaserWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LaserWorldRenderer.class, remap = false)
abstract class ArtifactLaserMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private static void isolate(CallbackInfo ci) {
        if (ArtifactWorldSmoke.mask == 2) ci.cancel();
    }
}
