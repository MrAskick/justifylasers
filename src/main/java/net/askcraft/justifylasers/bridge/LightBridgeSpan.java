package net.askcraft.justifylasers.bridge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** One continuous field, with its origin at the first emitter in world-axis order. */
public record LightBridgeSpan(BlockPos origin, Direction facing, Direction mount, boolean rolled, int rotation,
                              int section, int connections, int width, double length, int rgb, long lumens) {
    public LightBridgeSpan(BlockPos origin, Direction facing, Direction mount, boolean rolled, int width, double length, int rgb, long lumens) {
        this(origin, facing, mount, rolled, 0, -1, 0, width, length, rgb, lumens);
    }
    public LightBridgeSpan(BlockPos origin, Direction facing, Direction mount, int width, double length, int rgb, long lumens) {
        this(origin, facing, mount, false, width, length, rgb, lumens);
    }
    public static final double HEIGHT = .5;
    public static final double THICKNESS = .0625;
    public static final double EDGE_INSET = .10;
    public static final double FRONT_OFFSET = BridgeOrientation.HALF_DEPTH;
    public static final int MAX_WIDTH = 3;
    public static final int MAX_LENGTH = 512;

    public LightBridgeSpan(BlockPos origin, Direction facing, int width, double length, int rgb, long lumens) {
        this(origin, facing, Direction.UP, width, length, rgb, lumens);
    }

    public LightBridgeSpan {
        if (facing == null || mount == null || width < 1 || width > MAX_WIDTH || rotation < 0 || rotation > 7
                || section < -1 || section > 1 || section >= 0 && width != 1 || connections < 0 || connections > 3
                || !Double.isFinite(length) || length < 0 || length > MAX_LENGTH + 1 || lumens < 0)
            throw new IllegalArgumentException("Invalid hard light field");
        origin = origin.toImmutable();
        rgb &= 0xFFFFFF;
    }

    public static Direction across(Direction facing) {
        return new BridgeOrientation(facing, Direction.UP).across();
    }

    public BridgeOrientation orientation() { return new BridgeOrientation(facing, mount, rolled, rotation); }
    public Direction across() { return orientation().gridAcross(); }
    public Vec3d normal() { return section == 1 ? orientation().acrossVector() : orientation().normalVector(); }
    public boolean corner() { return section >= 0; }
    public boolean joined(int end) { return (connections & (1 << end)) != 0; }

    public boolean active() { return length > 0; }

    public BlockPos emitter(int index) { return origin.offset(across(), index); }

    public Vec3d point(double across, double distance, double height) {
        if (corner()) {
            double end = .5 - (joined(section) ? 0 : EDGE_INSET);
            double lateral = BridgeOrientation.CORNER + across * (end - BridgeOrientation.CORNER);
            double depth = BridgeOrientation.CORNER + height - .5;
            return orientation().cornerPoint(origin, section == 0 ? lateral : depth, FRONT_OFFSET + distance,
                    section == 0 ? depth : lateral);
        }
        double inset0 = joined(0) ? 0 : EDGE_INSET, inset1 = joined(1) ? 0 : EDGE_INSET;
        double lateral = inset0 + across * (width - inset0 - inset1) / width - .5;
        return orientation().point(origin, lateral, FRONT_OFFSET + distance, height - .5);
    }

    public Box bounds() {
        return BridgeGeometry.bounds(point(0, 0, HEIGHT), point(width, 0, HEIGHT).subtract(point(0, 0, HEIGHT)),
                Vec3d.of(facing.getVector()).multiply(length), normal().multiply(-THICKNESS));
    }

    public java.util.List<Box> collisionBoxes() {
        return BridgeGeometry.boxes(point(0, 0, HEIGHT), point(width, 0, HEIGHT).subtract(point(0, 0, HEIGHT)),
                Vec3d.of(facing.getVector()).multiply(length), normal().multiply(-THICKNESS));
    }

    public LightBridgeSpan withLength(double clipped) {
        return new LightBridgeSpan(origin, facing, mount, rolled, rotation, section, connections, width, clipped, rgb, lumens);
    }

    public Box hardwareBounds() {
        return corner() ? BridgeGeometry.bounds(orientation().cornerPoint(origin, -.5, -FRONT_OFFSET, -.5),
                orientation().acrossVector(), Vec3d.of(facing.getVector()).multiply(2 * FRONT_OFFSET), orientation().normalVector())
                : orientation().casing(origin, width);
    }

    public static int poweredLength(long lumens, int width, long lumensPerBlock, int maximum) {
        return (int) Math.min(maximum, lumens / width / lumensPerBlock);
    }
}
