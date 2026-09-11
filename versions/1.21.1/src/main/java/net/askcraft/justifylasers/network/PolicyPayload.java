package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public record PolicyPayload(LaserPolicyPacket packet) implements CustomPayload {
    public static final Id<PolicyPayload> ID = new Id<>(JustifyLasers.id("energy_policy"));
    public static final PacketCodec<RegistryByteBuf, PolicyPayload> CODEC =
            PacketCodec.of((value, buffer) -> value.packet.write(buffer), buffer -> new PolicyPayload(new LaserPolicyPacket(buffer)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
