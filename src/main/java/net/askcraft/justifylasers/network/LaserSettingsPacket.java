package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;

public record LaserSettingsPacket(int syncId, int buttonId) implements FabricPacket {
    public static final PacketType<LaserSettingsPacket> TYPE = PacketType.create(
            JustifyLasers.id("emitter_setting"), LaserSettingsPacket::new);

    public LaserSettingsPacket(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void write(PacketByteBuf buf) {
        // Vanilla ButtonClickC2SPacket truncates button IDs to a signed byte in 1.20.1.
        buf.writeVarInt(syncId);
        buf.writeVarInt(buttonId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public boolean apply(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator()
                && player.currentScreenHandler instanceof LaserEmitterScreenHandler handler
                && handler.syncId == syncId
                && handler.onButtonClick(player, buttonId);
    }
}
