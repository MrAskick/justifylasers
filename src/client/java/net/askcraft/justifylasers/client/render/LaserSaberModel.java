package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public final class LaserSaberModel {
    static final OpticalComponentMesh SINGLE = build(false), STAFF = build(true);

    public static void renderHilt(boolean staff, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        renderHilt(staff, matrices, consumers, light, 0xFFFFFF, false);
    }

    public static void renderHilt(boolean staff, MatrixStack matrices, VertexConsumerProvider consumers, int light, int rgb, boolean active) {
        (staff ? STAFF : SINGLE).render(matrices, consumers, light, rgb, active, true);
    }

    private static OpticalComponentMesh build(boolean staff) {
        String kind = staff ? "light_staff" : "laser_saber";
        var atlas = ComponentAtlas.load(kind, "base");
        List<OpticalComponentMesh.Face> faces = new ArrayList<>();
        if (staff) {
            cylinder(faces, atlas, "metal", -.80, .80, .57, .57);
            for (int side = 0; side < 8; side++) {
                double angle = side * Math.PI / 4;
                panel(faces, atlas, "control", angle, .585, -.66, .66, .20);
                if (side % 2 == 0) panel(faces, atlas, "light", angle, .590, -.42, .42, .038);
            }
            for (int sign : new int[]{-1, 1}) {
                cylinder(faces, atlas, "steel", sign * .80, sign * .98, .64, .55);
                cylinder(faces, atlas, "grip", sign * .98, sign * 4.15, .46, .46);
                for (int i = 0; i < 7; i++)
                    cylinder(faces, atlas, "grip", sign * (1.06 + i * .43), sign * (1.26 + i * .43), .59, .59);
                cylinder(faces, atlas, "steel", sign * 4.15, sign * 4.40, .57, .70);
                cylinder(faces, atlas, "vent", sign * 4.40, sign * 4.76, .64, .64);
                cylinder(faces, atlas, "steel", sign * 4.76, sign * 4.90, .65, .65);
                cylinder(faces, atlas, "grip", sign * 4.90, sign * 6.12, .43, .43);
                for (int side = 0; side < 8; side++) {
                    double angle = side * Math.PI / 4;
                    panel(faces, atlas, "shroud", angle, .59, sign * 4.90, sign * (side % 2 == 0 ? 6.33 : 5.98), .215);
                    panel(faces, atlas, "light", angle, .595, sign * 5.12, sign * 5.85, .045);
                }
            }
        } else {
            cylinder(faces, atlas, "grip", -2.70, -.40, .47, .47);
            for (int side = 0; side < 8; side++) {
                double angle = side * Math.PI / 4;
                panel(faces, atlas, "grip", angle, .535, -2.55, -.55, .16);
                if (side % 2 == 0) panel(faces, atlas, "light", angle, .540, -2.38, -.72, .018);
            }
            cylinder(faces, atlas, "steel", -2.82, -2.67, .58, .55);
            cylinder(faces, atlas, "pommel", -3.65, -2.82, .61, .61);
            cylinder(faces, atlas, "steel", -3.78, -3.65, .50, .61);
            cylinder(faces, atlas, "steel", -.42, -.23, .62, .58);
            cylinder(faces, atlas, "metal", -.23, 1.25, .55, .55);
            panel(faces, atlas, "control", 0, .67, -.07, 1.10, .26);
            panel(faces, atlas, "light", 0, .675, .05, .26, .06);
            cylinder(faces, atlas, "steel", 1.25, 1.40, .61, .61);
            cylinder(faces, atlas, "grip", 1.40, 1.94, .52, .52);
            for (int side = 0; side < 8; side++) panel(faces, atlas, "light", side * Math.PI / 4, .525, 1.49, 1.85, .043);
            cylinder(faces, atlas, "steel", 1.94, 2.09, .60, .60);
            cylinder(faces, atlas, "grip", 2.09, 2.38, .50, .66);
            cylinder(faces, atlas, "steel", 2.38, 2.54, .70, .70);
            cylinder(faces, atlas, "grip", 2.54, 3.60, .43, .43);
            for (int side = 0; side < 8; side++) {
                double angle = side * Math.PI / 4;
                panel(faces, atlas, "shroud", angle, .64, 2.54, side % 2 == 0 ? 3.93 : 3.43, .215);
                panel(faces, atlas, "light", angle, .645, 2.68, 3.31, .040);
            }
        }
        return new OpticalComponentMesh(kind, faces);
    }

    private static void cylinder(List<OpticalComponentMesh.Face> faces, ComponentAtlas atlas, String part,
                                 double from, double to, double r0, double r1) {
        if (from > to) { double y = from; from = to; to = y; double r = r0; r0 = r1; r1 = r; }
        var region = atlas.region(part);
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
            Vec3d p = point(r0, from, a), q = point(r1, to, a), r = point(r1, to, b), s = point(r0, from, b);
            face(faces, p, q, r, s, region.uv(i / (double) sides, 1), region.uv(i / (double) sides, 0),
                    region.uv((i + 1d) / sides, 0), region.uv((i + 1d) / sides, 1));
            for (int end : new int[]{0, 1}) {
                double radius = end == 0 ? r0 : r1, y = end == 0 ? from : to;
                Vec3d center = new Vec3d(0, y, 0), v = point(radius, y, end == 0 ? a : b), w = point(radius, y, end == 0 ? b : a);
                var uv = atlas.region("steel");
                face(faces, center, v, w, center, uv.uv(.5, .5), uv.uv((v.x / radius + 1) / 2, (v.z / radius + 1) / 2),
                        uv.uv((w.x / radius + 1) / 2, (w.z / radius + 1) / 2), uv.uv(.5, .5));
            }
        }
    }

    private static void panel(List<OpticalComponentMesh.Face> faces, ComponentAtlas atlas, String part,
                              double angle, double radius, double from, double to, double halfWidth) {
        if (from > to) { double y = from; from = to; to = y; }
        Vec3d normal = new Vec3d(Math.sin(angle), 0, -Math.cos(angle));
        Vec3d right = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
        Vec3d center = normal.multiply(radius);
        var uv = atlas.region(part);
        Vec3d a = center.subtract(right.multiply(halfWidth)).add(0, from, 0),
                b = center.subtract(right.multiply(halfWidth)).add(0, to, 0),
                c = center.add(right.multiply(halfWidth)).add(0, to, 0),
                d = center.add(right.multiply(halfWidth)).add(0, from, 0);
        face(faces, a, b, c, d, uv.uv(0, 1), uv.uv(0, 0), uv.uv(1, 0), uv.uv(1, 1));
        if (part.equals("light")) {
            var f = faces.remove(faces.size() - 1);
            faces.add(new OpticalComponentMesh.Face(f.a(), f.b(), f.c(), f.d(), f.normal(), f.ua(), f.ub(), f.uc(), f.ud(), true, 255));
        }
        // A shroud is a thin solid extrusion, not a one-sided floating texture.
        if (part.equals("shroud") || part.equals("control") || part.equals("grip")) {
            Vec3d offset = normal.multiply(-0.07);
            Vec3d[] edge = {a, b, c, d};
            face(faces, d.add(offset), c.add(offset), b.add(offset), a.add(offset), uv.uv(1, 1), uv.uv(1, 0), uv.uv(0, 0), uv.uv(0, 1));
            for (int i = 0; i < 4; i++) {
                Vec3d p = edge[i], q = edge[(i + 1) % 4];
                face(faces, p, p.add(offset), q.add(offset), q, uv.uv(0, 1), uv.uv(0, 0), uv.uv(1, 0), uv.uv(1, 1));
            }
        }
    }

    private static Vec3d point(double radius, double y, double angle) { return new Vec3d(Math.sin(angle) * radius, y, -Math.cos(angle) * radius); }

    private static void face(List<OpticalComponentMesh.Face> faces, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                             ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud) {
        Vec3d normal = b.subtract(a).crossProduct(c.subtract(a)).normalize();
        faces.add(new OpticalComponentMesh.Face(a.multiply(1 / 16d), b.multiply(1 / 16d), c.multiply(1 / 16d), d.multiply(1 / 16d),
                normal, ua, ub, uc, ud, false, 255));
    }

    private LaserSaberModel() { }
}
