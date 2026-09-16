package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
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
                Map<BlockPos, LightDelivery> deliveries = new LinkedHashMap<>();
                snapshot.paths.forEach((source, path) -> {
                    if (!(world.getBlockEntity(source) instanceof LaserBeamSource emitter)) return;
                    long budget = LuminousFlux.clamp(emitter.opticalBudget());
                    long remaining = budget;
                    for (LaserBeamTrace ray : path.segments()) {
                        if (remaining <= 0 || !ray.hasBlockHit()) continue;
                        if (world.getBlockEntity(ray.hitBlock()) instanceof LaserLightSink receiver
                                && receiver.acceptsLaser(ray.hitSide(), ray.end())) {
                            long share = Math.min(remaining, LuminousFlux.share(budget, ray.power()));
                            remaining -= share;
                            if (share > 0) deliveries.computeIfAbsent(ray.hitBlock(), ignored -> new LightDelivery()).add(share, ray.rgb());
                        }
                    }
                });
                // Convert the combined flux only once at each terminal sink; small contributions
                // must not disappear through per-source FE rounding. No optic stores/replays flux.
                deliveries.forEach((pos, delivery) -> {
                    if (world.getBlockEntity(pos) instanceof LaserLightSink sink)
                        sink.receiveLight(delivery.lumens, delivery.color.rgb());
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

    public static LaserBeamPath path(LaserBeamSource emitter, float tickDelta) {
        World world = emitter.beamWorld();
        registerEmitter(world, emitter.beamPosition());
        LaserBeamPath result = snapshot(world, tickDelta).paths.get(emitter.beamPosition());
        return result != null ? result : new LaserBeamPath(List.of(
                LaserBeamTrace.traceFrom(world, emitter.beamOrigin(), emitter.beamDirection(), emitter.getBeamRange(), emitter.beamPosition())
                        .withOptics(emitter.beamRgb(), 1)));
    }

    public static Map<BlockPos, LaserBeamPath> paths(World world, float tickDelta) {
        return Collections.unmodifiableMap(snapshot(world, tickDelta).paths);
    }

    @Nullable
    public static LaserColor receivedColor(World world, BlockPos pos, Direction side) {
        LaserColor first = null;
        boolean combined = false;
        var color = new LightMixture();
        for (Map.Entry<BlockPos, LaserBeamPath> entry : snapshot(world, 1.0F).paths.entrySet()) {
            for (LaserBeamTrace trace : entry.getValue().segments()) {
                if (pos.equals(trace.hitBlock()) && side == trace.hitSide()) {
                    if (first == null) first = LaserColor.nearest(trace.rgb());
                    combined |= trace.combinedBy() != null;
                    if (world.getBlockEntity(entry.getKey()) instanceof LaserBeamSource source)
                        color.add(trace.rgb(), Math.max(1, source.luminousFlux()) * trace.power());
                }
            }
        }
        return combined ? LaserColor.nearest(color.rgb()) : first;
    }

    private static Snapshot snapshot(World world, float tickDelta) {
        State state = state(world);
        if (state.snapshot != null && state.time == world.getTime() && state.tickDelta == tickDelta) {
            return state.snapshot;
        }
        Map<BlockPos, LaserBeamPath> paths = new LinkedHashMap<>();
        Map<Integer, LaserBeamPath.CubeRoute> owners = new HashMap<>();
        Map<Integer, CubeInput> inputs = new HashMap<>();
        Map<BlockPos, OpticInput> optics = new HashMap<>();
        for (BlockPos pos : state.emitters.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList()) {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof LaserBeamSource emitter
                    && emitter.isBeamActive()) {
                paths.put(pos, LaserBeamPath.trace(world, emitter, tickDelta, owners, inputs, optics));
            }
        }
        tintCombinedInputs(world, paths, optics, inputs, owners);
        state.time = world.getTime();
        state.tickDelta = tickDelta;
        state.snapshot = new Snapshot(paths, inputs, optics);
        return state.snapshot;
    }

    public record CubeInput(LaserColor color, boolean emission, int rgb) {
        public CubeInput(LaserColor color, boolean emission) { this(color, emission, color.rgb()); }
    }

    public record OpticInput(int rgb, boolean emission, double power) { }

    private static void tintCombinedInputs(World world, Map<BlockPos, LaserBeamPath> paths,
                                          Map<BlockPos, OpticInput> optics, Map<Integer, CubeInput> cubes,
                                          Map<Integer, LaserBeamPath.CubeRoute> owners) {
        Map<BlockPos, LightMixture> mixtures = new HashMap<>();
        Map<BlockPos, Boolean> emission = new HashMap<>();
        Map<Integer, LightMixture> cubeColors = new HashMap<>();
        Map<Integer, Boolean> cubeEmission = new HashMap<>();
        paths.forEach((pos, path) -> {
            if (!(world.getBlockEntity(pos) instanceof LaserBeamSource source)) return;
            for (var ray : path.segments()) {
                if (ray.combinedBy() != null) owners.forEach((id, route) -> {
                    if (cubes.containsKey(id) && ray.combinedBy().equals(route.combiner())
                            && ray.end().squaredDistanceTo(route.entry()) < 1e-10 && ray.axis().dotProduct(route.axis()) > .999999) {
                        cubeColors.computeIfAbsent(id, ignored -> new LightMixture()).add(ray.rgb(), Math.max(1, source.luminousFlux()) * ray.power());
                        cubeEmission.merge(id, source.isLightEmissionEnabled(), Boolean::logicalOr);
                    }
                });
                // Redirected segments do not retain a block hit, so recover the optical port at the endpoint.
                BlockPos endpoint = BlockPos.ofFloored(ray.end().add(ray.axis().multiply(OpticalGeometry.EPSILON)));
                if (!optics.containsKey(endpoint)) continue;
                if (ray.combinedBy() == null && (!(world.getBlockEntity(endpoint) instanceof LaserOpticBlockEntity optic)
                        || optic.kind() != net.askcraft.justifylasers.block.LaserOpticBlock.Kind.COMBINER)) continue;
                mixtures.computeIfAbsent(endpoint, ignored -> new LightMixture()).add(ray.rgb(), Math.max(1, source.luminousFlux()) * ray.power());
                emission.merge(endpoint, source.isLightEmissionEnabled(), Boolean::logicalOr);
            }
        });
        mixtures.forEach((pos, color) -> optics.put(pos, new OpticInput(color.rgb(), emission.get(pos), color.weight())));
        cubeColors.forEach((id, color) -> cubes.put(id, new CubeInput(LaserColor.nearest(color.rgb()), cubeEmission.get(id), color.rgb())));
    }

    private static final class LightDelivery {
        private long lumens;
        private final LightMixture color = new LightMixture();
        void add(long amount, int rgb) {
            color.add(rgb, amount);
            lumens = LuminousFlux.clamp(lumens + amount);
        }
    }

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
