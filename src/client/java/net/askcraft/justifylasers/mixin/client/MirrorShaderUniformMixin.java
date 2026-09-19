package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.MirrorClipPlane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class MirrorShaderUniformMixin {
    @Inject(method = "generalCommonUniforms", at = @At("TAIL"), remap = false)
    private static void justifylasers$uniform(@Coerce Object holder, @Coerce Object notifier, @Coerce Object directives, CallbackInfo ci) {
        MirrorClipPlane.uniforms(holder);
    }
}
