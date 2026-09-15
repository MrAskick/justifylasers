package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.LaserGunRenderer;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class LaserGunHeldItemMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void justifylasers$renderGun(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
                                        float swing, ItemStack stack, float equip, MatrixStack matrices,
                                        VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        var screen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
        if (screen instanceof net.askcraft.justifylasers.client.screen.TabletScreen tabletScreen) {
            Hand tabletHand = tabletScreen.getScreenHandler().tabletSlot() == 40 ? Hand.OFF_HAND : Hand.MAIN_HAND;
            if (hand != tabletHand) { ci.cancel(); return; }
        } else {
            boolean mainTablet = player.getMainHandStack().getItem() instanceof net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
            boolean offTablet = player.getOffHandStack().getItem() instanceof net.askcraft.justifylasers.item.ExtraterrestrialTabletItem;
            if (mainTablet && hand == Hand.OFF_HAND || !mainTablet && offTablet && hand == Hand.MAIN_HAND) { ci.cancel(); return; }
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.ExtraterrestrialTabletItem) {
            net.askcraft.justifylasers.client.render.TabletRenderer.renderFirstPerson(player, stack, hand, delta, matrices, consumers, light);
            ci.cancel(); return;
        }
        if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof net.askcraft.justifylasers.client.screen.TabletScreen) {
            ci.cancel(); return;
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LaserSaberItem) {
            net.askcraft.justifylasers.client.render.LaserSaberRenderer.renderFirstPerson(player, stack, hand, delta, equip, matrices, consumers, light);
            ci.cancel();
            return;
        }
        var other = player.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        if (stack.isEmpty() && other.getItem() instanceof net.askcraft.justifylasers.item.LaserSaberItem saber && saber.isStaff()) {
            ci.cancel();
            return;
        }
        if (stack.getItem() instanceof LaserGunItem) {
            LaserGunRenderer.renderFirstPerson(player, stack, hand, delta, equip, swing, matrices, consumers, light);
            ci.cancel();
        } else if (other.getItem() instanceof LaserGunItem
                && stack.isEmpty()) ci.cancel();
    }
}
