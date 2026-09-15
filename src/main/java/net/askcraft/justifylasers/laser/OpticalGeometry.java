package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public final class OpticalGeometry {
    public static final double EPSILON = 0.0001;

    public static Vec3d reflect(Vec3d incoming, Vec3d normal) {
        Vec3d axis = incoming.normalize();
        Vec3d n = normal.normalize();
        return axis.subtract(n.multiply(2 * axis.dotProduct(n))).normalize();
    }

    public static Vec3d normal(double yaw, double pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vec3d(-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    @Nullable
    public static Vec3d aimMirror(Vec3d center, Vec3d start, Vec3d incoming, Vec3d target, double radius) {
        Vec3d axis = incoming.normalize();
        Vec3d closest = start.add(axis.multiply(center.subtract(start).dotProduct(axis)));
        double radialSquared = closest.squaredDistanceTo(center);
        if (radialSquared >= radius * radius) return null;
        double span = Math.sqrt(radius * radius - radialSquared);
        double low = -span, high = span;
        // Tilting moves an off-center hit. Solve the plane-through-pivot constraint at the new hit.
        for (int step = 0; step < 60; step++) {
            double midpoint = (low + high) * 0.5;
            Vec3d point = closest.add(axis.multiply(midpoint));
            Vec3d normal = axis.subtract(target.subtract(point).normalize());
            if (point.subtract(center).dotProduct(normal) > 0) high = midpoint;
            else low = midpoint;
        }
        Vec3d hit = closest.add(axis.multiply((low + high) * 0.5));
        Vec3d normal = axis.subtract(target.subtract(hit).normalize());
        if (normal.lengthSquared() < 1e-10 || Math.abs(hit.subtract(center).dotProduct(normal.normalize())) > 1e-7
                || hit.subtract(start).dotProduct(axis) < 0) return null;
        return normal.normalize();
    }

    public static Vec3d mounted(Vec3d point, Direction mount) {
        double x = point.x, y = point.y, z = point.z;
        return switch (mount) {
            case UP -> point;
            case DOWN -> new Vec3d(x, 1 - y, 1 - z);
            case NORTH -> new Vec3d(x, z, 1 - y);
            case SOUTH -> new Vec3d(x, 1 - z, y);
            case EAST -> new Vec3d(y, 1 - x, z);
            case WEST -> new Vec3d(1 - y, x, z);
        };
    }

    public static int mix(int first, int second) {
        // Normalize additive light instead of averaging sRGB channels into a dark pigment.
        double r = Math.pow((first >> 16 & 255) / 255.0, 2.2) + Math.pow((second >> 16 & 255) / 255.0, 2.2);
        double g = Math.pow((first >> 8 & 255) / 255.0, 2.2) + Math.pow((second >> 8 & 255) / 255.0, 2.2);
        double b = Math.pow((first & 255) / 255.0, 2.2) + Math.pow((second & 255) / 255.0, 2.2);
        double peak = Math.max(1, Math.max(r, Math.max(g, b)));
        return channel(r / peak) << 16 | channel(g / peak) << 8 | channel(b / peak);
    }

    private static int channel(double linear) {
        return (int) Math.round(255 * Math.pow(linear, 1 / 2.2));
    }

    private OpticalGeometry() { }
}
