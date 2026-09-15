package net.askcraft.justifylasers.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaserGunModelTest {
    @Test
    void gunHasDetailedClosedSurfacesAndFiniteOutwardNormals() {
        var mesh = LaserGunModel.mesh();
        assertTrue(mesh.size() > 1000 && mesh.size() < 12000);
        var materials = EnumSet.noneOf(LaserGunModel.Material.class);
        for (var face : mesh) {
            materials.add(face.material());
            assertEquals(1, face.normal().length(), 1e-6);
            assertTrue(face.b().subtract(face.a()).crossProduct(face.c().subtract(face.a())).dotProduct(face.normal()) > 0);
            for (var point : List.of(face.a(), face.b(), face.c(), face.d())) {
                assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z));
                assertTrue(point.length() < 40);
            }
        }
        assertEquals(EnumSet.allOf(LaserGunModel.Material.class), materials);
    }

    @Test
    void everyItemFitsTheSlotUnderEveryRotation() {
        var vertices = LaserGunModel.mesh().stream().flatMap(face -> List.of(face.a(), face.b(), face.c(), face.d()).stream()).toList();
        var bounds = ItemModelBounds.of(vertices);
        MatrixStack matrices = new MatrixStack();
        bounds.fitGui(matrices);
        for (Vec3d point : vertices) {
            Vector3f result = matrices.peek().getPositionMatrix().transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
            assertTrue(result.distance(0.5F, 0.5F, 0.5F) <= 0.46001F);
        }
        for (var mesh : LaserModuleModel.meshes().values()) {
            var points = mesh.stream().flatMap(face -> List.of(face.a(), face.b(), face.c(), face.d()).stream()).toList();
            var moduleBounds = ItemModelBounds.of(points);
            assertTrue(moduleBounds.radius() > 0);
            assertTrue(points.stream().allMatch(point -> point.distanceTo(moduleBounds.center()) <= moduleBounds.radius()));
        }
    }
}
