package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class CubeOptics {
    public static final double HALF_SIZE = 0.45D;
    private static final Box LOCAL_BOX = new Box(-HALF_SIZE, -HALF_SIZE, -HALF_SIZE,
            HALF_SIZE, HALF_SIZE, HALF_SIZE);

    public static Frame frame(Vec3d center, float yaw, float pitch) {
        double radians = Math.toRadians(yaw);
        double tilt = Math.toRadians(pitch);
        Vec3d right = new Vec3d(clean(Math.cos(radians)), 0, clean(Math.sin(radians)));
        Vec3d forward = new Vec3d(clean(-Math.sin(radians) * Math.cos(tilt)), clean(-Math.sin(tilt)),
                clean(Math.cos(radians) * Math.cos(tilt)));
        return new Frame(center, right, forward.crossProduct(right).normalize(), forward);
    }

    private static double clean(double value) {
        return Math.abs(value) < 1.0E-12D ? 0 : value;
    }

    public record Frame(Vec3d center, Vec3d right, Vec3d up, Vec3d forward) {
        public Vec3d toWorld(Vec3d local) {
            return center.add(right.multiply(local.x)).add(up.multiply(local.y)).add(forward.multiply(local.z));
        }

        public Vec3d toLocal(Vec3d world) {
            Vec3d offset = world.subtract(center);
            return new Vec3d(offset.dotProduct(right), offset.dotProduct(up), offset.dotProduct(forward));
        }

        public Vec3d halfExtents() {
            return new Vec3d(Math.abs(right.x) + Math.abs(up.x) + Math.abs(forward.x),
                    Math.abs(right.y) + Math.abs(up.y) + Math.abs(forward.y),
                    Math.abs(right.z) + Math.abs(up.z) + Math.abs(forward.z)).multiply(HALF_SIZE);
        }

        public Vec3d output() {
            return center.add(forward.multiply(HALF_SIZE + 0.002D));
        }

        @Nullable
        public Hit intersect(Vec3d start, Vec3d end) {
            Vec3d localStart = toLocal(start);
            if (LOCAL_BOX.contains(localStart)) {
                return new Hit(start, null);
            }
            return LOCAL_BOX.raycast(localStart, toLocal(end)).map(local -> {
                double x = Math.abs(local.x);
                double y = Math.abs(local.y);
                double z = Math.abs(local.z);
                Direction side = x >= y && x >= z ? (local.x < 0 ? Direction.WEST : Direction.EAST)
                        : y >= z ? (local.y < 0 ? Direction.DOWN : Direction.UP)
                        : local.z < 0 ? Direction.NORTH : Direction.SOUTH;
                return new Hit(toWorld(local), side);
            }).orElse(null);
        }
    }

    public record Hit(Vec3d position, @Nullable Direction side) {
        public boolean acceptsInput() {
            return side != null && side != Direction.SOUTH;
        }
    }

    private CubeOptics() {
    }
}
