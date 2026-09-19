package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.askcraft.justifylasers.client.compat.IrisMirrorPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris 1.7 caches terrain programs independently of the active world pipeline. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides",remap=false)
public abstract class MirrorSodiumShaderMixin {
    @Inject(method="getProgramOverride",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void justifylasers$secondaryCamera(@Coerce Object pass, @Coerce Object type, CallbackInfoReturnable<Object> cir) {
        if (IrisMirrorPass.ownsTerrain(this)) cir.setReturnValue(IrisMirrorPass.terrain("getProgramOverride", pass, type));
        else if(MirrorRenderer.rendering() && !IrisMirrorPass.shaders()) cir.setReturnValue(null);
    }

    @Inject(method="bindFramebuffer",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void justifylasers$bindMirror(@Coerce Object pass, CallbackInfo ci) {
        if (IrisMirrorPass.ownsTerrain(this)) { IrisMirrorPass.terrain("bindFramebuffer", pass); ci.cancel(); }
    }

    @Inject(method="unbindFramebuffer",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void justifylasers$unbindMirror(CallbackInfo ci) {
        if (IrisMirrorPass.ownsTerrain(this)) { IrisMirrorPass.terrain("unbindFramebuffer"); ci.cancel(); }
    }
}
