package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SettingsPayload(LaserSettingsPacket packet) implements CustomPayload {
    public static final Id<SettingsPayload> ID = new Id<>(JustifyLasers.id("emitter_setting"));
    public static final PacketCodec<RegistryByteBuf, SettingsPayload> CODEC =
            PacketCodec.of((value, buffer) -> value.packet.write(buffer), buffer -> new SettingsPayload(new LaserSettingsPacket(buffer)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
