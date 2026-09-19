package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity;
import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class SolarStructure {
    public record Cell(BlockPos offset, String component) { }
    public static final List<Cell> BODY = layout();
    public static final List<Cell> RESONATORS = java.util.stream.Stream.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
            .map(side -> new Cell(new BlockPos(1, 0, 1).offset(side, 2), "optical_resonator")).toList();
    public static final List<Cell> PARTS = java.util.stream.Stream.concat(BODY.stream(), RESONATORS.stream()).toList();

    public static Direction resonatorFacing(Cell cell) {
        return cell.offset.getX() == 1 ? cell.offset.getZ() < 1 ? Direction.NORTH : Direction.SOUTH
                : cell.offset.getX() < 1 ? Direction.WEST : Direction.EAST;
    }

    private static List<Cell> layout() {
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            boolean center = x == 1 && z == 1;
            String component = y == 0 ? "small_solar_concentrator" : y == 2 ? (center ? "laser_absorbing_glass" : "small_solar_concentrator")
                    : center ? "energy_core" : x != 1 && z != 1 ? "reinforced_laser_housing" : "beam_controller";
            cells.add(new Cell(new BlockPos(x, y, z), component));
        }
        return List.copyOf(cells);
    }
    public static BlockPos origin(SolarConcentratorBlockEntity source) {
        if (source.small()) return source.getPos();
        if (source.structureOrigin() != null) return source.structureOrigin();
        // The resonator's placement rotation is cosmetic, not a multiblock assembly constraint.
        for (Cell port : RESONATORS) {
            BlockPos candidate = source.getPos().subtract(port.offset());
            if (matchesAt(source, candidate)) return candidate;
        }
        return source.getPos().offset(source.facing().getOpposite(), 2).add(-1, 0, -1);
    }
    public static boolean matches(SolarConcentratorBlockEntity source) {
        return matchesAt(source, origin(source));
    }
    private static boolean matchesAt(SolarConcentratorBlockEntity source, BlockPos origin) {
        World world = source.getWorld();
        if (source.small() || world == null || !world.isChunkLoaded(source.getPos()) || source.isRemoved()
                || !world.getBlockState(source.getPos()).isOf(ModIndustry.COMPONENT_BLOCKS.get("optical_resonator"))) return false;
        for (Cell cell : PARTS) {
            BlockPos pos = origin.add(cell.offset);
            if (!world.isChunkLoaded(pos) || !world.getBlockState(pos).isOf(ModIndustry.COMPONENT_BLOCKS.get(cell.component))
                    || !(world.getBlockEntity(pos) instanceof LaserComponentBlockEntity part)) return false;
            if (part.controllerPos() != null && !part.controllerPos().equals(source.getPos())) return false;
            if (part instanceof SolarConcentratorBlockEntity collector && !collector.allowsJoining(source)) return false;
        }
        return true;
    }
    public static boolean form(SolarConcentratorBlockEntity source) {
        if (source.getWorld() == null || source.getWorld().isClient || !matches(source)) return false;
        source.setStructureOrigin(origin(source));
        for (Cell cell : PARTS) {
            var part = (LaserComponentBlockEntity) source.getWorld().getBlockEntity(origin(source).add(cell.offset));
            if (!source.getPos().equals(part.controllerPos())) part.assignController(source.getPos());
        }
        if (!source.getPos().equals(source.controllerPos())) source.assignController(source.getPos());
        source.shareSettings();
        return true;
    }
    public static void formNearby(World world, BlockPos changed) {
        for (BlockPos candidate : BlockPos.iterate(changed.add(-4, -2, -4), changed.add(4, 0, 4))) {
            if (world.isChunkLoaded(candidate) && world.getBlockState(candidate).isOf(ModIndustry.COMPONENT_BLOCKS.get("optical_resonator"))
                    && world.getBlockEntity(candidate) instanceof SolarConcentratorBlockEntity source) form(source);
        }
    }
    public static void dismantle(LaserComponentBlockEntity part) {
        World world = part.getWorld();
        if (world == null || world.isClient) return;
        SolarConcentratorBlockEntity source = part.controller();
        if (source == null) { part.assignController(null); return; }
        source.stop();
        // Also release the alpha.24 layout, whose resonator was one block higher.
        for (BlockPos pos : BlockPos.iterate(source.getPos().add(-4,-2,-4), source.getPos().add(4,2,4))) {
            if (world.isChunkLoaded(pos) && world.getBlockEntity(pos) instanceof LaserComponentBlockEntity member
                    && source.getPos().equals(member.controllerPos())) member.assignController(null);
        }
        source.assignController(null);
        source.setStructureOrigin(null);
        LaserBeamNetwork.invalidate(world);
    }
    private SolarStructure() { }
}
