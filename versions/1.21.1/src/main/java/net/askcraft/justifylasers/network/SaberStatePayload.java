package net.askcraft.justifylasers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record SaberStatePayload(SaberStatePacket packet) implements CustomPayload {
    public static final Id<SaberStatePayload> ID = new Id<>(SaberStatePacket.ID);
    public static final PacketCodec<RegistryByteBuf, SaberStatePayload> CODEC =
            PacketCodec.of((value, buffer) -> value.packet.write(buffer), buffer -> new SaberStatePayload(new SaberStatePacket(buffer)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
