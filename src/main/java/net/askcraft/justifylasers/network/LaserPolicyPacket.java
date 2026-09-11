package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record LaserPolicyPacket(boolean technicalMode) {
    public static final Identifier ID = JustifyLasers.id("energy_policy");

    public LaserPolicyPacket(PacketByteBuf buffer) {
        this(buffer.readBoolean());
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeBoolean(technicalMode);
    }
}
