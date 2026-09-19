package net.askcraft.justifylasers.bridge;

import io.netty.buffer.Unpooled;
import net.askcraft.justifylasers.network.LightBridgePacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LightBridgePacketTest {
    @Test void snapshotPreservesFractionalEndpointsNegativeCoordinatesAndLargeFlux() {
        var buffer=new PacketByteBuf(Unpooled.buffer());
        try {
            var packet=new LightBridgePacket(new Identifier("minecraft","overworld"), List.of(
                    new LightBridgeSpan(new BlockPos(-40,271,-1000),Direction.WEST,3,127.625,0x12CDFF,900_000_000_000_000L),
                    new LightBridgeSpan(BlockPos.ORIGIN,Direction.UP,Direction.DOWN,true,3,7.125,0x65717A,12000),
                    new LightBridgeSpan(BlockPos.ORIGIN,Direction.UP,Direction.NORTH,false,5,0,3,1,256.25,0xADFF22,12000),
                    new LightBridgeSpan(BlockPos.ORIGIN,Direction.UP,Direction.NORTH,false,5,1,3,1,256.25,0xADFF22,12000)));
            packet.write(buffer);
            assertEquals(packet,new LightBridgePacket(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void anEmptySnapshotCanRemoveEveryClientField() {
        var buffer=new PacketByteBuf(Unpooled.buffer());
        try {
            var packet=new LightBridgePacket(new Identifier("minecraft","the_nether"),List.of());
            packet.write(buffer);
            assertEquals(packet,new LightBridgePacket(buffer));
        } finally { buffer.release(); }
    }
}
