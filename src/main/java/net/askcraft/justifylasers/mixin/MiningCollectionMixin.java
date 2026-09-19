package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.laser.MiningCollection;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class MiningCollectionMixin {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void justifylasers$collectMiningDrops(Entity entity, CallbackInfoReturnable<Boolean> result) {
        if (MiningCollection.capture((ServerWorld)(Object)this, entity)) result.setReturnValue(true);
    }
}
