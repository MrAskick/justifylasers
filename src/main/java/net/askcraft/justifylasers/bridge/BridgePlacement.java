package net.askcraft.justifylasers.bridge;

import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.List;

/** Join apertures, including a straight panel continuing either arm of a corner. */
public final class BridgePlacement {
    public record Neighbor(BlockPos pos, BridgeOrientation orientation, boolean corner) { }

    public static BlockState align(BlockState preferred, BlockView world, BlockPos pos) {
        var neighbors = new ArrayList<Neighbor>();
        for (Direction side : Direction.values()) {
            BlockPos next = pos.offset(side);
            BlockState state = world.getBlockState(next);
            if (state.getBlock() instanceof LightBridgeBlock bridge)
                neighbors.add(new Neighbor(next, LightBridgeBlock.orientation(state), bridge.corner()));
        }
        if (neighbors.isEmpty()) return preferred;
        var result = align(LightBridgeBlock.orientation(preferred), ((LightBridgeBlock) preferred.getBlock()).corner(), pos, neighbors);
        return preferred.with(LightBridgeBlock.FACING, result.forward()).with(LightBridgeBlock.MOUNT, result.mount())
                .with(LightBridgeBlock.ROLLED, result.rolled()).with(LightBridgeBlock.ROTATION, result.rotation());
    }

    public static BridgeOrientation align(BridgeOrientation preferred, boolean corner, BlockPos pos, List<Neighbor> neighbors) {
        BridgeOrientation best = preferred;
        int bestConnections = connections(preferred, corner, pos, neighbors);
        double bestPreference = preference(preferred, preferred);
        for (Direction forward : Direction.values()) {
            if (neighbors.stream().noneMatch(n -> n.orientation.forward() == forward)) continue;
            for (Direction mount : Direction.values()) for (boolean rolled : new boolean[]{false, true}) for (int angle = 0; angle < 8; angle++) {
                var candidate = new BridgeOrientation(forward, mount, rolled, angle);
                int connections = connections(candidate, corner, pos, neighbors);
                double preference = preference(candidate, preferred);
                if (connections > bestConnections || connections > 0 && connections == bestConnections && preference > bestPreference) {
                    best = candidate; bestConnections = connections; bestPreference = preference;
                }
            }
        }
        return best;
    }

    private static double preference(BridgeOrientation candidate, BridgeOrientation preferred) {
        return (candidate.equals(preferred) ? 100 : 0) + (candidate.forward() == preferred.forward() ? 10 : 0)
                + (candidate.mount() == preferred.mount() ? 4 : 0)
                + candidate.normalVector().dotProduct(preferred.normalVector())
                + candidate.acrossVector().dotProduct(preferred.acrossVector());
    }

    private static int connections(BridgeOrientation candidate, boolean corner, BlockPos pos, List<Neighbor> neighbors) {
        int count = 0;
        for (Neighbor neighbor : neighbors) {
            if (candidate.forward() != neighbor.orientation.forward()) continue;
            boolean joined = false;
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++)
                if (endpoint(candidate, pos, corner, a).squaredDistanceTo(endpoint(neighbor.orientation, neighbor.pos, neighbor.corner, b)) < 1e-10)
                    joined = true;
            if (joined) count++;
        }
        return count;
    }

    public static Vec3d endpoint(BridgeOrientation frame, BlockPos pos, boolean corner, int end) {
        return corner ? frame.cornerPoint(pos, end == 0 ? .5 : BridgeOrientation.CORNER, BridgeOrientation.HALF_DEPTH,
                end == 0 ? BridgeOrientation.CORNER : .5)
                : frame.point(pos, end == 0 ? -.5 : .5, BridgeOrientation.HALF_DEPTH, 0);
    }

    private BridgePlacement() { }
}
