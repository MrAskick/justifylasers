package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
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
        return traceRaw(emitter.getWorld(), emitter, tickDelta, new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>(), new BeamModuleBudget());
    }

    public static boolean isOpticalInput(World world, BlockState state, LaserBeamTrace trace) {
        return world.getBlockEntity(trace.hitBlock()) instanceof LaserLightSink sink && sink.acceptsLaser(trace.hitSide(), trace.end())
                || state.getBlock() instanceof LaserOpticBlock optic
                && (optic.kind() == LaserOpticBlock.Kind.MIRROR || world.getBlockEntity(trace.hitBlock()) instanceof LaserOpticBlockEntity entity
                    && entity.acceptsLaser(trace.hitSide(), trace.end()));
    }

    static LaserBeamPath trace(World world, LaserBeamSource emitter, float tickDelta,
                               Map<Integer, CubeRoute> owners, Map<Integer, LaserBeamNetwork.CubeInput> inputs,
                               Map<BlockPos, LaserBeamNetwork.OpticInput> opticInputs) {
        var budget = new BeamModuleBudget();
        var path = traceRaw(world, emitter, tickDelta, owners, inputs, opticInputs, budget);
        budget.solve();
        return budget.apply(path);
    }

    static LaserBeamPath traceRaw(World world, LaserBeamSource emitter, float tickDelta,
                               Map<Integer, CubeRoute> owners, Map<Integer, LaserBeamNetwork.CubeInput> inputs,
                               Map<BlockPos, LaserBeamNetwork.OpticInput> opticInputs, BeamModuleBudget budget) {
        return traceRaw(world, emitter, tickDelta, owners, inputs, opticInputs, budget, Set.of());
    }

    static LaserBeamPath traceRaw(World world, LaserBeamSource emitter, float tickDelta,
                                 Map<Integer, CubeRoute> owners, Map<Integer, LaserBeamNetwork.CubeInput> inputs,
                                 Map<BlockPos, LaserBeamNetwork.OpticInput> opticInputs, BeamModuleBudget budget, Set<CubeRoute> exhausted) {
        ArrayDeque<Branch> pending = new ArrayDeque<>();
        var initialPayments = emitter.moduleFluxCost() == 0 ? List.<BeamModuleBudget.Port>of()
                : budget.pass(emitter.beamPosition(), emitter.beamOrigin(), emitter.beamDirection(),
                        emitter.moduleFluxCost(), emitter.luminousFlux(), List.of());
        // Start just inside the source face, ignoring only its own body. Starting beyond the face
        // skips the optical input plane when a receiver or splitter is placed directly against it.
        pending.add(new Branch(emitter.beamOrigin(), emitter.beamDirection(), emitter.getBeamRange(), emitter.beamRgb(),
                1, 0, emitter.beamExitBlock(), -1, Set.of(), Set.of(), null, emitter.beamBehavior(), 0, initialPayments));
        List<LaserBeamTrace> segments = new ArrayList<>();
        while (!pending.isEmpty() && segments.size() < MAX_SEGMENTS) {
            Branch branch = pending.removeFirst();
            if (branch.remaining <= OpticalGeometry.EPSILON || branch.power <= 0) continue;
            LaserBeamTrace ray = LaserBeamTrace.traceFrom(world, branch.start, branch.axis, branch.remaining, branch.ignoredBlock)
                    .withOptics(branch.rgb, branch.power).combinedBy(branch.combinedBy).withBehavior(branch.behavior);
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
                double incidentPower = branch.power * BeamAttenuation.retention(distance, world.isClient);
                var incident = new LaserBeamTrace(ray.start(), nearestHit.position(), ray.direction(), null)
                        .withOptics(branch.rgb, incidentPower).combinedBy(branch.combinedBy).withBehavior(branch.behavior);
                segments.add(incident); budget.remember(incident, branch.payments);
                if (!nearestHit.acceptsInput() || branch.depth >= MAX_REFOCUSES || branch.cubes.contains(nearest.getId())) continue;
                // A cube still selects one incident ray. Contributions already coalesced by a
                // combiner share that ray, including when a splitter rejoins its own branches.
                var route = new CubeRoute(emitter.beamPosition(), branch.combinedBy, nearestHit.position(), branch.axis);
                if (exhausted.contains(route)) continue;
                CubeRoute owner = owners.putIfAbsent(nearest.getId(), route);
                if (owner != null && !route.sharesCombinedRay(owner)) continue;
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
                pending.addFirst(new Branch(exit, frame.forward(), remaining, branch.rgb, incidentPower * BeamAttenuation.retention(toCenter + toExit, world.isClient),
                        branch.depth + 1, null, nearest.getId(), Set.copyOf(visited), branch.optics, branch.combinedBy, branch.behavior, branch.addedRange, branch.payments));
                continue;
            }
            branch = branch.atPower(branch.power * BeamAttenuation.retention(ray.length(), world.isClient));
            ray = ray.withOptics(branch.rgb, branch.power);
            if (!ray.hasBlockHit()) {
                segments.add(ray); budget.remember(ray, branch.payments);
                continue;
            }
            BlockState state = world.getBlockState(ray.hitBlock());
            boolean crystal = state.getBlock() instanceof LaserPartBlock part && part.isCrystal();
            LaserPartBlockEntity module = world.getBlockEntity(ray.hitBlock()) instanceof LaserPartBlockEntity part && part.module() != null ? part : null;
            LaserOpticBlockEntity optic = world.getBlockEntity(ray.hitBlock()) instanceof LaserOpticBlockEntity entity ? entity : null;
            if (optic != null && (optic.kind() == LaserOpticBlock.Kind.MIRROR
                    ? optic.reflectingSurface(ray.end()) : optic.acceptsLaser(ray.hitSide(), ray.end()))) {
                var input = new LaserBeamNetwork.OpticInput(branch.rgb, emitter.isLightEmissionEnabled(), branch.power);
                opticInputs.merge(ray.hitBlock(), input, (previous, next) -> next.power() > previous.power() ? next : previous);
            }
            boolean redirect = optic != null && (optic.kind() == LaserOpticBlock.Kind.MIRROR && optic.reflectingSurface(ray.end())
                    || (optic.kind() == LaserOpticBlock.Kind.SPLITTER || optic.kind() == LaserOpticBlock.Kind.COMBINER)
                        && optic.acceptsLaser(ray.hitSide(), ray.end()));
            if (!crystal && module == null && !redirect) {
                segments.add(ray); budget.remember(ray, branch.payments);
                continue;
            }
            var incident = new LaserBeamTrace(ray.start(), ray.end(), ray.direction(), null)
                    .withOptics(branch.rgb, branch.power).combinedBy(branch.combinedBy).withBehavior(branch.behavior);
            segments.add(incident); budget.remember(incident, branch.payments);
            if (branch.depth >= MAX_REFOCUSES || branch.optics.contains(ray.hitBlock())) continue;
            Set<BlockPos> visited = new HashSet<>(branch.optics);
            visited.add(ray.hitBlock());
            Set<BlockPos> visitedOptics = Set.copyOf(visited);
            double remaining = branch.remaining - ray.length();
            if (module != null) {
                boolean applied = module.enabled();
                BeamBehavior behavior = module.apply(branch.behavior);
                double power = branch.power;
                int addedRange = branch.addedRange;
                var payments = branch.payments;
                if (applied) {
                    payments = budget.pass(ray.hitBlock(), ray.end(), branch.axis, module.operatingFlux(), emitter.luminousFlux() * power, payments);
                    if (module.module() == LaserModule.RANGE) {
                        int bonus = Math.min(module.rangeBonus(), Math.max(0, LaserEmitterBlockEntity.MAX_RANGE - emitter.getBeamRange() - addedRange));
                        remaining += bonus;
                        addedRange += bonus;
                    }
                }
                int rgb = applied && module.module() == LaserModule.SPECTRUM ? module.spectrum() : branch.rgb;
                pending.addFirst(branch.continueFrom(ray.end(), branch.axis, remaining, rgb, power, ray.hitBlock(), visitedOptics)
                        .modifiedBy(behavior, addedRange, payments));
            } else if (crystal) {
                LaserPartBlock part = (LaserPartBlock) state.getBlock();
                int color = part.crystalColor().rgb();
                if (state.get(LaserPartBlock.MIXING)) color = OpticalGeometry.mix(branch.rgb, color);
                pending.addFirst(branch.continueFrom(ray.end(), branch.axis, remaining, color, branch.power, ray.hitBlock(), visitedOptics)
                        .modifiedBy(branch.behavior.withSpectrum(false), branch.addedRange, branch.payments));
            } else if (optic.kind() == LaserOpticBlock.Kind.MIRROR) {
                Vec3d reflected = OpticalGeometry.reflect(branch.axis, optic.normal());
                pending.addFirst(branch.continueFrom(ray.end(), reflected, remaining, branch.rgb, branch.power, null, visitedOptics));
            } else {
                List<Direction> outputs = optic.outputPorts();
                if (outputs.isEmpty()) continue;
                Vec3d center = Vec3d.ofCenter(ray.hitBlock());
                // Start inside our own output face; an adjacent optic owns the other side
                // of that same plane. Advancing past it skips the next input lens.
                double exitDepth = LaserOpticBlockEntity.PORT_DEPTH - OpticalGeometry.EPSILON;
                double interior = ray.end().distanceTo(center) + exitDepth;
                for (Direction output : outputs) {
                    if (pending.size() + segments.size() >= MAX_SEGMENTS) break;
                    Vec3d axis = Vec3d.of(output.getVector());
                    Vec3d exit = center.add(axis.multiply(exitDepth));
                    boolean combining = optic.kind() == LaserOpticBlock.Kind.COMBINER;
                    double efficiency = combining ? optic.combiningEfficiency() : 1;
                    Branch next = new Branch(exit, axis, remaining - interior, branch.rgb,
                            branch.power * BeamAttenuation.retention(interior, world.isClient) * efficiency / outputs.size(), branch.depth + 1, ray.hitBlock(),
                            -1, branch.cubes, visitedOptics, branch.combinedBy, branch.behavior, branch.addedRange, branch.payments);
                    pending.addLast(combining ? next.combinedAt(ray.hitBlock()) : next);
                }
            }
        }
        if (segments.isEmpty()) segments.add(LaserBeamTrace.traceFrom(world, emitter.beamOrigin(), emitter.beamDirection(),
                emitter.getBeamRange(), emitter.beamExitBlock()).withOptics(emitter.beamRgb(), 1).withBehavior(emitter.beamBehavior()));
        return new LaserBeamPath(List.copyOf(segments));
    }

    static LaserBeamPath coalesce(List<LaserBeamTrace> segments) {
        if (segments.stream().anyMatch(ray -> ray.combinedBy() != null)) {
            var contributions = segments.stream().filter(ray -> ray.combinedBy() != null)
                    .map(ray -> new BeamContributions.Beam(ray, BlockPos.ORIGIN, 1, false, ray.power(), 0)).toList();
            segments.removeIf(ray -> ray.combinedBy() != null);
            // Rejoined branches of this source must regain their summed damage/mining fraction,
            // not merely overlap visually and hit the once-per-source target guard twice.
            for (var beam : BeamContributions.merge(contributions, true)) segments.add(beam.trace().withOptics(beam.trace().rgb(), Math.min(1, beam.flux())).withWorkPower(Math.min(1, beam.trace().workPower())));
        }
        return new LaserBeamPath(List.copyOf(segments));
    }

    private record Branch(Vec3d start, Vec3d axis, double remaining, int rgb, double power, int depth,
                          BlockPos ignoredBlock, int previousCube, Set<Integer> cubes, Set<BlockPos> optics, BlockPos combinedBy, BeamBehavior behavior, int addedRange,
                          List<BeamModuleBudget.Port> payments) {
        Branch atPower(double value) {
            return new Branch(start, axis, remaining, rgb, value, depth, ignoredBlock, previousCube, cubes, optics, combinedBy, behavior, addedRange, payments);
        }
        Branch continueFrom(Vec3d point, Vec3d direction, double range, int color, double fraction,
                            BlockPos ignored, Set<BlockPos> visited) {
            return new Branch(point.add(direction.multiply(OpticalGeometry.EPSILON)), direction,
                    range - OpticalGeometry.EPSILON, color, fraction, depth + 1, ignored, -1, cubes, visited, combinedBy, behavior, addedRange, payments);
        }
        Branch combinedAt(BlockPos pos) {
            return new Branch(start, axis, remaining, rgb, power, depth, ignoredBlock, previousCube, cubes, optics, pos, behavior, addedRange, payments);
        }
        Branch modifiedBy(BeamBehavior value, int rangeBonus, List<BeamModuleBudget.Port> route) {
            return new Branch(start, axis, remaining, rgb, power, depth, ignoredBlock, previousCube, cubes, optics, combinedBy, value, rangeBonus, route);
        }
    }

    record CubeRoute(BlockPos source, BlockPos combiner, Vec3d entry, Vec3d axis) {
        boolean sharesCombinedRay(CubeRoute other) {
            return combiner != null && combiner.equals(other.combiner)
                    && entry.squaredDistanceTo(other.entry) < 1e-10 && axis.dotProduct(other.axis) > .999999;
        }
    }
}
