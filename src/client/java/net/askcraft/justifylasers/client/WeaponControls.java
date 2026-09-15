package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.laser.WeaponHands;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

public final class WeaponControls {
    public static boolean available(MinecraftClient client) {
        return client.player != null && client.currentScreen == null && !client.isPaused()
                && client.player.isAlive() && !client.player.isSpectator();
    }

    public static boolean pressed(MinecraftClient client, Hand hand) {
        if (!available(client)) return false;
        if (WeaponHands.dual(client.player)) return WeaponHands.arm(client.player, hand) == Arm.RIGHT
                ? client.options.useKey.isPressed() : client.options.attackKey.isPressed();
        return hand == WeaponHands.attackHand(client.player, false) && client.options.attackKey.isPressed();
    }

    private WeaponControls() { }
}
