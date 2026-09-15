package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record LaserBeamPath(List<LaserBeamTrace> segments) {
    public static final int MAX_REFOCUSES = 16;
    public static final int MAX_SEGMENTS = 128;

    public LaserBeamTrace last() {
        return segments.get(segments.size() - 1);
    }

    public static LaserBeamPath preview(LaserEmitterBlockEntity emitter, float tickDelta) {
        return trace(emitter.getWorld(), emitter, tickDelta, new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>());
    }

    public static boolean isOpticalInput(World world, BlockState state, LaserBeamTrace trace) {
        return state.getBlock() instanceof LaserOpticBlock optic
                && (optic.kind() == LaserOpticBlock.Kind.MIRROR || world.getBlockEntity(trace.hitBlock()) instanceof LaserOpticBlockEntity entity
                    && entity.acceptsLaser(trace.hitSide(), trace.end()));
    }

    static LaserBeamPath trace(World world, LaserEmitterBlockEntity emitter, float tickDelta,
                               Map<Integer, BlockPos> owners, Map<Integer, LaserBeamNetwork.CubeInput> inputs,
                               Map<BlockPos, LaserBeamNetwork.OpticInput> opticInputs) {
        LaserBeamTrace first = LaserBeamTrace.trace(world, emitter.getPos(), emitter.getCachedState(), emitter.getBeamRange());
        ArrayDeque<Branch> pending = new ArrayDeque<>();
        pending.add(new Branch(first.start(), first.axis(), emitter.getBeamRange(), emitter.getColor().rgb(),
                1, 0, null, -1, Set.of(), Set.of()));
        List<LaserBeamTrace> segments = new ArrayList<>();
        while (!pending.isEmpty() && segments.size() < MAX_SEGMENTS) {
            Branch branch = pending.removeFirst();
            if (branch.remaining <= OpticalGeometry.EPSILON || branch.power <= 0) continue;
            LaserBeamTrace ray = LaserBeamTrace.traceFrom(world, branch.start, branch.axis, branch.remaining, branch.ignoredBlock)
                    .withOptics(branch.rgb, branch.power);
            RefocusingCubeEntity nearest = null;
            CubeOptics.Hit nearestHit = null;
            double distance = ray.length();
            for (RefocusingCubeEntity cube : world.getEntitiesByClass(RefocusingCubeEntity.class,
                    new Box(ray.start(), ray.end()).expand(1), entity -> !entity.isRemoved())) {
                if (cube.getId() == branch.previousCube) continue;
                CubeOptics.Hit hit = cube.opticalFrame(tickDelta).intersect(ray.start(), ray.end());
                if (hit == null) continue;
                double current = ray.start().distanceTo(hit.position());
                if (current < distance - 1.0E-6 || nearest != null && Math.abs(current - distance) < 1.0E-6 && cube.getId() < nearest.getId()) {
                    nearest = cube;
                    nearestHit = hit;
                    distance = current;
                }
            }
            if (nearest != null) {
                segments.add(new LaserBeamTrace(ray.start(), nearestHit.position(), ray.direction(), null).withOptics(branch.rgb, branch.power));
                if (!nearestHit.acceptsInput() || branch.depth >= MAX_REFOCUSES || branch.cubes.contains(nearest.getId())) continue;
                // One output per cube, even when split branches or several sources converge.
                BlockPos owner = owners.putIfAbsent(nearest.getId(), emitter.getPos());
                if (owner != null || inputs.containsKey(nearest.getId())) continue;
                CubeOptics.Frame frame = nearest.opticalFrame(tickDelta);
                Vec3d exit = frame.output();
                double toCenter = nearestHit.position().distanceTo(frame.center());
                double toExit = frame.center().distanceTo(exit);
                double remaining = branch.remaining - distance - toCenter - toExit;
                if (remaining <= OpticalGeometry.EPSILON) continue;
                if (LaserBeamTrace.traceFrom(world, nearestHit.position(), frame.center().subtract(nearestHit.position()), toCenter).hasBlockHit()
                        || LaserBeamTrace.traceFrom(world, frame.center(), frame.forward(), toExit).hasBlockHit()) continue;
                inputs.put(nearest.getId(), new LaserBeamNetwork.CubeInput(LaserColor.nearest(branch.rgb), emitter.isLightEmissionEnabled(), branch.rgb));
                Set<Integer> visited = new HashSet<>(branch.cubes);
                visited.add(nearest.getId());
                pending.addFirst(new Branch(exit, frame.forward(), remaining, branch.rgb, branch.power,
                        branch.depth + 1, null, nearest.getId(), Set.copyOf(visited), branch.optics));
                continue;
            }
            if (!ray.hasBlockHit()) {
                segments.add(ray);
                continue;
            }
            BlockState state = world.getBlockState(ray.hitBlock());
            boolean crystal = state.getBlock() instanceof LaserPartBlock part && part.isCrystal();
            LaserOpticBlockEntity optic = world.getBlockEntity(ray.hitBlock()) instanceof LaserOpticBlockEntity entity ? entity : null;
            if (optic != null && (optic.kind() == LaserOpticBlock.Kind.MIRROR
                    ? optic.reflectingSurface(ray.end()) : optic.acceptsLaser(ray.hitSide(), ray.end()))) {
                var input = new LaserBeamNetwork.OpticInput(branch.rgb, emitter.isLightEmissionEnabled(), branch.power);
                opticInputs.merge(ray.hitBlock(), input, (previous, next) -> next.power() > previous.power() ? next : previous);
            }
            boolean redirect = optic != null && (optic.kind() == LaserOpticBlock.Kind.MIRROR && optic.reflectingSurface(ray.end())
                    || optic.kind() == LaserOpticBlock.Kind.SPLITTER && optic.acceptsLaser(ray.hitSide(), ray.end()));
            if (!crystal && !redirect) {
                segments.add(ray);
                continue;
            }
            segments.add(new LaserBeamTrace(ray.start(), ray.end(), ray.direction(), null).withOptics(branch.rgb, branch.power));
            if (branch.depth >= MAX_REFOCUSES || branch.optics.contains(ray.hitBlock())) continue;
            Set<BlockPos> visited = new HashSet<>(branch.optics);
            visited.add(ray.hitBlock());
            Set<BlockPos> visitedOptics = Set.copyOf(visited);
            double remaining = branch.remaining - ray.length();
            if (crystal) {
                LaserPartBlock part = (LaserPartBlock) state.getBlock();
                int color = part.crystalColor().rgb();
                if (state.get(LaserPartBlock.MIXING)) color = OpticalGeometry.mix(branch.rgb, color);
                pending.addFirst(branch.continueFrom(ray.end(), branch.axis, remaining, color, branch.power, ray.hitBlock(), visitedOptics));
            } else if (optic.kind() == LaserOpticBlock.Kind.MIRROR) {
                Vec3d reflected = OpticalGeometry.reflect(branch.axis, optic.normal());
                pending.addFirst(branch.continueFrom(ray.end(), reflected, remaining, branch.rgb, branch.power, null, visitedOptics));
            } else {
                List<Direction> outputs = optic.outputPorts();
                if (outputs.isEmpty()) continue;
                Vec3d center = Vec3d.ofCenter(ray.hitBlock());
                double interior = ray.end().distanceTo(center) + LaserOpticBlockEntity.PORT_DEPTH;
                for (Direction output : outputs) {
                    if (pending.size() + segments.size() >= MAX_SEGMENTS) break;
                    Vec3d axis = Vec3d.of(output.getVector());
                    Vec3d exit = center.add(axis.multiply(LaserOpticBlockEntity.PORT_DEPTH));
                    pending.addLast(branch.continueFrom(exit, axis, remaining - interior, branch.rgb,
                            branch.power / outputs.size(), ray.hitBlock(), visitedOptics));
                }
            }
        }
        if (segments.isEmpty()) segments.add(first.withOptics(emitter.getColor().rgb(), 1));
        return new LaserBeamPath(List.copyOf(segments));
    }

    private record Branch(Vec3d start, Vec3d axis, double remaining, int rgb, double power, int depth,
                          BlockPos ignoredBlock, int previousCube, Set<Integer> cubes, Set<BlockPos> optics) {
        Branch continueFrom(Vec3d point, Vec3d direction, double range, int color, double fraction,
                            BlockPos ignored, Set<BlockPos> visited) {
            return new Branch(point.add(direction.multiply(OpticalGeometry.EPSILON)), direction,
                    range - OpticalGeometry.EPSILON, color, fraction, depth + 1, ignored, -1, cubes, visited);
        }
    }
}
