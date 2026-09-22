package net.askcraft.justifylasers.smoke.mixin;

import net.askcraft.justifylasers.smoke.PerformanceWorldSmoke;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class PerformanceSmokeMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void measureFrame(CallbackInfo ci) {
        if (Boolean.getBoolean("justifylasers.smokePerformance")) PerformanceWorldSmoke.frame();
    }
}
