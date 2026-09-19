package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.askcraft.justifylasers.network.MirrorAimPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.MathHelper;

public final class MirrorControls {
    private static BlockHitResult grabbed;
    private static Hand hand;
    private static int slot, ticks;
    private static long started;
    private static double yaw, pitch, sentYaw, sentPitch;
    private static boolean dragged;

    public static boolean begin(MinecraftClient client) {
        if (grabbed != null) return true;
        if (client.player == null || client.world == null || client.currentScreen != null
                || !(client.crosshairTarget instanceof BlockHitResult hit)
                || !(client.world.getBlockEntity(hit.getBlockPos()) instanceof LaserOpticBlockEntity mirror)
                || mirror.kind() != LaserOpticBlock.Kind.MIRROR) return false;
        for (Hand candidate : Hand.values()) {
            var stack = client.player.getStackInHand(candidate);
            if (!(stack.getItem() instanceof LaserConfiguratorItem) || LaserConfiguratorItem.mode(stack) != 3) continue;
            grabbed = hit; hand = candidate; slot = hand == Hand.MAIN_HAND ? client.player.getInventory().selectedSlot : 40;
            yaw = sentYaw = mirror.yaw(); pitch = sentPitch = mirror.pitch();
            started = Util.getMeasuringTimeMs(); ticks = 0; dragged = false;
            return true;
        }
        return false;
    }

    public static boolean mouse(double dx, double dy) {
        if (grabbed == null) return false;
        if (Util.getMeasuringTimeMs() - started >= 180) {
            dragged = true;
            yaw = sentYaw + MathHelper.clamp(MathHelper.wrapDegrees(yaw + dx * .15 - sentYaw), -40, 40);
            pitch = MathHelper.clamp(pitch - dy * .15, Math.max(-89.5, sentPitch - 40), Math.min(89.5, sentPitch + 40));
        }
        return true;
    }

    public static void tick(MinecraftClient client) {
        if (grabbed == null) return;
        if (client.player == null || client.world == null || !client.player.isAlive() || client.currentScreen != null
                || slot != (hand == Hand.MAIN_HAND ? client.player.getInventory().selectedSlot : 40)
                || !(client.player.getStackInHand(hand).getItem() instanceof LaserConfiguratorItem)
                || LaserConfiguratorItem.mode(client.player.getStackInHand(hand)) != 3
                || !(client.world.getBlockEntity(grabbed.getBlockPos()) instanceof LaserOpticBlockEntity mirror)
                || mirror.kind() != LaserOpticBlock.Kind.MIRROR || client.player.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(grabbed.getBlockPos())) > 64) {
            grabbed = null; return;
        }
        if (!client.options.useKey.isPressed()) {
            if (!dragged && client.interactionManager != null) client.interactionManager.interactBlock(client.player, hand, grabbed);
            else send();
            grabbed = null;
            return;
        }
        if (Util.getMeasuringTimeMs() - started >= 180) {
            dragged = true;
            if (++ticks % 2 == 0) send();
            client.player.sendMessage(Text.translatable("message.justifylasers.mirror_angles", Math.round(yaw), Math.round(pitch)), true);
        }
    }

    private static void send() {
        if (Math.abs(MathHelper.wrapDegrees(yaw - sentYaw)) < .02 && Math.abs(pitch - sentPitch) < .02) return;
        ClientPlatform.sendMirrorAim(new MirrorAimPacket(grabbed.getBlockPos(), hand, slot, yaw, pitch));
        sentYaw = yaw; sentPitch = pitch;
    }
    private MirrorControls() { }
}
