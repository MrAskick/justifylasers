package net.askcraft.justifylasers.bridge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BridgePlacementTest {
    @Test void straightPanelContinuesEitherCornerArmInEveryProjectionDirection() {
        for (Direction facing : Direction.values()) for (int angle = 0; angle < 8; angle += 2) {
            var corner = new BridgeOrientation(facing, facing, false, angle);
            for (int arm = 0; arm < 2; arm++) {
                Vec3d endpoint = BridgePlacement.endpoint(corner, BlockPos.ORIGIN, true, arm);
                Vec3d edge = endpoint.subtract(Vec3d.ofCenter(BlockPos.ORIGIN));
                // Ignore the depth offset: a tunnel connects in its cross-section.
                edge = edge.subtract(Vec3d.of(facing.getVector()).multiply(edge.dotProduct(Vec3d.of(facing.getVector()))));
                Direction side = Direction.getFacing(edge.x, edge.y, edge.z);
                BlockPos pos = BlockPos.ORIGIN.offset(side);
                var selected = BridgePlacement.align(new BridgeOrientation(facing, Direction.UP), false, pos,
                        List.of(new BridgePlacement.Neighbor(BlockPos.ORIGIN, corner, true)));
                assertEquals(facing, selected.forward());
                assertTrue(BridgePlacement.endpoint(selected, pos, false, 0).squaredDistanceTo(endpoint) < 1e-10
                        || BridgePlacement.endpoint(selected, pos, false, 1).squaredDistanceTo(endpoint) < 1e-10,
                        "Straight emitter fails to join " + facing + "/" + angle + "/" + arm);
            }
        }
    }

    @Test void noNearbyApertureKeepsChosenMountAndRoll() {
        var preferred = new BridgeOrientation(Direction.UP, Direction.NORTH, true, 3);
        assertEquals(preferred, BridgePlacement.align(preferred, false, BlockPos.ORIGIN, List.of()));
        assertEquals(preferred, BridgePlacement.align(preferred, false, BlockPos.ORIGIN,
                List.of(new BridgePlacement.Neighbor(new BlockPos(9,8,7), preferred, false))));
    }
}
