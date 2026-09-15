package net.askcraft.justifylasers.client.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaserSaberModelTest {
    @Test void emissiveFacesUseOnlyTheDedicatedColorableLightRegion() {
        for (var mesh : List.of(LaserSaberModel.SINGLE, LaserSaberModel.STAFF)) {
            var atlas = ComponentAtlas.load(mesh == LaserSaberModel.SINGLE ? "laser_saber" : "light_staff", "base");
            var light = atlas.region("light");
            assertTrue(mesh.faces().stream().anyMatch(OpticalComponentMesh.Face::glowing));
            for (var face : mesh.faces()) if (face.glowing())
                assertTrue(List.of(face.ua(), face.ub(), face.uc(), face.ud()).stream().allMatch(light::contains),
                        "Colored emission cannot spill onto grip or casing UVs");
        }
    }

    @Test void centerAndNeckLightsAreOutsideTheirOpaqueSleeves() {
        for (var mesh : List.of(LaserSaberModel.SINGLE, LaserSaberModel.STAFF))
            for (var face : mesh.faces()) if (face.glowing()) {
                var center = face.a().add(face.b()).add(face.c()).add(face.d()).multiply(.25);
                if (mesh == LaserSaberModel.STAFF && Math.abs(center.y) < .7 / 16)
                    assertTrue(center.horizontalLength() > .57 / 16, "Staff controls must not sink into the center sleeve");
                if (mesh == LaserSaberModel.SINGLE && center.y > 1.49 / 16 && center.y < 1.85 / 16)
                    assertTrue(center.horizontalLength() > .52 / 16, "Neck lights must clear the opaque collar");
            }
    }

    @Test void hiltsHaveFiniteUnitNormalsAndComponentLocalUvs() {
        for (var mesh : List.of(LaserSaberModel.SINGLE, LaserSaberModel.STAFF)) {
            assertTrue(mesh.faces().size() < 4000, "Bounded geometry budget");
            var atlas = ComponentAtlas.load(mesh == LaserSaberModel.SINGLE ? "laser_saber" : "light_staff", "base");
            assertEquals(512, atlas.size());
            for (var face : mesh.faces()) {
                assertEquals(1, face.normal().length(), 1e-6);
                assertTrue(atlas.regions().values().stream().anyMatch(region ->
                        List.of(face.ua(), face.ub(), face.uc(), face.ud()).stream().allMatch(region::contains)), "Every face stays inside one cropped component");
                for (var vertex : List.of(face.a(), face.b(), face.c(), face.d())) {
                    assertTrue(Double.isFinite(vertex.x) && Double.isFinite(vertex.y) && Double.isFinite(vertex.z));
                    assertTrue(Math.abs(vertex.x) < 0.06 && Math.abs(vertex.z) < 0.06 && Math.abs(vertex.y) < 0.4);
                }
            }
        }
    }
}
