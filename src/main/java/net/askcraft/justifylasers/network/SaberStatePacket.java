package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.laser.SaberCut;
import net.askcraft.justifylasers.laser.SaberState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public record SaberStatePacket(int entity, long time, SaberState state, SaberCombat.Contact contact, Vec3d point, int rgb, Hand hand) {
    public SaberStatePacket(int entity, long time, SaberState state, SaberCombat.Contact contact, Vec3d point, int rgb) {
        this(entity, time, state, contact, point, rgb, Hand.MAIN_HAND);
    }
    public static final Identifier ID = JustifyLasers.id("saber_state");
    public static java.util.function.Consumer<SaberStatePacket> receiver = packet -> { };
    public void deliver() { receiver.accept(this); }
    public SaberStatePacket(PacketByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readLong(), new SaberState(buffer.readEnumConstant(SaberState.Action.class), buffer.readEnumConstant(SaberCut.class),
                buffer.readVarInt(), buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readBoolean()), buffer.readEnumConstant(SaberCombat.Contact.class), new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readInt(), buffer.readEnumConstant(Hand.class));
    }
    public void write(PacketByteBuf buffer) {
        buffer.writeVarInt(entity); buffer.writeLong(time);
        buffer.writeEnumConstant(state.action()); buffer.writeEnumConstant(state.cut()); buffer.writeVarInt(state.stage()); buffer.writeLong(state.started());
        buffer.writeVarInt(state.windup()); buffer.writeVarInt(state.active()); buffer.writeVarInt(state.recovery()); buffer.writeFloat(state.stamina());
        buffer.writeFloat(state.capacity()); buffer.writeVarInt(state.sequence()); buffer.writeBoolean(state.staff());
        buffer.writeEnumConstant(contact); buffer.writeDouble(point.x); buffer.writeDouble(point.y); buffer.writeDouble(point.z); buffer.writeInt(rgb);
        buffer.writeEnumConstant(hand);
    }
}
