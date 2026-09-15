package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

public final class WeaponHands {
    public static boolean weapon(ItemStack stack) {
        return stack.getItem() instanceof LaserGunItem || stack.getItem() instanceof LaserSaberItem;
    }

    public static boolean dual(PlayerEntity player) {
        return player != null && weapon(player.getMainHandStack()) && weapon(player.getOffHandStack());
    }

    public static Arm arm(PlayerEntity player, Hand hand) {
        return hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    public static Hand hand(PlayerEntity player, Arm arm) {
        return player.getMainArm() == arm ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    public static Hand attackHand(PlayerEntity player, boolean rightButton) {
        if (dual(player)) return hand(player, rightButton ? Arm.RIGHT : Arm.LEFT);
        return weapon(player.getMainHandStack()) ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    private WeaponHands() { }
}
