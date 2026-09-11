package net.askcraft.justifylasers.platform;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
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
    protected void initDataTracker(DataTracker.Builder builder) {
        defineTrackedData(builder::add);
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
        interpolate(x, y, z, yaw, pitch, steps);
    }

}
