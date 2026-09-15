package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeamEndpointClipTest {
    @Test
    void obliqueHaloStopsAtMirrorPlane() {
        Vec3d normal = new Vec3d(1, 0, -1).normalize();
        BeamEndpointClip clip = new BeamEndpointClip(new Vec3d(3, 2, 1), normal, new Vec3d(0, 0, -1));
        for (double x : new double[]{-2, -0.2, 0, 0.2, 2}) {
            Vec3d vertex = clip.point().add(x, 0.5, 0);
            Vec3d result = clip.clip(vertex);
            assertTrue(result.subtract(clip.point()).dotProduct(normal) >= -1e-12);
            assertEquals(vertex.x, result.x);
            assertEquals(vertex.y, result.y);
        }
    }

    @Test
    void frontSideVerticesRemainUnchanged() {
        BeamEndpointClip clip = new BeamEndpointClip(Vec3d.ZERO, new Vec3d(0, 0, 1), new Vec3d(0, 0, 1));
        Vec3d vertex = new Vec3d(2, -3, 0.1);
        assertSame(vertex, clip.clip(vertex));
    }
}
