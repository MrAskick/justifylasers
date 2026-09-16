package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class SolarExposure {
    public record Panel(Vec3d position, Direction normal) { }
    public record Exposure(double fraction, long visiblePanels) { }
    public static final List<Panel> PANELS = panels();

    private static List<Panel> panels() {
        List<Panel> result = new ArrayList<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++)
            result.add(new Panel(new Vec3d(x + .5, 3.01, z + .5), Direction.UP));
        for (int y : new int[]{0, 2}) for (int n = 0; n < 3; n++) {
            result.add(new Panel(new Vec3d(-.01, y + .5, n + .5), Direction.WEST));
            result.add(new Panel(new Vec3d(3.01, y + .5, n + .5), Direction.EAST));
            result.add(new Panel(new Vec3d(n + .5, y + .5, -.01), Direction.NORTH));
            result.add(new Panel(new Vec3d(n + .5, y + .5, 3.01), Direction.SOUTH));
        }
        return List.copyOf(result);
    }
    public static Vec3d sunDirection(World world, float delta) {
        double angle = world.getSkyAngle(delta) * Math.PI * 2;
        return new Vec3d(-Math.sin(angle), Math.cos(angle), 0);
    }
    public static boolean hasSun(World world) {
        return world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling() && !world.isRaining()
                && !world.isThundering() && sunDirection(world, 1).y > .015;
    }
    public static Exposure measure(World world, BlockPos origin) {
        if (!hasSun(world)) return new Exposure(0, 0);
        Vec3d sun = sunDirection(world, 1);
        long visible = 0;
        for (int index = 0; index < PANELS.size(); index++) {
            Panel panel = PANELS.get(index);
            double incidence = sun.dotProduct(Vec3d.of(panel.normal.getVector()));
            if (incidence <= .001 || !seesSun(world, Vec3d.of(origin).add(panel.position), sun)) continue;
            visible |= 1L << index;
        }
        return new Exposure(fraction(sun, visible), visible);
    }

    public static double activity(double altitude) {
        double t = Math.max(0, Math.min(1, (altitude - .015) / .985));
        return t * t * (3 - 2 * t);
    }

    public static double fraction(Vec3d sun, long visiblePanels) {
        double exposed = 0, possible = 0;
        for (int index = 0; index < PANELS.size(); index++) {
            double incidence = Math.max(0, sun.dotProduct(Vec3d.of(PANELS.get(index).normal.getVector())));
            possible += incidence;
            if ((visiblePanels & (1L << index)) != 0) exposed += incidence;
        }
        // Normalize shading independently: side panels must not flatten the daily production curve.
        return possible <= 0 ? 0 : activity(sun.y) * Math.min(1, exposed / possible);
    }
    public static boolean seesSun(World world, Vec3d start, Vec3d direction) {
        double distance = Math.min(128, Math.max(1, (world.getTopY() + 1 - start.y) / direction.y));
        Vec3d end = start.add(direction.multiply(distance));
        // Unknown terrain is not assumed transparent; daylight probes never load chunks.
        return BlockView.raycast(start, end, world, (view, pos) -> {
            if (!view.isChunkLoaded(pos)) return Boolean.FALSE;
            var state = view.getBlockState(pos);
            if (state.isAir() || state.isOf(Blocks.GLASS) || state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof StainedGlassPaneBlock
                    || state.isOf(Blocks.GLASS_PANE) || state.isOf(ModIndustry.LASER_ABSORBING_GLASS)) return null;
            var shape = state.getCollisionShape(view, pos, ShapeContext.absent());
            return !shape.isEmpty() && shape.raycast(start, end, pos) != null ? Boolean.FALSE : null;
        }, ignored -> Boolean.TRUE);
    }
    private SolarExposure() { }
}
