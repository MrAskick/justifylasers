package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.laser.SaberCombat;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class SaberSwingMixin {
    @Shadow public ServerPlayerEntity player;

    // TAIL runs after vanilla has handed the packet to the server thread.
    @Inject(method = "onHandSwing", at = @At("TAIL"))
    private void justifylasers$saberSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        SaberCombat.swing(player, packet.getHand());
    }
}
