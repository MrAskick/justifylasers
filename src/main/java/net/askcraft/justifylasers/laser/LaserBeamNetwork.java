package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.entity.RefocusingCubeEntity;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class LaserBeamNetwork {
    // Values contain positions and IDs, never world/entity references, so unloaded worlds can be collected.
    private static final Map<World, State> WORLDS = new WeakHashMap<>();

    public static void initialize() {
        Platform.onEndWorldTick(world -> {
            State state = state(world);
            Snapshot snapshot = snapshot(world, 1.0F);
            if (state.energyTick != world.getTime()) {
                state.energyTick = world.getTime();
                snapshot.paths.forEach((source, path) -> {
                    if (!(world.getBlockEntity(source) instanceof LaserEmitterBlockEntity emitter)) return;
                    int budget = emitter.transferBudget();
                    int remaining = budget;
                    for (LaserBeamTrace ray : path.segments()) {
                        if (remaining <= 0 || !ray.hasBlockHit()) continue;
                        if (world.getBlockEntity(ray.hitBlock()) instanceof LaserOpticBlockEntity receiver
                                && receiver.kind() == LaserOpticBlock.Kind.ENERGY_RECEIVER && receiver.acceptsLaser(ray.hitSide(), ray.end())) {
                            int share = (int) Math.min(remaining, Math.floor(budget * ray.power()));
                            remaining -= share;
                            receiver.receiveBeam((int) Math.floor(share * LaserConfig.get().energyTransmissionEfficiency), ray.rgb());
                        }
                    }
                });
            }
            // Resolving a pending block entity or changing its lit state can register
            // or remove optics through world callbacks during this same tick.
            for (BlockPos pos : List.copyOf(state.optics)) {
                if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof LaserOpticBlockEntity optic)
                    optic.setBeamInput(snapshot.optics.get(pos));
            }
            for (int id : List.copyOf(state.cubes)) {
                if (world.getEntityById(id) instanceof RefocusingCubeEntity cube) cube.setBeamInput(snapshot.cubes.get(id));
                else state.cubes.remove(id);
            }
        });
    }

    private static synchronized State state(World world) {
        return WORLDS.computeIfAbsent(world, ignored -> new State());
    }

    public static void registerEmitter(World world, BlockPos pos) {
        State state = state(world);
        if (state.emitters.add(pos.toImmutable())) {
            state.snapshot = null;
        }
    }

    public static void removeEmitter(World world, BlockPos pos) {
        State state = state(world);
        state.emitters.remove(pos);
        state.snapshot = null;
    }

    public static void registerOptic(World world, BlockPos pos) {
        if (state(world).optics.add(pos.toImmutable())) invalidate(world);
    }

    public static void removeOptic(World world, BlockPos pos) {
        state(world).optics.remove(pos);
        invalidate(world);
    }

    public static void registerCube(World world, int id) {
        if (!world.isClient) {
            state(world).cubes.add(id);
        }
        invalidate(world);
    }

    public static void removeCube(World world, int id) {
        state(world).cubes.remove(id);
        invalidate(world);
    }

    public static void beginEmitterTick(World world, BlockPos pos) {
        State state = state(world);
        if (state.serverTickTime != world.getTime()) {
            state.serverTickTime = world.getTime();
            state.tickedEmitters.clear();
        }
        // Repeated ticks from an accelerated ticker must refresh the route as well.
        if (!state.tickedEmitters.add(pos)) {
            state.snapshot = null;
        }
    }

    public static void invalidate(@Nullable World world) {
        if (world != null) {
            state(world).snapshot = null;
        }
    }

    public static LaserBeamPath path(LaserEmitterBlockEntity emitter, float tickDelta) {
        World world = emitter.getWorld();
        registerEmitter(world, emitter.getPos());
        LaserBeamPath result = snapshot(world, tickDelta).paths.get(emitter.getPos());
        return result != null ? result : new LaserBeamPath(List.of(
                LaserBeamTrace.trace(world, emitter.getPos(), emitter.getCachedState(), emitter.getBeamRange())));
    }

    public static Map<BlockPos, LaserBeamPath> paths(World world, float tickDelta) {
        return Collections.unmodifiableMap(snapshot(world, tickDelta).paths);
    }

    @Nullable
    public static LaserColor receivedColor(World world, BlockPos pos, Direction side) {
        for (Map.Entry<BlockPos, LaserBeamPath> entry : snapshot(world, 1.0F).paths.entrySet()) {
            for (LaserBeamTrace trace : entry.getValue().segments()) {
                if (pos.equals(trace.hitBlock()) && side == trace.hitSide()) {
                    return LaserColor.nearest(trace.rgb());
                }
            }
        }
        return null;
    }

    private static Snapshot snapshot(World world, float tickDelta) {
        State state = state(world);
        if (state.snapshot != null && state.time == world.getTime() && state.tickDelta == tickDelta) {
            return state.snapshot;
        }
        Map<BlockPos, LaserBeamPath> paths = new LinkedHashMap<>();
        Map<Integer, BlockPos> owners = new HashMap<>();
        Map<Integer, CubeInput> inputs = new HashMap<>();
        Map<BlockPos, OpticInput> optics = new HashMap<>();
        for (BlockPos pos : state.emitters.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter
                    && emitter.isBeamActive()) {
                paths.put(pos, LaserBeamPath.trace(world, emitter, tickDelta, owners, inputs, optics));
            }
        }
        state.time = world.getTime();
        state.tickDelta = tickDelta;
        state.snapshot = new Snapshot(paths, inputs, optics);
        return state.snapshot;
    }

    public record CubeInput(LaserColor color, boolean emission, int rgb) {
        public CubeInput(LaserColor color, boolean emission) { this(color, emission, color.rgb()); }
    }

    public record OpticInput(int rgb, boolean emission, double power) { }

    private record Snapshot(Map<BlockPos, LaserBeamPath> paths, Map<Integer, CubeInput> cubes, Map<BlockPos, OpticInput> optics) {
    }

    private static final class State {
        private final Set<BlockPos> emitters = new HashSet<>();
        private final Set<BlockPos> optics = new HashSet<>();
        private final Set<Integer> cubes = new HashSet<>();
        private final Set<BlockPos> tickedEmitters = new HashSet<>();
        private long serverTickTime = Long.MIN_VALUE;
        private long energyTick = Long.MIN_VALUE;
        private long time;
        private float tickDelta;
        private Snapshot snapshot;
    }

    private LaserBeamNetwork() {
    }
}
