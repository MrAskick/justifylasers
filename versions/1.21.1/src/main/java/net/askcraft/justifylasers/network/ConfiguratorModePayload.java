package net.askcraft.justifylasers.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record ConfiguratorModePayload(ConfiguratorModePacket packet) implements CustomPayload {
    public static final Id<ConfiguratorModePayload> ID = new Id<>(ConfiguratorModePacket.ID);
    public static final PacketCodec<RegistryByteBuf, ConfiguratorModePayload> CODEC = PacketCodec.of(
            (value, buffer) -> value.packet.write(buffer), buffer -> new ConfiguratorModePayload(new ConfiguratorModePacket(buffer)));
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
