package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.GpuMeshShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.TransformPatcher", remap = false)
public abstract class GpuShaderTransformMixin {
    @Inject(method = "patchVanilla", at = @At("RETURN"), cancellable = true, remap = false)
    private static void justifylasers$localMeshes(CallbackInfoReturnable<Map<Object, String>> cir) {
        cir.setReturnValue(GpuMeshShaderSource.patch(cir.getReturnValue()));
    }
}
