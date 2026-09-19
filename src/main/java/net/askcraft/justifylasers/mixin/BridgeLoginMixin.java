package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.bridge.BridgeJoinSupport;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
abstract class BridgeLoginMixin {
    @Unique private NbtCompound justifylasers$landing;

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void justifylasers$saveLanding(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound checkpoint = BridgeJoinSupport.checkpoint((ServerPlayerEntity) (Object) this);
        if (!checkpoint.isEmpty()) nbt.put(BridgeJoinSupport.KEY, checkpoint);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void justifylasers$readLanding(NbtCompound nbt, CallbackInfo ci) {
        justifylasers$landing = nbt.getCompound(BridgeJoinSupport.KEY).copy();
    }

    @Inject(method = {"onSpawn", "tick"}, at = @At("HEAD"))
    private void justifylasers$restoreLanding(CallbackInfo ci) {
        if (justifylasers$landing == null) return;
        NbtCompound landing = justifylasers$landing;
        justifylasers$landing = null;
        BridgeJoinSupport.restore((ServerPlayerEntity) (Object) this, landing);
    }
}
