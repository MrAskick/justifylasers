package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.MirrorControls;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mouse.class)
public abstract class MirrorMouseMixin {
    @Redirect(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void justifylasers$turnMirror(ClientPlayerEntity player, double dx, double dy) {
        if (!MirrorControls.mouse(dx, dy)) player.changeLookDirection(dx, dy);
    }
}
