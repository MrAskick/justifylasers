package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SaberGeometry {
    public record Segment(Vec3d start, Vec3d end) {
        public Vec3d at(double t) { return start.lerp(end, t); }
    }

    public static boolean faces(Vec3d forward, Vec3d towards, double arcDegrees) {
        return towards.lengthSquared() < 1e-10 || forward.normalize().dotProduct(towards.normalize())
                >= Math.cos(Math.toRadians(arcDegrees * .5));
    }

    public static Vec3d contact(Segment first, Segment second, double radius) {
        Vec3d u = first.end.subtract(first.start), v = second.end.subtract(second.start), w = first.start.subtract(second.start);
        double a = u.dotProduct(u), b = u.dotProduct(v), c = v.dotProduct(v), d = u.dotProduct(w), e = v.dotProduct(w);
        if (a < 1e-10 || c < 1e-10) return null;
        double denominator = a * c - b * b;
        double s = denominator > 1e-10 ? MathHelper.clamp((b * e - c * d) / denominator, 0, 1) : 0;
        double t = (b * s + e) / c;
        if (t < 0) { t = 0; s = MathHelper.clamp(-d / a, 0, 1); }
        else if (t > 1) { t = 1; s = MathHelper.clamp((b - d) / a, 0, 1); }
        Vec3d p = first.at(s), q = second.at(t);
        return p.squaredDistanceTo(q) <= radius * radius ? p.lerp(q, .5) : null;
    }

    private SaberGeometry() { }
}
