package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SaberTogglePacket() {
    public static final Identifier ID = JustifyLasers.id("saber_toggle");
    public SaberTogglePacket(PacketByteBuf buffer) { this(); }
    public void write(PacketByteBuf buffer) { }
    public void apply(PlayerEntity player) {
        for (var hand : net.minecraft.util.Hand.values()) SaberCombat.toggle(player, hand);
    }
}
