package net.askcraft.justifylasers.smoke.mixin;

import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.askcraft.justifylasers.smoke.MirrorTemporalSmoke;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MirrorRenderer.class, remap = false)
abstract class MirrorTemporalSmokeMixin {
    @Inject(method = "composite", at = @At("RETURN"))
    private static void sample(Camera camera, MatrixStack matrices, Matrix4f projection, CallbackInfo ci) {
        if (Boolean.getBoolean("justifylasers.smokeMirrorTemporal")) MirrorTemporalSmoke.sample(camera, matrices, projection);
    }
}
