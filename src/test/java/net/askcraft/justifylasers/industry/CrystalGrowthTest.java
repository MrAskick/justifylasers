package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.laser.LaserSpectrum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalGrowthTest {
    @Test void seedYieldsDeclineWithoutMakingFreshSeedsFail() {
        int[][] expected = {{0, 815, 180, 5}, {50, 879, 70, 1}, {250, 740, 10, 0}, {900, 100, 0, 0}};
        double previous = 4, total = 0;
        for (int stage = 0; stage < 4; stage++) {
            int[] counts = new int[4]; double average = 0;
            for (int roll = 0; roll < 1000; roll++) { int count = CrystalGrowth.yield(stage, roll); counts[count]++; average += count / 1000d; }
            assertArrayEquals(expected[stage], counts);
            assertTrue(average < previous); previous = average; total += average;
        }
        assertEquals(3.072, total, 1e-9);
        assertThrows(IllegalArgumentException.class, () -> CrystalGrowth.yield(4, 0));
        assertThrows(IllegalArgumentException.class, () -> CrystalGrowth.yield(0, 1000));
    }
    @Test void wearCanSkipStagesButUsuallyOnlyDamagesTheSeed() {
        int[] outcomes = new int[5];
        for(int roll=0;roll<1000;roll++) { int stage=CrystalGrowth.nextStage(0,roll); outcomes[stage<0?4:stage]++; }
        assertArrayEquals(new int[]{0,800,150,45,5},outcomes);
        for(int roll=0;roll<1000;roll++) assertEquals(-1,CrystalGrowth.nextStage(3,roll));
    }
    @Test void everyHueInsideTheBandIsAcceptedNotJustThePreset() {
        for (var crystal : CrystalGrowth.values()) {
            for (double hue=crystal.hueMin()+.5; hue<crystal.hueMax(); hue+=.5)
                assertTrue(crystal.acceptsSpectrum(java.awt.Color.HSBtoRGB((float)(hue/360),.8F,.8F)),crystal+" hue "+hue);
            for (double hue : new double[]{crystal.hueMin()-2,crystal.hueMax()+2})
                assertFalse(crystal.acceptsSpectrum(java.awt.Color.HSBtoRGB((float)(hue/360),.8F,.8F)));
        }
    }
    @Test void spectrumIsIndependentOfBrightnessButRejectsWrongColorsAndBlack() {
        for (var crystal : CrystalGrowth.values()) {
            int rgb = crystal.spectrum();
            int dim = ((rgb >> 16 & 255) / 2 << 16) | ((rgb >> 8 & 255) / 2 << 8) | (rgb & 255) / 2;
            assertTrue(CrystalGrowth.matchesSpectrum(rgb, rgb));
            assertTrue(CrystalGrowth.matchesSpectrum(dim, rgb));
            assertFalse(CrystalGrowth.matchesSpectrum(0, rgb));
            assertFalse(CrystalGrowth.matchesSpectrum(0xFFFFFF, rgb));
            for (var other : CrystalGrowth.values()) if (other != crystal) assertFalse(CrystalGrowth.matchesSpectrum(other.spectrum(), rgb));
            assertTrue(crystal.minimum() < crystal.reference());
        }
    }
    @Test void hexRequiresExactlySixValidDigits() {
        assertEquals(0x1A2BEF, LaserSpectrum.parse("1a2bEF"));
        for (String invalid : new String[]{"", "12345", "1234567", "-00001", "ffffff ", "GG1234"}) assertEquals(-1, LaserSpectrum.parse(invalid));
        assertEquals(-1, LaserSpectrum.parse(null));
    }
}
