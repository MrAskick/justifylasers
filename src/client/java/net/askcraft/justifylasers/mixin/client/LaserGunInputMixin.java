package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.LaserGunControls;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class LaserGunInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void justifylasers$noMelee(CallbackInfoReturnable<Boolean> ci) {
        if (net.askcraft.justifylasers.client.SaberControls.held(MinecraftClient.getInstance())) {
            net.askcraft.justifylasers.client.SaberControls.attack(MinecraftClient.getInstance(), false);
            ci.setReturnValue(false);
            return;
        }
        if (LaserGunControls.hand(MinecraftClient.getInstance()) != null) ci.setReturnValue(false);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void justifylasers$noMining(boolean breaking, CallbackInfo ci) {
        if (net.askcraft.justifylasers.client.SaberControls.held(MinecraftClient.getInstance())) ci.cancel();
        if (LaserGunControls.hand(MinecraftClient.getInstance()) != null) ci.cancel();
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void justifylasers$aimInsteadOfUse(CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (net.askcraft.justifylasers.laser.WeaponHands.dual(client.player)) {
            net.askcraft.justifylasers.client.SaberControls.attack(client, true);
            ci.cancel();
            return;
        }
        if (LaserGunControls.hand(client) != null && !client.player.isSneaking() && !LaserGunControls.mounting(client)) ci.cancel();
    }

    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private void justifylasers$keepFireHeld(ClientPlayerInteractionManager manager, PlayerEntity player) {
        if (!(player.getActiveItem().getItem() instanceof LaserGunItem) || !LaserGunControls.firing(MinecraftClient.getInstance()))
            manager.stopUsingItem(player);
    }
}
