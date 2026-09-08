package net.askcraft.justifylasers.laser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LaserScorchTest {
    @Test
    void heatCoolsMonotonicallyAndStaysClamped() {
        assertEquals(1, LaserScorchMarks.heat(-10));
        assertEquals(1, LaserScorchMarks.heat(0));
        float previous = 1;
        for (double age = 0; age < LaserScorchMarks.COOLING_TICKS + 10; age += 0.25D) {
            float heat = LaserScorchMarks.heat(age);
            assertTrue(heat >= 0 && heat <= previous);
            previous = heat;
        }
        assertEquals(0, LaserScorchMarks.heat(LaserScorchMarks.COOLING_TICKS));
    }

    @Test
    void coldSootHasAStableLifetimeWithASmoothFinalFade() {
        assertEquals(1, LaserScorchMarks.opacity(0));
        assertEquals(1, LaserScorchMarks.opacity(2_000));
        assertEquals(0.5F, LaserScorchMarks.opacity(2_200));
        assertEquals(0, LaserScorchMarks.opacity(2_400));
        assertEquals(0, LaserScorchMarks.opacity(100_000));
        float previous = 1;
        for (double age = 0; age < 2_500; age += 0.5D) {
            float opacity = LaserScorchMarks.opacity(age);
            assertTrue(opacity >= 0 && opacity <= previous);
            previous = opacity;
        }
    }

    @Test
    void widthTracksTheLaserButKeepsTinyAndHugeMarksBounded() {
        assertEquals(0.012D, ScorchGeometry.radius(0.001D));
        assertEquals(0.095D, ScorchGeometry.radius(1));
        assertEquals(0.19D, ScorchGeometry.radius(2));
        assertEquals(0.85D, ScorchGeometry.radius(1_000));
    }
}
