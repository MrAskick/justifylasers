package net.askcraft.justifylasers.bridge;

import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightBridgeSpanTest {
    @Test void floorAndCeilingEmittersHaveTheirBacksAtTheBlockEdge() {
        for (Direction facing : Direction.Type.HORIZONTAL) for (Direction mount : new Direction[]{Direction.UP, Direction.DOWN})
            for (int rotation = 0; rotation < 8; rotation++) {
                var frame = new BridgeOrientation(facing, mount, false, rotation);
                var axis = net.minecraft.util.math.Vec3d.of(facing.getVector());
                var center = net.minecraft.util.math.Vec3d.ofCenter(BlockPos.ORIGIN);
                assertEquals(-.5, frame.point(BlockPos.ORIGIN, 0, -BridgeOrientation.HALF_DEPTH, 0).subtract(center).dotProduct(axis), 1e-9);
                assertEquals(-.5, frame.cornerPoint(BlockPos.ORIGIN, 0, -BridgeOrientation.HALF_DEPTH, 0).subtract(center).dotProduct(axis), 1e-9);
                double minimum = Double.POSITIVE_INFINITY;
                double inner = BridgeOrientation.CORNER + BridgeOrientation.HALF_HEIGHT;
                for (double[] p : new double[][]{{-.5, -.5}, {.5, -.5}, {.5, inner}, {inner, inner}, {inner, .5}, {-.5, .5}})
                    minimum = Math.min(minimum, frame.cornerPoint(BlockPos.ORIGIN, p[0], 0, p[1]).subtract(center)
                            .dotProduct(net.minecraft.util.math.Vec3d.of(mount.getVector())));
                assertEquals(-.5, minimum, 1e-9, "The actual L, not its empty quadrant, rests on the support");
            }
    }

    @Test void gridCenteredInputReachesHardwareAtEveryRollAndMount() {
        for (Direction facing : Direction.values()) for (Direction mount : Direction.values())
            for (boolean rolled : new boolean[]{false, true}) for (int angle = 0; angle < 8; angle++) {
                var frame = new BridgeOrientation(facing, mount, rolled, angle);
                var direction = net.minecraft.util.math.Vec3d.of(facing.getVector());
                var center = net.minecraft.util.math.Vec3d.ofCenter(BlockPos.ORIGIN);
                var start = center.subtract(direction.multiply(2));
                var end = center.add(direction.multiply(2));
                var body = BridgeGeometry.boxes(frame.point(BlockPos.ORIGIN, -.5, -BridgeOrientation.HALF_DEPTH, -BridgeOrientation.HALF_HEIGHT),
                        frame.acrossVector(), direction.multiply(2 * BridgeOrientation.HALF_DEPTH), frame.normalVector().multiply(2 * BridgeOrientation.HALF_HEIGHT));
                assertTrue(body.stream().anyMatch(box -> box.raycast(start, end).isPresent())
                        || frame.hasFeed() && frame.feed(BlockPos.ORIGIN).raycast(start, end).isPresent(),
                        "Grid input misses " + frame);
            }
    }

    @Test void diagonalSurfacesKeepTheirActualShapeInsteadOfFillingTheBoundingBox() {
        for (Direction facing : Direction.values()) for (Direction mount : Direction.values()) for (int angle = 0; angle < 8; angle++) {
            var span = new LightBridgeSpan(BlockPos.ORIGIN, facing, mount, false, angle, -1, 0, 1, 16, 0xFFFFFF, 32_000);
            var frame = span.orientation();
            assertEquals(1, frame.normalVector().length(), 1e-9);
            assertEquals(0, frame.normalVector().dotProduct(frame.acrossVector()), 1e-9);
            var bounds = frame.body(BlockPos.ORIGIN, 1);
            double edge = mount.getDirection() == Direction.AxisDirection.POSITIVE ? bounds.getMin(mount.getAxis()) : bounds.getMax(mount.getAxis());
            assertEquals(mount.getDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1, edge, 1e-8);
            var boxes = span.collisionBoxes();
            if (angle % 2 == 0) assertEquals(1, boxes.size());
            else assertTrue(boxes.size() >= 32);
            for (int i = 0; i <= 100; i++) {
                var center = span.point(.005 + i * .0099, 8, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS / 2);
                assertTrue(boxes.stream().anyMatch(box -> box.contains(center)), "Surface has a hole at " + facing + "/" + mount + "/" + angle);
                var air = center.add(span.normal().multiply(.13));
                assertFalse(boxes.stream().anyMatch(box -> box.contains(air)), "Empty space beside the diagonal must stay empty");
            }
        }
    }

    @Test void cornerFacesJoinOneAnotherAndTheTwoStraightNeighborsWithoutGaps() {
        var a = new LightBridgeSpan(BlockPos.ORIGIN, Direction.NORTH, Direction.UP, false, 0, 0, 3, 1, 8, 0, 32_000);
        var b = new LightBridgeSpan(BlockPos.ORIGIN, Direction.NORTH, Direction.UP, false, 0, 1, 3, 1, 8, 0, 32_000);
        var floor = new LightBridgeSpan(BlockPos.ORIGIN.east(), Direction.NORTH, Direction.UP, false, 0, -1, 1, 1, 8, 0, 16_000);
        var wall = new LightBridgeSpan(BlockPos.ORIGIN.up(), Direction.NORTH, Direction.EAST, false, 0, -1, 1, 1, 8, 0, 16_000);
        for (double distance : new double[]{0, 2, 8}) {
            assertEquals(0, a.point(0, distance, .5).distanceTo(b.point(0, distance, .5)), 1e-9);
            assertEquals(0, a.point(1, distance, .5).distanceTo(floor.point(0, distance, .5)), 1e-9);
            assertEquals(0, b.point(1, distance, .5).distanceTo(wall.point(0, distance, .5)), 1e-9);
        }
        var emptyQuadrant = new net.minecraft.util.math.Vec3d(.8, .8, -2);
        assertTrue(a.collisionBoxes().stream().noneMatch(box -> box.contains(emptyQuadrant)));
        assertTrue(b.collisionBoxes().stream().noneMatch(box -> box.contains(emptyQuadrant)));
    }
    @Test void samePowerSupportsTheSameAreaRatherThanThreeFreeBridges() {
        assertEquals(8, LightBridgeSpan.poweredLength(16_000, 1, 2_000, 64));
        assertEquals(40, LightBridgeSpan.poweredLength(80_000, 1, 2_000, 64));
        assertEquals(20, LightBridgeSpan.poweredLength(80_000, 2, 2_000, 64));
        assertEquals(13, LightBridgeSpan.poweredLength(80_000, 3, 2_000, 64));
        assertEquals(64, LightBridgeSpan.poweredLength(LuminousFlux.MAX, 3, 2_000, 64));
        assertEquals(0, LightBridgeSpan.poweredLength(1_999, 1, 2_000, 64));
    }

    @Test void collisionAndVisualCoordinatesAgreeForEveryHorizontalDirectionAndWidth() {
        for (var facing : Direction.Type.HORIZONTAL) for (int width = 1; width <= 3; width++) {
            var span = new LightBridgeSpan(new BlockPos(-19, 70, -34), facing, width, 17.5, 0x33EEFF, 300_000);
            var box = span.bounds();
            assertEquals(70 + BridgeOrientation.HALF_HEIGHT, box.maxY, 1e-9);
            assertEquals(LightBridgeSpan.THICKNESS, box.getYLength(), 1e-9);
            assertEquals(17.5 * (width - 2 * LightBridgeSpan.EDGE_INSET) * LightBridgeSpan.THICKNESS, box.getXLength() * box.getYLength() * box.getZLength(), 1e-9);
            assertFalse(box.intersects(span.hardwareBounds()), "Projection starts at the face, not inside the emitter");
            assertEquals(width * 2 * LightBridgeSpan.FRONT_OFFSET, span.hardwareBounds().getXLength() * span.hardwareBounds().getZLength(), 1e-9);
            assertEquals(17.5, span.point(.5, 0, .25).distanceTo(span.point(.5, span.length(), .25)), 1e-9);
        }
    }

    @Test void defaultRangeIs256AndLightStillSetsTheActualLength() {
        var config = new LaserConfig();
        assertEquals(256, config.lightBridgeMaxLength);
        assertEquals(480_000, config.crystalGrowthFlux);
        assertEquals(128, LightBridgeSpan.poweredLength(256_000, 1, 2_000, 256));
        assertEquals(256, LightBridgeSpan.poweredLength(512_000, 1, 2_000, 256));
        assertEquals(512, LightBridgeSpan.poweredLength(2_000_000, 1, 2_000, 512));
    }

    @Test void rejectsCorruptNetworkGeometryAndInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new LightBridgeSpan(BlockPos.ORIGIN, null, 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightBridgeSpan(BlockPos.ORIGIN, Direction.NORTH, 4, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LightBridgeSpan(BlockPos.ORIGIN, Direction.NORTH, 1, Double.NaN, 0, 1));
        LaserConfig config = new LaserConfig();
        config.validate();
        config.lightBridgeLumensPerBlock = 0;
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test void everyMountIsFlushAndEveryProjectionUsesTheSameOrientedBasis() {
        for (Direction facing : Direction.values()) for (Direction mount : Direction.values()) for (boolean rolled : new boolean[]{false, true}) {
            var frame = new BridgeOrientation(facing, mount, rolled);
            var normal = net.minecraft.util.math.Vec3d.of(frame.normal().getVector());
            var axis = net.minecraft.util.math.Vec3d.of(facing.getVector());
            assertEquals(0, axis.dotProduct(normal));
            var body = frame.body(BlockPos.ORIGIN, 1);
            double edge = mount.getDirection() == Direction.AxisDirection.POSITIVE
                    ? body.getMin(mount.getAxis()) : body.getMax(mount.getAxis());
            assertEquals(mount.getDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1, edge, 1e-10);
            var span = new LightBridgeSpan(BlockPos.ORIGIN, facing, mount, rolled, 3, 8 + frame.leadIn(), 0x00FFFF, 48_000);
            assertEquals(8.5, span.point(1.5, span.length(), .5).subtract(net.minecraft.util.math.Vec3d.ofCenter(BlockPos.ORIGIN)).dotProduct(axis), 1e-9);
            assertEquals((8 + frame.leadIn()) * 2.8 * LightBridgeSpan.THICKNESS,
                    span.bounds().getXLength() * span.bounds().getYLength() * span.bounds().getZLength(), 1e-9);
        }
    }
}
