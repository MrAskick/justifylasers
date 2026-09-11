package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
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
            state.cubes.removeIf(id -> !(world.getEntityById(id) instanceof RefocusingCubeEntity));
            for (int id : state.cubes) {
                RefocusingCubeEntity cube = (RefocusingCubeEntity) world.getEntityById(id);
                CubeInput input = snapshot.cubes.get(id);
                cube.setBeamInput(input);
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
            LaserBeamTrace trace = entry.getValue().last();
            if (pos.equals(trace.hitBlock()) && side == trace.hitSide()
                    && world.getBlockEntity(entry.getKey()) instanceof LaserEmitterBlockEntity emitter) {
                return emitter.getColor();
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
        for (BlockPos pos : state.emitters.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter
                    && emitter.isBeamActive()) {
                paths.put(pos, LaserBeamPath.trace(world, emitter, tickDelta, owners, inputs));
            }
        }
        state.time = world.getTime();
        state.tickDelta = tickDelta;
        state.snapshot = new Snapshot(paths, inputs);
        return state.snapshot;
    }

    public record CubeInput(LaserColor color, boolean emission) {
    }

    private record Snapshot(Map<BlockPos, LaserBeamPath> paths, Map<Integer, CubeInput> cubes) {
    }

    private static final class State {
        private final Set<BlockPos> emitters = new HashSet<>();
        private final Set<Integer> cubes = new HashSet<>();
        private final Set<BlockPos> tickedEmitters = new HashSet<>();
        private long serverTickTime = Long.MIN_VALUE;
        private long time;
        private float tickDelta;
        private Snapshot snapshot;
    }

    private LaserBeamNetwork() {
    }
}
