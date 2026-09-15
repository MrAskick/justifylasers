package net.askcraft.justifylasers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SaberTogglePayload(SaberTogglePacket packet) implements CustomPayload {
    public static final Id<SaberTogglePayload> ID = new Id<>(SaberTogglePacket.ID);
    public static final PacketCodec<RegistryByteBuf, SaberTogglePayload> CODEC =
            PacketCodec.of((value, buffer) -> value.packet.write(buffer), buffer -> new SaberTogglePayload(new SaberTogglePacket(buffer)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
