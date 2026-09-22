package net.askcraft.justifylasers.smoke.mixin;

import net.askcraft.justifylasers.smoke.ArtifactWorldSmoke;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ParticleManager.class)
abstract class ArtifactParticleMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void isolate(CallbackInfo ci) {
        if (ArtifactWorldSmoke.mask == 4) ci.cancel();
    }
}
