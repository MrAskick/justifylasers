package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** A terminal optical consumer. The network assigns its share once per server tick. */
public interface LaserLightSink {
    boolean acceptsLaser(Direction side, Vec3d point);

    /** Unused flux is lost; a consumer must not also forward this allocation to another sink. */
    void receiveLight(long lumens, int rgb);

    default void receiveLight(long lumens, int rgb, long spectralLumens, int spectrum) { receiveLight(lumens, rgb); }
}
