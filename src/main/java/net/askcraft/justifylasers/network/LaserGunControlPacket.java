package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.item.LaserGunItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public record LaserGunControlPacket(Hand hand, boolean firing) {
    public static final Identifier ID = JustifyLasers.id("gun_control");
    public LaserGunControlPacket(PacketByteBuf buffer) { this(buffer.readEnumConstant(Hand.class), buffer.readBoolean()); }
    public void write(PacketByteBuf buffer) { buffer.writeEnumConstant(hand); buffer.writeBoolean(firing); }

    public boolean apply(PlayerEntity player) {
        if (!firing) {
            LaserGunItem.stopFiring(player, hand);
            return true;
        }
        if (!player.isAlive() || player.isSpectator() || player.currentScreenHandler != player.playerScreenHandler
                || !(player.getStackInHand(hand).getItem() instanceof LaserGunItem)) return false;
        LaserGunItem.startFiring(player, hand);
        return true;
    }
}
