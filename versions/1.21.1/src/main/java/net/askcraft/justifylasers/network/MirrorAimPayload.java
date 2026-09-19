package net.askcraft.justifylasers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record MirrorAimPayload(MirrorAimPacket packet) implements CustomPayload {
    public static final Id<MirrorAimPayload> ID = new Id<>(MirrorAimPacket.ID);
    public static final PacketCodec<RegistryByteBuf, MirrorAimPayload> CODEC = PacketCodec.of(
            (value, buffer) -> value.packet.write(buffer), buffer -> new MirrorAimPayload(new MirrorAimPacket(buffer)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
