package net.askcraft.justifylasers.smoke.mixin;

import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Checks the actual native render path, before an invalid frustum can hang world entry. */
@Mixin(WorldRenderer.class)
public abstract class WorldFrustumSmokeMixin {
    @Shadow private Frustum frustum;
    @Unique private Frustum justifylasers$expectedMainFrustum;
    @Unique private boolean justifylasers$enteredWorld;

    @Inject(method = "setupFrustum", at = @At("RETURN"))
    private void justifylasers$rememberMainFrustum(CallbackInfo ci) {
        if (!MirrorRenderer.rendering() && justifylasers$expectedMainFrustum == null)
            justifylasers$expectedMainFrustum = frustum;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void justifylasers$checkMainFrustum(CallbackInfo ci) {
        if (!MirrorRenderer.rendering() && (justifylasers$expectedMainFrustum == null
                || justifylasers$expectedMainFrustum != frustum))
            throw new AssertionError("The mirror pass replaced vanilla's main camera frustum");
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void justifylasers$completedWorldFrame(CallbackInfo ci) {
        if (MirrorRenderer.rendering()) return;
        justifylasers$expectedMainFrustum = null;
        if (!justifylasers$enteredWorld) {
            justifylasers$enteredWorld = true;
            LoggerFactory.getLogger("justifylasers-client-smoke").info("WORLD_ENTRY_FRUSTUM_SMOKE_PASSED");
        }
    }
}
