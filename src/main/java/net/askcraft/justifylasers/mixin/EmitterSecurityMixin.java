package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
abstract class EmitterSecurityMixin {
    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void protectPrivateEmitter(BlockPos pos, CallbackInfoReturnable<Boolean> callback) {
        if (player.getWorld().getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter && !emitter.canAccess(player)) {
            callback.setReturnValue(false);
        }
    }
}
