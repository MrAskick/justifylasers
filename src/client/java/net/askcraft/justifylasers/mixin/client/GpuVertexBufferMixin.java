package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.GpuModelRenderer;
import net.minecraft.client.gl.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public abstract class GpuVertexBufferMixin {
    // VertexBuffer binds the selected vanilla/Iris program before reaching this overload.
    @Inject(method = "draw()V", at = @At("HEAD"))
    private void justifylasers$localTransform(CallbackInfo ci) { GpuModelRenderer.bindUniforms(); }

    @Inject(method = "draw()V", at = @At("RETURN"))
    private void justifylasers$resetTransform(CallbackInfo ci) { GpuModelRenderer.unbindUniforms(); }
}
