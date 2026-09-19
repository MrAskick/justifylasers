package net.askcraft.justifylasers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record LightBridgePayload(LightBridgePacket packet) implements CustomPayload {
    public static final Id<LightBridgePayload> ID = new Id<>(LightBridgePacket.ID);
    public static final PacketCodec<RegistryByteBuf, LightBridgePayload> CODEC =
            PacketCodec.of((value, buffer) -> value.packet.write(buffer), buffer -> new LightBridgePayload(new LightBridgePacket(buffer)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
