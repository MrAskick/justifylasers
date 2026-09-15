package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemFeatureRenderer.class)
public abstract class SaberHeldFeatureMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void justifylasers$worldSpaceSaber(LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                              Arm arm, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (entity instanceof PlayerEntity && stack.getItem() instanceof LaserSaberItem) ci.cancel();
    }
}
