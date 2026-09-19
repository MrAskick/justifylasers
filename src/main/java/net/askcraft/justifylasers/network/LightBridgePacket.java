package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record LightBridgePacket(Identifier dimension, List<LightBridgeSpan> fields, List<net.minecraft.util.math.Box> landings) {
    public static final Identifier ID = JustifyLasers.id("light_bridges_v2");
    public static Consumer<LightBridgePacket> receiver = packet -> { };
    public LightBridgePacket { fields = List.copyOf(fields); landings = List.copyOf(landings); }
    public LightBridgePacket(Identifier dimension, List<LightBridgeSpan> fields) { this(dimension, fields, List.of()); }
    public void deliver() { receiver.accept(this); }
    public LightBridgePacket(PacketByteBuf buffer) { this(buffer.readIdentifier(), readFields(buffer), readLandings(buffer)); }

    private static List<net.minecraft.util.math.Box> readLandings(PacketByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 4096) throw new IllegalArgumentException("Too many bridge landings");
        var result = new ArrayList<net.minecraft.util.math.Box>(count);
        for (int i = 0; i < count; i++) result.add(new net.minecraft.util.math.Box(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        return result;
    }

    private static List<LightBridgeSpan> readFields(PacketByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 16_384) throw new IllegalArgumentException("Too many light bridges");
        var fields = new ArrayList<LightBridgeSpan>(count);
        for (int i = 0; i < count; i++) fields.add(new LightBridgeSpan(buffer.readBlockPos(), buffer.readEnumConstant(Direction.class),
                buffer.readEnumConstant(Direction.class), buffer.readBoolean(), buffer.readUnsignedByte(), buffer.readByte(), buffer.readUnsignedByte(),
                buffer.readUnsignedByte(), buffer.readDouble(), buffer.readInt(), buffer.readVarLong()));
        return fields;
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeIdentifier(dimension);
        buffer.writeVarInt(fields.size());
        for (var span : fields) {
            buffer.writeBlockPos(span.origin()); buffer.writeEnumConstant(span.facing()); buffer.writeEnumConstant(span.mount()); buffer.writeBoolean(span.rolled());
            buffer.writeByte(span.rotation()); buffer.writeByte(span.section()); buffer.writeByte(span.connections()); buffer.writeByte(span.width());
            buffer.writeDouble(span.length()); buffer.writeInt(span.rgb()); buffer.writeVarLong(span.lumens());
        }
        buffer.writeVarInt(landings.size());
        for (var box : landings) {
            buffer.writeDouble(box.minX); buffer.writeDouble(box.minY); buffer.writeDouble(box.minZ);
            buffer.writeDouble(box.maxX); buffer.writeDouble(box.maxY); buffer.writeDouble(box.maxZ);
        }
    }
}
