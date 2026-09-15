package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChamberSeamsTest {
    @Test void chambersAndCasingsHaveNoCoplanarOverlaps() {
        for (boolean grower : new boolean[]{false,true}) for (boolean casing : new boolean[]{false,true})
            verify(ChamberModel.chassis(grower, casing), "grower=" + grower + ", casing=" + casing);
    }

    @Test void newLaserComponentsHaveSeparatedPanels() {
        for (String kind : new String[]{"optical_resonator","reinforced_laser_housing","energy_core","focusing_lens_assembly","beam_controller"})
            verify(LaserComponentRenderer.model(kind), kind);
    }

    @Test void tabletAndSchematicHaveNoOverlappingFaces() {
        verify(TabletRenderer.body(), "tablet");
        verify(BlueprintRenderer.card(), "schematic");
    }

    @Test void chamberDetailPanelsKeepTheTextureAspectRatio() {
        for (String kind : new String[]{"crystal_chamber", "assembly_chamber", "crystal_chamber_casing", "assembly_chamber_casing"}) {
            var builder = new OpticalComponentMesh.Builder(kind);
            builder.trimmedPanel("column", true, 0,0,1.6,22,0);
            builder.trimmedPanel("beam", true, 0,0,24,3,0);
            builder.trimmedPanel("joint", true, 0,0,2,2,0);
            for (var face : builder.build().faces()) {
                double a = Math.hypot(face.ua().u()-face.ub().u(), face.ua().v()-face.ub().v()) / face.a().distanceTo(face.b());
                double b = Math.hypot(face.ub().u()-face.uc().u(), face.ub().v()-face.uc().v()) / face.b().distanceTo(face.c());
                assertEquals(1, a/b, .10, kind + ": avoid stretched detail panels");
            }
        }
    }

    private static void verify(OpticalComponentMesh mesh, String name) {
        var faces = mesh.faces();
        var overlaps = new java.util.ArrayList<String>();
        for (int i = 0; i < faces.size(); i++) {
            var a = faces.get(i);
            assertTrue(Double.isFinite(a.normal().lengthSquared()), name);
            for (int j = i + 1; j < faces.size(); j++) {
                var b = faces.get(j);
                if (a.normal().dotProduct(b.normal()) < .999999 || Math.abs(a.normal().dotProduct(a.a().subtract(b.a()))) > 1e-7) continue;
                if (overlap(a, b)) overlaps.add(i + "/" + j + " at " + a.a() + " / " + b.a() + " normal=" + a.normal());
            }
        }
        assertTrue(overlaps.isEmpty(), name + ": " + overlaps.size() + " coplanar overlaps: " + overlaps.stream().limit(12).toList());
    }

    private static boolean overlap(OpticalComponentMesh.Face a, OpticalComponentMesh.Face b) {
        var aa = List.of(a.a(), a.b(), a.c(), a.d());
        var bb = List.of(b.a(), b.b(), b.c(), b.d());
        for (var polygon : List.of(aa, bb)) for (int i = 0; i < 4; i++) {
            Vec3d edge = polygon.get((i + 1) % 4).subtract(polygon.get(i));
            if (edge.lengthSquared() < 1e-12) continue;
            Vec3d axis = edge.crossProduct(a.normal()).normalize();
            double minA = aa.stream().mapToDouble(axis::dotProduct).min().orElseThrow(), maxA = aa.stream().mapToDouble(axis::dotProduct).max().orElseThrow();
            double minB = bb.stream().mapToDouble(axis::dotProduct).min().orElseThrow(), maxB = bb.stream().mapToDouble(axis::dotProduct).max().orElseThrow();
            if (Math.min(maxA, maxB) - Math.max(minA, minB) <= 1e-6) return false;
        }
        return true;
    }
}
