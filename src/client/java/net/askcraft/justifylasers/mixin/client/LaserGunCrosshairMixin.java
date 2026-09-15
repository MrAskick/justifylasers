package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.LaserGunRenderer;
import net.askcraft.justifylasers.client.screen.TabletScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class LaserGunCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void justifylasers$useScopeReticle(CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof TabletScreen || LaserGunRenderer.aim(1) > 0.85F) ci.cancel();
    }
}
