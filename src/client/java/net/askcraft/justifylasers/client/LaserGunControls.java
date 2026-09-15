package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.block.LaserTurretBlock;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.askcraft.justifylasers.laser.WeaponHands;
import net.askcraft.justifylasers.network.LaserGunControlPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public final class LaserGunControls {
    private static PlayerEntity player;
    private static final ItemStack[] previous = {ItemStack.EMPTY, ItemStack.EMPTY};
    private static final boolean[] firing = new boolean[2];
    private static final int[] heartbeat = new int[2];

    public static Hand hand(MinecraftClient client) {
        if (client.player == null) return null;
        for (Hand hand : Hand.values()) if (client.player.getStackInHand(hand).getItem() instanceof LaserGunItem) return hand;
        return null;
    }

    public static boolean firing(MinecraftClient client) {
        return firing(client, Hand.MAIN_HAND) || firing(client, Hand.OFF_HAND);
    }

    public static boolean firing(MinecraftClient client, Hand hand) {
        return WeaponControls.pressed(client, hand) && client.player.getStackInHand(hand).getItem() instanceof LaserGunItem;
    }

    public static boolean aiming(MinecraftClient client) {
        return WeaponControls.available(client) && hand(client) != null && !WeaponHands.dual(client.player)
                && !client.player.isSneaking() && !client.player.isSprinting() && client.options.useKey.isPressed() && !mounting(client);
    }

    public static boolean mounting(MinecraftClient client) {
        return client.world != null && client.crosshairTarget instanceof BlockHitResult hit
                && client.world.getBlockState(hit.getBlockPos()).getBlock() instanceof LaserTurretBlock;
    }

    public static void tick(MinecraftClient client) {
        if (player != client.player) {
            player = client.player;
            java.util.Arrays.fill(firing, false);
            java.util.Arrays.fill(previous, ItemStack.EMPTY);
        }
        for (Hand hand : Hand.values()) {
            int index = hand.ordinal();
            ItemStack stack = player == null ? ItemStack.EMPTY : player.getStackInHand(hand);
            boolean fire = firing(client, hand);
            if (firing[index] && (!fire || previous[index] != stack)) {
                if (player != null && client.getNetworkHandler() != null) {
                    ClientPlatform.sendGunControl(new LaserGunControlPacket(hand, false));
                    LaserGunItem.stopFiring(player, hand);
                }
                firing[index] = false;
            }
            if (fire && (!firing[index] || ++heartbeat[index] >= 10)) {
                ClientPlatform.sendGunControl(new LaserGunControlPacket(hand, true));
                firing[index] = true;
                heartbeat[index] = 0;
            }
            if (fire) LaserGunItem.startFiring(player, hand);
            previous[index] = stack;
        }
    }

    private LaserGunControls() { }
}
