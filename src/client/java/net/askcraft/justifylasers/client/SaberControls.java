package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.render.LaserSaberRenderer;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.laser.SaberState;
import net.askcraft.justifylasers.laser.WeaponHands;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

public final class SaberControls {
    private static PlayerEntity player;
    private static final int[] nextSwing = new int[2], bufferedUntil = new int[2];

    public static boolean held(MinecraftClient client) {
        return client.player != null && (client.player.getMainHandStack().getItem() instanceof LaserSaberItem
                || client.player.getOffHandStack().getItem() instanceof LaserSaberItem);
    }

    private static void updatePlayer(MinecraftClient client) {
        if (player == client.player) return;
        player = client.player;
        java.util.Arrays.fill(nextSwing, 0);
        java.util.Arrays.fill(bufferedUntil, 0);
    }

    public static void attack(MinecraftClient client) { attack(client, false); }

    public static void attack(MinecraftClient client, boolean rightButton) {
        if (!held(client)) return;
        updatePlayer(client);
        Hand hand = WeaponHands.attackHand(player, rightButton);
        if (!(player.getStackInHand(hand).getItem() instanceof LaserSaberItem)) return;
        bufferedUntil[hand.ordinal()] = player.age + 3;
        tick(client);
    }

    public static void tick(MinecraftClient client) {
        updatePlayer(client);
        if (!held(client) || !WeaponControls.available(client)) {
            java.util.Arrays.fill(bufferedUntil, 0);
            return;
        }
        for (Hand hand : Hand.values()) {
            int index = hand.ordinal();
            var stack = player.getStackInHand(hand);
            if (!(stack.getItem() instanceof LaserSaberItem)) { bufferedUntil[index] = 0; continue; }
            var state = SaberCombat.state(player, hand);
            if (state.action() == SaberState.Action.PARRY) nextSwing[index] = Math.min(nextSwing[index], player.age);
            if (state.action() == SaberState.Action.BROKEN || state.action() == SaberState.Action.RECOIL) continue;
            if (LaserSaberItem.active(stack) && !player.getItemCooldownManager().isCoolingDown(stack.getItem()) && player.age >= nextSwing[index]
                    && (WeaponControls.pressed(client, hand) || bufferedUntil[index] > 0 && player.age <= bufferedUntil[index])) {
                LaserSaberRenderer.onSwing(player, hand);
                if (player.isUsingItem() && player.getActiveHand() == hand && client.interactionManager != null)
                    client.interactionManager.stopUsingItem(player);
                player.swingHand(hand);
                nextSwing[index] = player.age + SaberCombat.state(player, hand).duration();
                bufferedUntil[index] = 0;
            }
        }
    }

    private SaberControls() { }
}
