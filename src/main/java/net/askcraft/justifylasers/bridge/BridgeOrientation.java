package net.askcraft.justifylasers.bridge;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** The mounting face anchors the casing; forward and normal orient the light sheet. */
public record BridgeOrientation(Direction forward, Direction mount, boolean rolled, int rotation) {
    public BridgeOrientation(Direction forward, Direction mount, boolean rolled) { this(forward, mount, rolled, 0); }
    public BridgeOrientation(Direction forward, Direction mount) { this(forward, mount, false); }
    public static final double HALF_HEIGHT = 3.15 / 16;
    public static final double HALF_DEPTH = 3.15 / 16;
    public static final double CORNER = HALF_HEIGHT - .5;

    public BridgeOrientation {
        if (forward == null || mount == null || rotation < 0 || rotation > 7) throw new IllegalArgumentException("Invalid bridge orientation");
    }

    private Vec3d rotate(Vec3d vector) {
        double angle = rotation * Math.PI / 4;
        return vector.multiply(Math.cos(angle)).add(Vec3d.of(forward.getVector()).crossProduct(vector).multiply(Math.sin(angle)));
    }

    public Vec3d normalVector() { return rotate(Vec3d.of(normal().getVector())); }
    public Vec3d acrossVector() { return rotate(Vec3d.of(across().getVector())); }
    public Direction gridAcross() {
        Vec3d across = acrossVector();
        return Direction.getFacing(across.x, across.y, across.z);
    }

    public Direction normal() {
        Direction normal = mount.getAxis() != forward.getAxis() ? mount
                : forward.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        if (!rolled) return normal;
        for (Direction.Axis axis : Direction.Axis.values())
            if (axis != forward.getAxis() && axis != normal.getAxis()) return Direction.from(axis, Direction.AxisDirection.POSITIVE);
        throw new IllegalStateException("Parallel bridge axes");
    }

    public Direction across() {
        for (Direction.Axis axis : Direction.Axis.values())
            if (axis != forward.getAxis() && axis != normal().getAxis())
                return Direction.from(axis, Direction.AxisDirection.POSITIVE);
        throw new IllegalStateException("Parallel bridge axes");
    }

    public Vec3d center(BlockPos origin) {
        Vec3d side = Vec3d.of(mount.getVector());
        double extent = mount.getAxis() == forward.getAxis() ? HALF_DEPTH
                : Math.abs(acrossVector().dotProduct(side)) * .5 + Math.abs(normalVector().dotProduct(side)) * HALF_HEIGHT;
        return anchorDepth(Vec3d.ofCenter(origin).add(side.multiply(extent - .5)));
    }

    public Vec3d cornerCenter(BlockPos origin) {
        if (mount.getAxis() == forward.getAxis()) return center(origin);
        Vec3d side = Vec3d.of(mount.getVector());
        double across = acrossVector().dotProduct(side), normal = normalVector().dotProduct(side);
        double inner = CORNER + HALF_HEIGHT;
        double minimum = Double.POSITIVE_INFINITY;
        for (double[] vertex : new double[][]{{-.5, -.5}, {.5, -.5}, {.5, inner},
                {inner, inner}, {inner, .5}, {-.5, .5}})
            minimum = Math.min(minimum, vertex[0] * across + vertex[1] * normal);
        return anchorDepth(Vec3d.ofCenter(origin).add(side.multiply(-.5 - minimum)));
    }

    private Vec3d anchorDepth(Vec3d center) {
        // Floor/ceiling mounting anchors the rear edge as well as the supporting face.
        return mount.getAxis() == forward.getAxis() ? center
                : center.add(Vec3d.of(forward.getVector()).multiply(HALF_DEPTH - .5));
    }

    public Vec3d cornerPoint(BlockPos origin, double across, double forward, double normal) {
        return cornerCenter(origin).add(acrossVector().multiply(across)).add(Vec3d.of(this.forward.getVector()).multiply(forward))
                .add(normalVector().multiply(normal));
    }

    public Vec3d point(BlockPos origin, double across, double forward, double normal) {
        return center(origin).add(acrossVector().multiply(across))
                .add(Vec3d.of(this.forward.getVector()).multiply(forward))
                .add(normalVector().multiply(normal));
    }

    public Box casing(BlockPos origin, int width) {
        Box box = body(origin, width);
        if (hasFeed()) for (int i = 0; i < width; i++) box = box.union(feed(origin.offset(gridAcross(), i)));
        return box;
    }

    public Box body(BlockPos origin, int width) {
        return BridgeGeometry.bounds(point(origin, -.5, -HALF_DEPTH, -HALF_HEIGHT), acrossVector().multiply(width),
                Vec3d.of(forward.getVector()).multiply(2 * HALF_DEPTH), normalVector().multiply(2 * HALF_HEIGHT));
    }

    public boolean hasFeed() { return Math.abs(normalVector().dotProduct(Vec3d.of(mount.getVector()))) > .999999; }

    public Box feed(BlockPos origin) {
        // A small intake keeps grid-centred input beams connected to a floor/ceiling fixture.
        double sign = Math.signum(normalVector().dotProduct(Vec3d.of(mount.getVector())));
        return new Box(point(origin, -.08, -.13, sign * HALF_HEIGHT), point(origin, .08, .13, sign * (.56 - HALF_HEIGHT)));
    }

    public double leadIn() {
        double offset = center(BlockPos.ORIGIN).subtract(.5, .5, .5).dotProduct(Vec3d.of(forward.getVector()));
        return .5 - offset - HALF_DEPTH;
    }
}
