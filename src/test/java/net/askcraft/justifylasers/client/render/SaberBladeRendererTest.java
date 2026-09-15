package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaberBladeRendererTest {
    @Test void opticalCapBandsAreConcentricAndFadeBeyondTheSolidBladeTip() {
        var end = new Vec3d(0, 2, 0);
        for (double radius : new double[]{0, 0.01, 0.05, 0.15}) for (int sign : new int[]{-1, 1}) {
            Vec3d base = SaberBladeRenderer.capPoint(end, new Vec3d(0, 1, 0), new Vec3d(1, 0, 0), radius, sign, 0);
            Vec3d tip = SaberBladeRenderer.capPoint(end, new Vec3d(0, 1, 0), new Vec3d(1, 0, 0), radius, sign, Math.PI / 2);
            assertEquals(radius * sign, base.x, 1e-10);
            assertEquals(2, base.y, 1e-10);
            assertEquals(radius, tip.distanceTo(end), 1e-10);
            assertEquals(2 + radius, tip.y, 1e-10);
        }
    }
}
