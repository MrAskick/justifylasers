package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record LaserSettingsPacket(int syncId, int buttonId) {
    public static final Identifier ID = JustifyLasers.id("emitter_setting");
    public LaserSettingsPacket(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    public void write(PacketByteBuf buf) {
        // Vanilla ButtonClickC2SPacket truncates button IDs to a signed byte in 1.20.1.
        buf.writeVarInt(syncId);
        buf.writeVarInt(buttonId);
    }

    public boolean apply(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator()
                && player.currentScreenHandler instanceof LaserEmitterScreenHandler handler
                && handler.syncId == syncId
                && handler.onButtonClick(player, buttonId);
    }
}
