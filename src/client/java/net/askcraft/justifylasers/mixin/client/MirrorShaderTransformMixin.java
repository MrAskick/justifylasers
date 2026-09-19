package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.MirrorClipPlane;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class MirrorShaderTransformMixin {
    @Inject(method = {"patchVanilla", "patchSodium"}, at = @At("RETURN"), cancellable = true, remap = false)
    private static void justifylasers$clipPlane(CallbackInfoReturnable<Map<Object, String>> cir) {
        cir.setReturnValue(MirrorClipPlane.patch(cir.getReturnValue()));
    }
}
