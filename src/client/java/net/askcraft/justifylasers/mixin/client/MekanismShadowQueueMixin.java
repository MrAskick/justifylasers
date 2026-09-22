package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.client.render.RenderTickHandler", remap = false)
abstract class MekanismShadowQueueMixin {
    @Inject(method = "addTransparentRenderer", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void justifylasers$mainViewEffectsOnly(CallbackInfo ci) {
        // Mekanism replays this queue after translucent terrain. Shadow-view entries are
        // otherwise replayed with the wrong projection, leaving energy-core ghosts in the sky.
        if (IrisCompatibility.isRenderingShadowPass()) ci.cancel();
    }
}
