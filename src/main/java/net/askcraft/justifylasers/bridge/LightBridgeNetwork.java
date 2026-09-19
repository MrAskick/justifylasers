package net.askcraft.justifylasers.bridge;

import com.google.common.collect.Iterables;
import net.askcraft.justifylasers.block.entity.LightBridgeBlockEntity;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LightMixture;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.network.LightBridgePacket;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.block.ShapeContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Positions and immutable fields only; values must not retain their weakly keyed world. */
public final class LightBridgeNetwork {
    private static final Map<World, State> WORLDS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final Comparator<BlockPos> ORDER = Comparator.comparingInt(BlockPos::getX)
            .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);

    private static State state(World world) { return WORLDS.computeIfAbsent(world, key -> new State()); }

    public static void register(World world, BlockPos pos) {
        if (!world.isClient) state(world).emitters.add(pos.toImmutable());
    }

    public static void unregister(World world, BlockPos pos) {
        if (world.isClient) return; // The server may still be projecting into a nearby client chunk.
        State state = WORLDS.get(world);
        if (state == null) return;
        state.emitters.remove(pos);
        removeFieldsAt(world, pos);
    }

    public static void removeFieldsAt(World world, BlockPos pos) {
        if (world.isClient) return;
        State state = WORLDS.get(world);
        if (state != null) state.replace(state.fields.stream().filter(span -> {
            for (int i = 0; i < span.width(); i++) if (span.emitter(i).equals(pos)) return false;
            return true;
        }).toList());
    }

    public static List<LightBridgeSpan> fields(World world) {
        State state = WORLDS.get(world);
        return state == null ? List.of() : state.fields;
    }

    public static LightBridgeSpan at(World world, BlockPos pos) {
        for (LightBridgeSpan span : fields(world))
            for (int i = 0; i < span.width(); i++) if (span.emitter(i).equals(pos)) return span;
        return null;
    }

    public static void receive(World world, LightBridgePacket packet) {
        if (world != null && world.isClient && world.getRegistryKey().getValue().equals(packet.dimension())) {
            state(world).replace(packet.fields());
            state(world).landingBoxes = packet.landings();
        }
    }

    public static void restoreLanding(net.minecraft.server.network.ServerPlayerEntity player, BlockPos origin, Box box) {
        ServerWorld world = (ServerWorld) player.getWorld();
        State state = state(world);
        state.landings.put(player.getUuid(), new Landing(origin, box, world.getTime()));
        state.landingBoxes = state.landings.values().stream().map(Landing::box).toList();
        sendNearby(world, state);
    }

    private static void updateLandings(ServerWorld world, State state) {
        state.landings.entrySet().removeIf(entry -> {
            Landing landing = entry.getValue();
            var player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.getWorld() != world || player.isSpectator() || !player.getBoundingBox().expand(1).intersects(landing.box)) return true;
            LightBridgeSpan field = at(world, landing.origin);
            if (field != null && field.active() && field.bounds().intersects(landing.box)) return true;
            // Allow two seconds for chunk/block-entity startup, but never persist a powered surface.
            return world.getTime() - landing.start >= 40;
        });
        state.landingBoxes = state.landings.values().stream().map(Landing::box).toList();
    }

    public static void tick(ServerWorld world) {
        State state = WORLDS.get(world);
        if (state == null) return;
        long tick = world.getTime();
        if (state.lastTick == tick) return;
        state.lastTick = tick;
        var loaded = new LinkedHashMap<BlockPos, LightBridgeBlockEntity>();
        state.emitters.stream().sorted(ORDER).forEach(pos -> {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof LightBridgeBlockEntity bridge && !bridge.isRemoved())
                loaded.put(pos, bridge);
        });
        var used = new HashSet<BlockPos>();
        var groups = new ArrayList<Group>();
        for (var entry : loaded.entrySet()) {
            BlockPos origin = entry.getKey();
            if (used.contains(origin)) continue;
            Direction facing = entry.getValue().facing(), mount = entry.getValue().mount();
            boolean rolled = entry.getValue().rolled();
            boolean corner = entry.getValue().corner();
            var orientation = entry.getValue().orientation();
            Direction across = orientation.gridAcross();
            boolean groupable = !corner && BridgeGeometry.cardinal(orientation.acrossVector());
            if (groupable) {
                while (matching(loaded.get(origin.offset(across.getOpposite())), orientation, used)) origin = origin.offset(across.getOpposite());
            }
            used.add(origin);
            var first = loaded.get(origin);
            long flux = first.input(tick);
            var color = new LightMixture();
            color.add(first.rgb(), flux);
            int width = 1;
            while (groupable && width < LightBridgeSpan.MAX_WIDTH) {
                BlockPos next = origin.offset(across, width);
                LightBridgeBlockEntity bridge = loaded.get(next);
                if (!matching(bridge, orientation, used)) break;
                used.add(next);
                long input = bridge.input(tick);
                flux = LuminousFlux.clamp(flux + input);
                color.add(bridge.rgb(), input);
                width++;
            }
            groups.add(new Group(origin, orientation, corner, width, flux, color.rgb()));
        }
        // Match aperture endpoints, not merely adjacent block positions: differently rolled panels must not join.
        var endpoints = new HashMap<Endpoint, List<End>>();
        for (Group group : groups) for (int end = 0; end < 2; end++) {
            var key = Endpoint.of(group.orientation.forward(), group.endpoint(end));
            var matches = endpoints.computeIfAbsent(key, ignored -> new ArrayList<>());
            for (End other : matches) if (group.corner || other.group.corner) {
                group.neighbors.add(other.group); other.group.neighbors.add(group);
                group.connections |= 1 << end; other.group.connections |= 1 << other.index;
            }
            matches.add(new End(group, end));
        }
        var fields = new ArrayList<LightBridgeSpan>();
        var visited = new HashSet<Group>();
        for (Group seed : groups) {
            if (!visited.add(seed)) continue;
            var connected = new ArrayList<Group>();
            connected.add(seed);
            long flux = 0;
            int lanes = 0;
            var color = new LightMixture();
            for (int i = 0; i < connected.size(); i++) {
                Group group = connected.get(i);
                flux = LuminousFlux.clamp(flux + group.flux);
                lanes += group.corner ? 2 : group.width;
                color.add(group.rgb, group.flux);
                for (Group neighbor : group.neighbors) if (visited.add(neighbor)) connected.add(neighbor);
            }
            var config = LaserConfig.get();
            int powered = LightBridgeSpan.poweredLength(flux, lanes, config.lightBridgeLumensPerBlock, config.lightBridgeMaxLength);
            for (Group group : connected) {
                var o = group.orientation;
                double length = powered > 0 ? powered + o.leadIn() : 0;
                for (int section = group.corner ? 0 : -1; section < (group.corner ? 2 : 0); section++) {
                    var candidate = new LightBridgeSpan(group.origin, o.forward(), o.mount(), o.rolled(), o.rotation(), section,
                            group.connections, group.width, length, flux > 0 ? color.rgb() : 0x6A737C, flux * (group.corner ? 1 : group.width) / lanes);
                    fields.add(candidate.withLength(clip(world, candidate)));
                }
            }
        }
        state.replace(fields);
        updateLandings(world, state);
        sendNearby(world, state);
    }

    private static boolean matching(LightBridgeBlockEntity bridge, BridgeOrientation orientation, Set<BlockPos> used) {
        return bridge != null && !bridge.corner() && bridge.orientation().equals(orientation) && !used.contains(bridge.getPos());
    }

    private static final class Group {
        final BlockPos origin;
        final BridgeOrientation orientation;
        final boolean corner;
        final int width, rgb;
        final long flux;
        final List<Group> neighbors = new ArrayList<>();
        int connections;
        Group(BlockPos origin, BridgeOrientation orientation, boolean corner, int width, long flux, int rgb) {
            this.origin = origin; this.orientation = orientation; this.corner = corner; this.width = width; this.flux = flux; this.rgb = rgb;
        }
        net.minecraft.util.math.Vec3d endpoint(int index) {
            return corner ? orientation.cornerPoint(origin, index == 0 ? .5 : BridgeOrientation.CORNER, BridgeOrientation.HALF_DEPTH,
                    index == 0 ? BridgeOrientation.CORNER : .5)
                    : orientation.point(origin, index == 0 ? -.5 : width - .5, BridgeOrientation.HALF_DEPTH, 0);
        }
    }
    private record End(Group group, int index) { }
    private record Endpoint(Direction direction, long x, long y, long z) {
        static Endpoint of(Direction direction, net.minecraft.util.math.Vec3d point) {
            return new Endpoint(direction, Math.round(point.x * 1_000_000), Math.round(point.y * 1_000_000), Math.round(point.z * 1_000_000));
        }
    }

    private static void sendNearby(ServerWorld world, State state) {
        // Players near the far end need the surface even when the emitter's chunk isn't tracked.
        var present = new HashSet<UUID>();
        for (var player : world.getPlayers()) {
            present.add(player.getUuid());
            Box interest = player.getBoundingBox().expand(160);
            List<LightBridgeSpan> nearby = state.fields.stream()
                    .filter(span -> span.bounds().union(span.hardwareBounds()).intersects(interest)).toList();
            List<Box> pads = state.landingBoxes.stream().filter(interest::intersects).toList();
            var previous = state.sent.get(player.getUuid());
            if (previous == null || previous.player().get() != player || !nearby.equals(previous.fields()) || !pads.equals(previous.landings())) {
                Platform.sendLightBridges(player, new LightBridgePacket(world.getRegistryKey().getValue(), nearby, pads));
                state.sent.put(player.getUuid(), new Delivery(new WeakReference<>(player), nearby, pads));
            }
        }
        state.sent.keySet().retainAll(present);
    }

    static double clip(World world, LightBridgeSpan span) {
        if (!span.active()) return 0;
        double length = span.length();
        Box bounds = span.bounds();
        Direction forward = span.facing();
        var surfaces = span.collisionBoxes();
        // Check the real ribbon rather than its containing AABB, including diagonal and corner sheets.
        double faceGap = span.orientation().leadIn();
        for (int step = 0; step + faceGap < length; step++) {
            BlockPos row = span.origin().offset(forward, step + 1);
            int x0 = forward.getAxis() == Direction.Axis.X ? row.getX() : MathHelper.floor(bounds.minX + 1e-7);
            int x1 = forward.getAxis() == Direction.Axis.X ? row.getX() : MathHelper.floor(bounds.maxX - 1e-7);
            int y0 = forward.getAxis() == Direction.Axis.Y ? row.getY() : MathHelper.floor(bounds.minY + 1e-7);
            int y1 = forward.getAxis() == Direction.Axis.Y ? row.getY() : MathHelper.floor(bounds.maxY - 1e-7);
            int z0 = forward.getAxis() == Direction.Axis.Z ? row.getZ() : MathHelper.floor(bounds.minZ + 1e-7);
            int z1 = forward.getAxis() == Direction.Axis.Z ? row.getZ() : MathHelper.floor(bounds.maxZ - 1e-7);
            for (BlockPos pos : BlockPos.iterate(x0, y0, z0, x1, y1, z1)) {
                if (!world.isChunkLoaded(pos) || !world.getWorldBorder().contains(pos)) return Math.min(length, step + faceGap);
                for (Box obstacle : world.getBlockState(pos).getCollisionShape(world, pos, ShapeContext.absent()).getBoundingBoxes()) {
                    Box box = obstacle.offset(pos);
                    if (!box.intersects(bounds) || surfaces.stream().noneMatch(box::intersects)) continue;
                    double distance = switch (forward) {
                        case EAST -> box.minX - bounds.minX;
                        case WEST -> bounds.maxX - box.maxX;
                        case SOUTH -> box.minZ - bounds.minZ;
                        case NORTH -> bounds.maxZ - box.maxZ;
                        case UP -> box.minY - bounds.minY;
                        case DOWN -> bounds.maxY - box.maxY;
                    };
                    length = Math.min(length, Math.max(0, distance));
                }
            }
        }
        return length;
    }

    public static Iterable<VoxelShape> appendCollisions(World world, Box box, Iterable<VoxelShape> blocks) {
        List<VoxelShape> fields = collisions(world, box);
        return fields.isEmpty() ? blocks : Iterables.concat(blocks, fields);
    }

    public static List<VoxelShape> collisions(World world, Box query) {
        State state = WORLDS.get(world);
        if (state == null) return List.of();
        var result = new ArrayList<VoxelShape>();
        for (var pad : state.landingBoxes) if (pad.intersects(query)) result.add(VoxelShapes.cuboid(pad));
        var visited = new HashSet<LightBridgeSpan>();
        int x0 = MathHelper.floor(query.minX) >> 4, x1 = MathHelper.floor(query.maxX) >> 4;
        int z0 = MathHelper.floor(query.minZ) >> 4, z1 = MathHelper.floor(query.maxZ) >> 4;
        // Large diagnostic queries shouldn't iterate millions of empty chunks.
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 256) {
            for (var field : state.fields) if (field.active() && field.bounds().intersects(query)) addCollisions(state, field, query, result);
        } else for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            for (var field : state.buckets.getOrDefault(ChunkPos.toLong(x, z), List.of()))
                if (visited.add(field) && field.bounds().intersects(query)) addCollisions(state, field, query, result);
        }
        return result;
    }

    private static void addCollisions(State state, LightBridgeSpan field, Box query, List<VoxelShape> result) {
        for (Box box : state.surfaces.getOrDefault(field, List.of())) if (box.intersects(query)) result.add(VoxelShapes.cuboid(box));
    }

    private static final class State {
        final Set<BlockPos> emitters = new HashSet<>();
        final Map<UUID, Delivery> sent = new HashMap<>();
        final Map<Long, List<LightBridgeSpan>> buckets = new HashMap<>();
        final Map<LightBridgeSpan, List<Box>> surfaces = new HashMap<>();
        final Map<UUID, Landing> landings = new HashMap<>();
        List<Box> landingBoxes = List.of();
        List<LightBridgeSpan> fields = List.of();
        long lastTick = Long.MIN_VALUE;

        void replace(List<LightBridgeSpan> next) {
            if (fields.equals(next)) return;
            fields = List.copyOf(next);
            buckets.clear();
            surfaces.keySet().retainAll(fields);
            for (var span : fields) {
                if (!span.active()) continue;
                surfaces.computeIfAbsent(span, LightBridgeSpan::collisionBoxes);
                Box box = span.bounds();
                for (int x = MathHelper.floor(box.minX) >> 4; x <= MathHelper.floor(box.maxX) >> 4; x++)
                    for (int z = MathHelper.floor(box.minZ) >> 4; z <= MathHelper.floor(box.maxZ) >> 4; z++)
                        buckets.computeIfAbsent(ChunkPos.toLong(x, z), key -> new ArrayList<>()).add(span);
            }
        }
    }

    private record Landing(BlockPos origin, Box box, long start) { }
    private record Delivery(WeakReference<net.minecraft.server.network.ServerPlayerEntity> player, List<LightBridgeSpan> fields, List<Box> landings) { }

    private LightBridgeNetwork() { }
}
