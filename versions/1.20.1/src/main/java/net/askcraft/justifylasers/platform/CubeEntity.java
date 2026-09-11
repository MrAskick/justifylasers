package net.askcraft.justifylasers.platform;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public abstract class CubeEntity extends Entity {
    protected CubeEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    protected abstract void defineTrackedData(TrackedValues values);
    protected abstract void interpolate(double x, double y, double z, float yaw, float pitch, int steps);

    public interface TrackedValues {
        <T> void add(TrackedData<T> key, T value);
    }

    @Override
    protected void initDataTracker() {
        defineTrackedData(dataTracker::startTracking);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        interpolate(x, y, z, yaw, pitch, steps);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return Platform.cubeSpawnPacket(this);
    }

}
