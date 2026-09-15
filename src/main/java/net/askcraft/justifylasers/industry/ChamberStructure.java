package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class ChamberStructure {
    public static List<BlockPos> positions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>(8);
        for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) positions.add(origin.add(x, y, z));
        return positions;
    }

    public static boolean complete(World world, BlockPos origin, MachineKind kind) {
        for (BlockPos pos : positions(origin)) {
            if (!world.isChunkLoaded(pos) || !(world.getBlockEntity(pos) instanceof IndustrialMachineBlockEntity part)
                    || part.kind() != kind || !origin.equals(part.origin())) return false;
        }
        return true;
    }

    public static boolean form(IndustrialMachineBlockEntity clicked) {
        World world = clicked.getWorld();
        if (world == null || world.isClient || !clicked.kind().multiblock()) return false;
        if (clicked.formed()) return true;
        BlockPos position = clicked.getPos();
        for (int y = -1; y <= 0; y++) for (int z = -1; z <= 0; z++) for (int x = -1; x <= 0; x++) {
            BlockPos origin = position.add(x, y, z);
            List<IndustrialMachineBlockEntity> parts = new ArrayList<>(8);
            for (BlockPos pos : positions(origin)) {
                if (!world.isChunkLoaded(pos) || !(world.getBlockEntity(pos) instanceof IndustrialMachineBlockEntity part)
                        || part.kind() != clicked.kind() || part.origin() != null && !origin.equals(part.origin())
                        || part.owner() != null && !part.owner().equals(clicked.owner())
                        || !pos.equals(origin) && part.hasLocalContents()) break;
                parts.add(part);
            }
            if (parts.size() != 8) continue;
            var master = parts.get(0);
            if (master.owner() == null) master.copyAccess(clicked);
            var facing = clicked.getCachedState().get(net.askcraft.justifylasers.block.IndustrialMachineBlock.FACING);
            for (var part : parts) {
                world.setBlockState(part.getPos(), part.getCachedState().with(net.askcraft.justifylasers.block.IndustrialMachineBlock.FACING, facing), 2);
                part.setOrigin(origin);
                if (part != master) part.copyAccess(master);
            }
            return true;
        }
        return false;
    }

    public static void dismantle(IndustrialMachineBlockEntity removed) {
        if (removed.origin() == null || removed.getWorld() == null || removed.getWorld().isClient) return;
        BlockPos origin = removed.origin();
        for (BlockPos pos : positions(origin)) {
            if (!removed.getWorld().isChunkLoaded(pos)) continue;
            if (removed.getWorld().getBlockEntity(pos) instanceof IndustrialMachineBlockEntity part && origin.equals(part.origin())) part.setOrigin(null);
        }
    }

    private ChamberStructure() { }
}
