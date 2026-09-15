package net.askcraft.justifylasers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record GunControlPayload(LaserGunControlPacket packet) implements CustomPayload {
    public static final Id<GunControlPayload> ID = new Id<>(LaserGunControlPacket.ID);
    public static final PacketCodec<RegistryByteBuf, GunControlPayload> CODEC =
            PacketCodec.of((value, buffer) -> value.packet.write(buffer), buffer -> new GunControlPayload(new LaserGunControlPacket(buffer)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
