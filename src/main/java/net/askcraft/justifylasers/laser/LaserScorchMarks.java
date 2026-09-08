package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-side surface effects; no block states, damage rules, or chunk loading are changed. */
public final class LaserScorchMarks {
    public static final int LIFETIME_TICKS = 2_400;
    public static final int COOLING_TICKS = 50;
    public static final int MAX_PATCHES = 2_048;
    public static final int MAX_VERTICES = 500_000;
    private final Map<Long, Mark> marks = new LinkedHashMap<>(128, 0.75F, true);
    private final Map<BlockPos, Contact> contacts = new HashMap<>();
    private World world;
    private long lastTick = Long.MIN_VALUE;
    private long nextId;
    private int vertexCount;

    public void tick(@Nullable World world) {
        if (world == null) {
            clear();
        } else {
            update(world, world.getTime());
        }
    }

    void update(World nextWorld, long now) {
        if (world != nextWorld || now < lastTick) {
            clear();
            world = nextWorld;
        }
        if (now == lastTick) {
            return;
        }
        lastTick = now;
        Iterator<Mark> iterator = marks.values().iterator();
        while (iterator.hasNext()) {
            Mark mark = iterator.next();
            ScorchGeometry.Patch patch = mark.patch;
            if (now - mark.touched >= LIFETIME_TICKS || !world.isChunkLoaded(patch.position())
                    || world.getBlockState(patch.position()) != patch.state()) {
                vertexCount -= mark.vertexCount;
                iterator.remove();
            }
        }
        Set<BlockPos> activeContacts = new HashSet<>();
        for (Map.Entry<BlockPos, LaserBeamPath> entry : LaserBeamNetwork.paths(world, 1).entrySet()) {
            LaserBeamTrace trace = entry.getValue().last();
            if (!trace.hasBlockHit() || trace.hitSide() == null
                    || !(world.getBlockEntity(entry.getKey()) instanceof LaserEmitterBlockEntity emitter)) {
                continue;
            }
            BlockState target = world.getBlockState(trace.hitBlock());
            if (target.getBlock() instanceof LaserReceiverBlock && target.get(LaserReceiverBlock.FACING) == trace.hitSide()) {
                continue;
            }
            activeContacts.add(entry.getKey());
            record(entry.getKey(), trace.end(), trace.hitSide(), ScorchGeometry.radius(emitter.getBeamWidthScale()),
                    emitter.isLightEmissionEnabled(), now);
        }
        contacts.keySet().retainAll(activeContacts);
        iterator = marks.values().iterator();
        while ((marks.size() > MAX_PATCHES || vertexCount > MAX_VERTICES) && iterator.hasNext()) {
            vertexCount -= iterator.next().vertexCount;
            iterator.remove();
        }
    }

    private void record(BlockPos source, Vec3d point, Direction face, double radius, boolean emission, long now) {
        Contact previous = contacts.get(source);
        Vec3d from = point;
        if (previous != null && previous.face == face && previous.tick == now - 1
                && Math.abs(point.subtract(previous.point).dotProduct(Vec3d.of(face.getVector()))) < 0.0001D
                && previous.point.squaredDistanceTo(point) <= 16.0D
                && Math.abs(previous.radius - radius) < 0.001D) {
            if (previous.point.squaredDistanceTo(point) < Math.pow(Math.max(0.008D, radius * 0.14D), 2)) {
                boolean retained = !previous.marks.isEmpty();
                for (long id : previous.marks) {
                    Mark mark = marks.get(id);
                    if (mark == null) {
                        retained = false;
                    } else {
                        mark.touched = now;
                        mark.emission = emission;
                    }
                }
                if (retained) {
                    contacts.put(source, new Contact(previous.point, face, radius, now, previous.marks));
                    return;
                }
            }
            from = previous.point;
        }
        List<Long> added = new ArrayList<>();
        for (ScorchGeometry.Patch patch : ScorchGeometry.create(world, from, point, face, radius)) {
            Mark mark = new Mark(patch, now, emission);
            long id = nextId++;
            marks.put(id, mark);
            added.add(id);
            vertexCount += mark.vertexCount;
        }
        contacts.put(source, new Contact(point, face, radius, now, List.copyOf(added)));
    }

    public boolean belongsTo(World world) {
        return this.world == world;
    }

    public Collection<Mark> marks() {
        return Collections.unmodifiableCollection(marks.values());
    }

    public void clear() {
        marks.clear();
        contacts.clear();
        world = null;
        lastTick = Long.MIN_VALUE;
        nextId = 0;
        vertexCount = 0;
    }

    public static float heat(double age) {
        return (float) Math.pow(MathHelper.clamp(1 - age / COOLING_TICKS, 0, 1), 1.4D);
    }

    public static float opacity(double age) {
        double fade = MathHelper.clamp((LIFETIME_TICKS - age) / 400.0D, 0, 1);
        return (float) (fade * fade * (3 - 2 * fade));
    }

    public static final class Mark {
        private final ScorchGeometry.Patch patch;
        private final int vertexCount;
        private long touched;
        private boolean emission;

        private Mark(ScorchGeometry.Patch patch, long touched, boolean emission) {
            this.patch = patch;
            this.touched = touched;
            this.emission = emission;
            vertexCount = patch.soot().size() + patch.heat().size() + patch.emission().size();
        }

        public ScorchGeometry.Patch patch() {
            return patch;
        }

        public double age(double now) {
            return Math.max(0, now - touched);
        }

        public boolean emitsLight() {
            return emission;
        }
    }

    private record Contact(Vec3d point, Direction face, double radius, long tick, List<Long> marks) {
    }
}
