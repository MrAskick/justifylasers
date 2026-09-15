package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class SaberAttackMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void justifylasers$validatedSaberAttack(Entity target, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        Hand hand = net.askcraft.justifylasers.laser.WeaponHands.attackHand(player, false);
        if (player.getStackInHand(hand).getItem() instanceof LaserSaberItem) {
            SaberCombat.swing(player, hand);
            ci.cancel();
        }
    }
}
