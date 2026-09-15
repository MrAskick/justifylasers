package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.LaserScopeRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class LaserScopeHudMixin {
    // The caller has finished world post-processing, including Iris exposure and color-space conversion.
    @Inject(method = "render", at = @At("HEAD"))
    private void justifylasers$drawOpticalLens(CallbackInfo ci) {
        LaserScopeRenderer.render();
        net.askcraft.justifylasers.client.render.SaberHandRenderer.render();
        net.askcraft.justifylasers.client.render.TabletTextRenderer.render();
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void justifylasers$drawStamina(CallbackInfo ci) {
        net.askcraft.justifylasers.client.SaberHud.render();
    }
}
