package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Iris hook. It runs after the first-person hand pathway and records Iris' opaque,
 * pre-hand, and post-hand depth snapshots before the shader pack's final composition.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandRendererMixin {
    @Inject(method = "renderTranslucent", at = @At("RETURN"), remap = false, require = 0)
    private void justifylasers$captureHandAwareDepth(CallbackInfo ci) {
        IrisCompatibility.captureFinalDepthWithHand();
    }
}
