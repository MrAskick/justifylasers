package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.OpticPortMode;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpticalSurfaceRegressionTest {
    @Test
    void cubeEmissionIsConfinedToContinuousModeledChannels() throws Exception {
        var atlas = ComponentAtlas.load("refocusing_cube", "base");
        var channel = atlas.region("light");
        try (var stream = getClass().getResourceAsStream("/assets/justifylasers/textures/component/refocusing_cube/glow.png")) {
            var mask = ImageIO.read(stream);
            for (var region : atlas.regions().values()) for (int y = region.y(); y < region.y() + region.height(); y++)
                for (int x = region.x(); x < region.x() + region.width(); x++) {
                    int pixel = mask.getRGB(x, y);
                    assertEquals(region == channel ? 255 : 0, pixel >>> 24, "Only the clean light channel may be colored");
                    if (region == channel) assertEquals(0xFFEEEEEE, pixel, "No holes, cyan remnants or mottled intensity");
                }
        }
        var lit = RefocusingCubeChassis.MESH.faces().stream().filter(OpticalComponentMesh.Face::glowing).toList();
        assertEquals(6 * (48 + 4) + 2, lit.size(), "Six rings, four guides per face, two output indicators");
        for (var face : lit) assertTrue(List.of(face.ua(), face.ub(), face.uc(), face.ud()).stream().allMatch(channel::contains));
        for (Direction side : Direction.values()) {
            Vec3d normal = Vec3d.of(side.getVector());
            var ring = lit.stream().filter(f -> f.normal().dotProduct(normal) > 0.999999
                    && Math.abs(f.a().dotProduct(normal) - 7.085 / 16) < 1e-8).toList();
            assertEquals(48, ring.size(), "Complete ring on " + side);
            for (int i = 0; i < ring.size(); i++) {
                var a = ring.get(i); var next = ring.get((i + 1) % ring.size());
                assertEquals(0, a.b().squaredDistanceTo(next.a()), 1e-15, "Inner edge seam");
                assertEquals(0, a.c().squaredDistanceTo(next.d()), 1e-15, "Outer edge seam");
                for (var p : List.of(a.a(), a.b(), a.c(), a.d())) {
                    double radius = p.subtract(normal.multiply(p.dotProduct(normal))).length();
                    assertTrue(Math.abs(radius - 5.18 / 16) < 1e-8 || Math.abs(radius - 5.39 / 16) < 1e-8);
                }
            }
        }
        assertTrue(RefocusingCubeChassis.MESH.faces().stream().allMatch(f -> f.alpha() == 255),
                "Shader G-buffer must not contain a dark translucent cover over the core");
    }

    @Test
    void allSixSplitterPortsHaveNoCoplanarSurfaceOverlaps() {
        for (int layout = 0; layout < 6; layout++) {
            var faces = new ArrayList<>(BeamSplitterModel.CHASSIS.faces());
            for (Direction side : Direction.values()) {
                var mode = OpticPortMode.values()[layout < 3 ? layout : (layout + side.ordinal()) % 3];
                faces.addAll(BeamSplitterModel.PORTS.get(side).get(mode).faces());
            }
            var collisions = new ArrayList<String>();
            for (int i = 0; i < faces.size(); i++) for (int j = i + 1; j < faces.size(); j++) {
                var a = faces.get(i); var b = faces.get(j);
                if (a.normal().dotProduct(b.normal()) < 0.999999
                        || Math.abs(a.normal().dotProduct(a.a().subtract(b.a()))) > 1e-8) continue;
                if (overlap(a, b)) collisions.add(a.a() + " / " + a.c() + " overlaps " + b.a() + " / " + b.c());
            }
            assertTrue(collisions.isEmpty(), "Layout " + layout + ": " + collisions.size() + " coplanar overlaps: " + collisions.stream().limit(10).toList());
        }
    }

    private static boolean overlap(OpticalComponentMesh.Face a, OpticalComponentMesh.Face b) {
        var pa = List.of(a.a(), a.b(), a.c(), a.d());
        var pb = List.of(b.a(), b.b(), b.c(), b.d());
        for (var polygon : List.of(pa, pb)) for (int i = 0; i < 4; i++) {
            Vec3d edge = polygon.get((i + 1) % 4).subtract(polygon.get(i));
            if (edge.lengthSquared() < 1e-16) continue;
            Vec3d axis = a.normal().crossProduct(edge).normalize();
            double minA = pa.stream().mapToDouble(axis::dotProduct).min().orElseThrow();
            double maxA = pa.stream().mapToDouble(axis::dotProduct).max().orElseThrow();
            double minB = pb.stream().mapToDouble(axis::dotProduct).min().orElseThrow();
            double maxB = pb.stream().mapToDouble(axis::dotProduct).max().orElseThrow();
            if (Math.min(maxA, maxB) - Math.max(minA, minB) <= 1e-8) return false;
        }
        return true;
    }
}
