package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.laser.SaberCombat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SaberGuardMixin {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void justifylasers$guardMelee(DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
        if ((Object) this instanceof PlayerEntity player && SaberCombat.guardMelee(player, source, amount)) ci.setReturnValue(false);
    }
}
