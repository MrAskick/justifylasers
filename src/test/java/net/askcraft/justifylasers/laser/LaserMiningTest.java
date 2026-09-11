package net.askcraft.justifylasers.laser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LaserMiningTest {
    @Test
    void minimumMatchesThePreviousHardnessFormulaExactly() {
        for (float hardness : new float[]{0, 0.1F, 0.2F, 0.3F, 0.5F, 1.5F, 2, 3, 5, 50, 100}) {
            int oldTicks = (int) Math.ceil(Math.min(600.0F, Math.max(12.0F, 18.0F + hardness * 28.0F)));
            assertEquals(oldTicks, LaserMining.ticksToBreak(hardness, 0));
        }
    }

    @Test
    void increasingSpeedNeverSlowsMiningAndMaximumAlwaysTakesOneTick() {
        for (float hardness : new float[]{0, 0.3F, 1.5F, 3, 50, 100}) {
            int previous = LaserMining.ticksToBreak(hardness, 0);
            for (int speed = 1; speed <= LaserMining.MAX_SPEED_STEP; speed++) {
                int ticks = LaserMining.ticksToBreak(hardness, speed);
                assertTrue(ticks >= 1 && ticks <= previous);
                previous = ticks;
            }
            assertEquals(1, previous);
        }
    }

    @Test
    void invalidSpeedValuesClampToTheEndpoints() {
        assertEquals(0, LaserMining.clampSpeedStep(Integer.MIN_VALUE));
        assertEquals(100, LaserMining.clampSpeedStep(Integer.MAX_VALUE));
        assertEquals(60, LaserMining.ticksToBreak(1.5F, Integer.MIN_VALUE));
        assertEquals(1, LaserMining.ticksToBreak(50, Integer.MAX_VALUE));
    }
}
