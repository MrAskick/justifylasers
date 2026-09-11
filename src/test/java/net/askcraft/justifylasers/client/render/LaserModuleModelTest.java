package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class LaserModuleModelTest {
    @Test
    void everyModuleHasItsOwnSolidMeshWithValidNormalsAndBounds() {
        var meshes = LaserModuleModel.meshes();
        assertEquals(10, meshes.size());
        var unique = new HashSet<>();
        for (var entry : meshes.entrySet()) {
            String id = entry.getKey();
            var mesh = entry.getValue();
            assertTrue(unique.add(mesh), "Modules must have distinct geometry: " + id);
            assertTrue(mesh.size() > 100 && mesh.size() < 2000, id + ": " + mesh.size());
            var materials = EnumSet.noneOf(LaserModuleModel.Material.class);
            double minX = 16, minY = 16, minZ = 16, maxX = 0, maxY = 0, maxZ = 0;
            for (var face : mesh) {
                materials.add(face.material());
                assertEquals(1, face.normal().length(), 1e-7, "Degenerate face: " + id);
                assertTrue(face.b().subtract(face.a()).crossProduct(face.c().subtract(face.a())).dotProduct(face.normal()) > 0, id);
                for (Vec3d p : new Vec3d[]{face.a(), face.b(), face.c(), face.d()}) {
                    assertTrue(p.x >= 0 && p.x <= 16 && p.y >= 0 && p.y <= 16 && p.z >= 0 && p.z <= 16,
                            id + " extends beyond its block: " + p);
                    minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                    minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
                    minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
                }
            }
            assertTrue(materials.contains(LaserModuleModel.Material.FRAME) && materials.contains(LaserModuleModel.Material.STEEL), id);
            assertTrue(materials.stream().anyMatch(material -> material.emissive), id);
            assertTrue(maxX - minX >= 9 && maxZ - minZ >= 9 && maxY - minY > 2, "Must be volumetric: " + id);
        }
    }
}
