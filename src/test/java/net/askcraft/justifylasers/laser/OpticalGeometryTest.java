package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpticalGeometryTest {
    @Test
    void reflectionPreservesAngleAndLength() {
        Vec3d incoming = new Vec3d(1, 0, 0);
        Vec3d normal = new Vec3d(1, 0, -1).normalize();
        Vec3d reflected = OpticalGeometry.reflect(incoming, normal);
        assertEquals(1, reflected.z, 1e-9);
        assertEquals(1, reflected.length(), 1e-9);
        assertEquals(-incoming.dotProduct(normal), reflected.dotProduct(normal), 1e-9);
        assertTrue(OpticalGeometry.reflect(reflected, normal).squaredDistanceTo(incoming) < 1e-9);
    }

    @Test
    void additiveColorsStayBrightAndIdenticalColorsStayUnchanged() {
        assertEquals(0xFF00FF, OpticalGeometry.mix(0xFF0000, 0x0000FF));
        assertEquals(0xFFFF00, OpticalGeometry.mix(0xFF0000, 0x00FF00));
        assertEquals(0xFFFFFF, OpticalGeometry.mix(0xFF0000, 0x00FFFF));
        for (LaserColor color : LaserColor.values()) {
            assertEquals(color.rgb(), OpticalGeometry.mix(color.rgb(), color.rgb()));
            for (LaserColor other : LaserColor.values()) {
                assertEquals(OpticalGeometry.mix(color.rgb(), other.rgb()), OpticalGeometry.mix(other.rgb(), color.rgb()));
            }
        }
    }

    @Test
    void crystalTipFollowsTheMountingSurface() {
        for (Direction direction : Direction.values()) {
            Vec3d tip = OpticalGeometry.mounted(new Vec3d(0.5, 1, 0.5), direction).subtract(0.5, 0.5, 0.5);
            assertTrue(tip.squaredDistanceTo(Vec3d.of(direction.getVector()).multiply(0.5)) < 1e-9);
        }
    }

    @Test
    void preciseAimingAccountsForHitsAwayFromTheMirrorPivot() {
        Vec3d axis = new Vec3d(1, 0, 0), target = new Vec3d(1, 0.2, 4);
        for (Vec3d start : new Vec3d[]{new Vec3d(-3, 0, 0), new Vec3d(-3, 0.1, 0.08), new Vec3d(-3, -0.2, 0.15)}) {
            Vec3d normal = OpticalGeometry.aimMirror(Vec3d.ZERO, start, axis, target, 0.36);
            assertNotNull(normal);
            Vec3d hit = start.add(axis.multiply(-start.dotProduct(normal) / axis.dotProduct(normal)));
            assertTrue(hit.length() <= 0.36);
            assertTrue(OpticalGeometry.reflect(axis, normal).dotProduct(target.subtract(hit).normalize()) > 0.999999);
        }
        assertNull(OpticalGeometry.aimMirror(Vec3d.ZERO, new Vec3d(-3, 1, 0), axis, target, 0.36));
        assertNull(OpticalGeometry.aimMirror(Vec3d.ZERO, new Vec3d(-3, 0, 0), axis, axis.multiply(4), 0.36));
    }
}
