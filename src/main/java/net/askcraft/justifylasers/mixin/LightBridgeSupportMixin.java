package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class LightBridgeSupportMixin {
    @Inject(method = "isEntityOnAir", at = @At("RETURN"), cancellable = true)
    private void justifylasers$bridgeIsGround(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Vanilla's flight check scans block states, so the ordinary collision hook isn't enough.
        if (cir.getReturnValueZ() && (net.askcraft.justifylasers.laser.LaserBeamEffects.supports(entity)
                || !LightBridgeNetwork.collisions(entity.getWorld(),
                entity.getBoundingBox().expand(.0625).stretch(0, -.55, 0)).isEmpty())) cir.setReturnValue(false);
    }
}
