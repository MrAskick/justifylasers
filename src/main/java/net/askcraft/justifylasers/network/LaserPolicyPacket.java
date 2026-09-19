package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.config.LaserConfig;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record LaserPolicyPacket(boolean technicalMode, boolean beamAttenuation, double beamLossPerBlock) {
    public static final Identifier ID = JustifyLasers.id("energy_policy");

    public LaserPolicyPacket(boolean technicalMode) {
        this(technicalMode, LaserConfig.get().beamAttenuation, LaserConfig.get().beamLossPerBlock);
    }

    public LaserPolicyPacket(PacketByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readBoolean(), buffer.readDouble());
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeBoolean(technicalMode);
        buffer.writeBoolean(beamAttenuation);
        buffer.writeDouble(beamLossPerBlock);
    }

    public void apply() {
        LaserConfig.applyServerMode(technicalMode);
        LaserConfig.applyServerAttenuation(beamAttenuation, beamLossPerBlock);
    }
}
