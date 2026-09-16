package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A paid or generated optical budget. Routing never creates additional source energy. */
public interface LaserBeamSource {
    World beamWorld();
    BlockPos beamPosition();
    default BlockPos beamExitBlock() { return beamPosition(); }
    Vec3d beamOrigin();
    Vec3d beamDirection();
    int beamRgb();
    int getBeamRange();
    float getBeamWidthScale();
    long getTicks();
    boolean isBeamActive();
    boolean isLightEmissionEnabled();
    boolean showsScorchMarks();
    /** Current visible flux; only opticalBudget may be consumed by the network. */
    long luminousFlux();
    long opticalBudget();
}
