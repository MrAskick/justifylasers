package net.askcraft.justifylasers.bridge;

import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgeShapeTest {
    @Test void batchedUnionPreservesEverySliceOfStraightAndCornerCasings() {
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.UP)) {
            for (int rotation = 0; rotation < 8; rotation++) for (boolean corner : new boolean[]{false, true}) {
                var frame = new BridgeOrientation(facing, facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP, false, rotation);
                var across = frame.acrossVector();
                var normal = frame.normalVector();
                var forward = Vec3d.of(facing.getVector()).multiply(2 * BridgeOrientation.HALF_DEPTH);
                double height = 2 * BridgeOrientation.HALF_HEIGHT;
                var boxes = new ArrayList<Box>();
                var start = corner ? frame.cornerPoint(BlockPos.ORIGIN, -.5, -BridgeOrientation.HALF_DEPTH, -.5)
                        : frame.point(BlockPos.ORIGIN, -.5, -BridgeOrientation.HALF_DEPTH, -BridgeOrientation.HALF_HEIGHT);
                boxes.addAll(BridgeGeometry.boxes(start, across, forward, normal.multiply(height)));
                if (corner) {
                    boxes.addAll(BridgeGeometry.boxes(start.add(normal.multiply(height)), normal.multiply(1 - height), forward, across.multiply(height)));
                    boxes.add(BridgeGeometry.bounds(frame.cornerPoint(BlockPos.ORIGIN, -.12, -BridgeOrientation.HALF_DEPTH, -.12),
                            across.multiply(.24), Vec3d.of(facing.getVector()).multiply(.13), normal.multiply(.24)));
                } else if (frame.hasFeed()) boxes.add(frame.feed(BlockPos.ORIGIN));
                var original = VoxelShapes.empty();
                for (var box : boxes) original = VoxelShapes.union(original, VoxelShapes.cuboid(box));
                var optimized = BridgeGeometry.union(boxes);
                assertFalse(VoxelShapes.matchesAnywhere(original, optimized, BooleanBiFunction.NOT_SAME),
                        "Geometry changed: " + facing + "/" + rotation + "/corner=" + corner);
                Collections.reverse(boxes);
                assertFalse(VoxelShapes.matchesAnywhere(optimized, BridgeGeometry.union(boxes), BooleanBiFunction.NOT_SAME),
                        "Cache-key ordering must not change occupied space");
            }
        }
    }

    @Test void emptyAndSingleBoxUnionsKeepTheirBounds() {
        assertTrue(BridgeGeometry.union(List.of()).isEmpty());
        var box = new Box(-.2, 0, .7, .4, .15, 1.1);
        assertEquals(box, BridgeGeometry.union(List.of(box)).getBoundingBox());
    }
}
