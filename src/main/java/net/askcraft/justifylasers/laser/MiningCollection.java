package net.askcraft.justifylasers.laser;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Captures only newly spawned drops during this one synchronous block removal. */
public final class MiningCollection {
    private record Context(ServerWorld world, Box bounds, LaserLootCollector destination) { }
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public static boolean breakBlock(ServerWorld world, BlockPos pos, boolean drops, boolean silk, LaserLootCollector destination) {
        return breakBlock(world, pos, drops, silk, false, destination);
    }

    public static boolean breakBlock(ServerWorld world, BlockPos pos, boolean drops, boolean silk, boolean smelt, LaserLootCollector destination) {
        if (!drops || destination == null) return LaserMining.breakBlock(world, pos, drops, silk, smelt);
        Context previous = CURRENT.get();
        CURRENT.set(new Context(world, new Box(pos).expand(.5), destination));
        try { return LaserMining.breakBlock(world, pos, true, silk, smelt); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static boolean capture(ServerWorld world, Entity entity) {
        Context context = CURRENT.get();
        if (context == null || context.world != world || !(entity instanceof ItemEntity item)
                || !context.bounds.contains(item.getPos())) return false;
        var remaining = context.destination.collect(item.getStack());
        if (remaining.isEmpty()) return true;
        item.setStack(remaining);
        return false;
    }

    private MiningCollection() { }
}
