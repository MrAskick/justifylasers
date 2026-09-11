package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class LaserCrystalModelTest {
    @Test
    void crystalHasFiniteVolumetricGeometryAndAllFourMaterials() {
        var mesh = LaserCrystalModel.mesh();
        assertTrue(mesh.size() > 100 && mesh.size() < 1200);
        var materials = EnumSet.noneOf(LaserCrystalModel.Material.class);
        double minX = 16, minZ = 16, maxX = 0, maxZ = 0, maxY = 0;
        for (var face : mesh) {
            materials.add(face.material());
            assertEquals(1, face.normal().length(), 1e-7, "No degenerate or non-finite faces");
            assertTrue(face.b().subtract(face.a()).crossProduct(face.c().subtract(face.a())).dotProduct(face.normal()) > 0);
            for (Vec3d point : new Vec3d[]{face.a(), face.b(), face.c(), face.d()}) {
                assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z));
                assertTrue(point.x >= 0 && point.x <= 16 && point.y >= 0 && point.y <= 16 && point.z >= 0 && point.z <= 16);
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
                maxY = Math.max(maxY, point.y);
            }
        }
        assertEquals(EnumSet.allOf(LaserCrystalModel.Material.class), materials);
        assertTrue(maxX - minX > 10 && maxZ - minZ > 10, "Model must have width and depth, not a billboard");
        assertEquals(15.75, maxY);
    }

    @Test
    void pointedCrownUsesEightSolidFacetsWithOutwardNormals() {
        var crown = LaserCrystalModel.mesh().stream().filter(face -> face.material() == LaserCrystalModel.Material.CRYSTAL
                && face.b().y == 15.75).toList();
        assertEquals(8, crown.size());
        for (var face : crown) {
            assertTrue(face.triangle());
            assertEquals(face.c(), face.d());
            assertTrue(face.normal().y > 0);
            Vec3d outward = face.a().add(face.c()).multiply(0.5).subtract(8, 12.6, 8);
            assertTrue(face.normal().dotProduct(outward) > 0);
        }
    }
}
