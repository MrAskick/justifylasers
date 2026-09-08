package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CubeOpticsTest {
    @Test
    void fiveInputFacesAndOneOutputAtArbitraryRotations() {
        for (float yaw : new float[]{0, 45, 90, -137, 180}) {
            for (float pitch : new float[]{-90, -28, 0, 61, 90}) {
                CubeOptics.Frame frame = CubeOptics.frame(new Vec3d(12.4D, 31.2D, -16.8D), yaw, pitch);
                for (Direction face : Direction.values()) {
                    Vec3d direction = Vec3d.of(face.getVector());
                    CubeOptics.Hit hit = frame.intersect(frame.toWorld(direction.multiply(3)), frame.center());
                    assertNotNull(hit, "Missed face at yaw " + yaw + ", pitch " + pitch);
                    assertEquals(face, hit.side());
                    assertEquals(face != Direction.SOUTH, hit.acceptsInput());
                    assertEquals(CubeOptics.HALF_SIZE, hit.position().distanceTo(frame.center()), 1.0E-6D);
                }
            }
        }
    }

    @Test
    void transformRoundTripAndCollisionBoundsEncloseEveryCorner() {
        CubeOptics.Frame frame = CubeOptics.frame(new Vec3d(100, -15, 20), 37.0F, -42.0F);
        Vec3d half = frame.halfExtents();
        for (int x : new int[]{-1, 1}) {
            for (int y : new int[]{-1, 1}) {
                for (int z : new int[]{-1, 1}) {
                    Vec3d local = new Vec3d(x, y, z).multiply(CubeOptics.HALF_SIZE);
                    Vec3d world = frame.toWorld(local);
                    assertTrue(frame.toLocal(world).distanceTo(local) < 1.0E-6D);
                    Vec3d delta = world.subtract(frame.center());
                    assertTrue(Math.abs(delta.x) <= half.x + 1.0E-6D);
                    assertTrue(Math.abs(delta.y) <= half.y + 1.0E-6D);
                    assertTrue(Math.abs(delta.z) <= half.z + 1.0E-6D);
                }
            }
        }
    }

    @Test
    void insideAndGrazingRaysDoNotProducePhantomInputs() {
        CubeOptics.Frame frame = CubeOptics.frame(Vec3d.ZERO, 0, 0);
        assertFalse(frame.intersect(Vec3d.ZERO, new Vec3d(0, 0, 3)).acceptsInput());
        assertNull(frame.intersect(new Vec3d(0.6D, 0, -3), new Vec3d(0.6D, 0, 3)));
        assertEquals(new Vec3d(0, 0, CubeOptics.HALF_SIZE + 0.002D), frame.output());
    }
}
