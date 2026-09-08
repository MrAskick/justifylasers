package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record LaserBeamPath(List<LaserBeamTrace> segments) {
    public static final int MAX_REFOCUSES = 16;

    public LaserBeamTrace last() {
        return segments.get(segments.size() - 1);
    }

    static LaserBeamPath trace(World world, LaserEmitterBlockEntity emitter, float tickDelta,
                               Map<Integer, BlockPos> owners, Map<Integer, LaserBeamNetwork.CubeInput> inputs) {
        List<LaserBeamTrace> segments = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        double remaining = emitter.getBeamRange();
        LaserBeamTrace trace = LaserBeamTrace.trace(world, emitter.getPos(), emitter.getCachedState(), remaining);
        int previousCube = -1;
        while (true) {
            RefocusingCubeEntity nearest = null;
            CubeOptics.Hit nearestHit = null;
            double nearestDistance = trace.length();
            for (RefocusingCubeEntity cube : world.getEntitiesByClass(RefocusingCubeEntity.class,
                    new Box(trace.start(), trace.end()).expand(1.0D), entity -> !entity.isRemoved())) {
                if (cube.getId() == previousCube) {
                    continue;
                }
                CubeOptics.Hit hit = cube.opticalFrame(tickDelta).intersect(trace.start(), trace.end());
                if (hit == null) {
                    continue;
                }
                double distance = trace.start().distanceTo(hit.position());
                if (distance < nearestDistance - 1.0E-6D
                        || nearest != null && Math.abs(distance - nearestDistance) < 1.0E-6D && cube.getId() < nearest.getId()) {
                    nearest = cube;
                    nearestHit = hit;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) {
                segments.add(trace);
                break;
            }
            segments.add(new LaserBeamTrace(trace.start(), nearestHit.position(), trace.direction(), null));
            remaining -= nearestDistance;
            if (!nearestHit.acceptsInput() || !visited.add(nearest.getId()) || visited.size() > MAX_REFOCUSES) {
                break;
            }
            // Stable source ordering assigns one output when several emitters share a cube.
            BlockPos owner = owners.putIfAbsent(nearest.getId(), emitter.getPos());
            if (owner != null && !owner.equals(emitter.getPos())) {
                break;
            }
            inputs.put(nearest.getId(), new LaserBeamNetwork.CubeInput(emitter.getColor(), emitter.isLightEmissionEnabled()));
            CubeOptics.Frame frame = nearest.opticalFrame(tickDelta);
            Vec3d exit = frame.output();
            remaining -= nearestHit.position().distanceTo(frame.center()) + frame.center().distanceTo(exit);
            if (remaining <= 0.002D) {
                break;
            }
            // Check the interior too: a cube partly embedded in a wall cannot tunnel light through it.
            LaserBeamTrace inside = LaserBeamTrace.traceFrom(world, nearestHit.position(),
                    frame.center().subtract(nearestHit.position()), nearestHit.position().distanceTo(frame.center()));
            LaserBeamTrace outlet = LaserBeamTrace.traceFrom(world, frame.center(), frame.forward(), frame.center().distanceTo(exit));
            if (inside.hasBlockHit() || outlet.hasBlockHit()) {
                break;
            }
            previousCube = nearest.getId();
            trace = LaserBeamTrace.traceFrom(world, exit, frame.forward(), remaining);
        }
        return new LaserBeamPath(List.copyOf(segments));
    }
}
