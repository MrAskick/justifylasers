package net.askcraft.justifylasers.industry;

import net.askcraft.justifylasers.laser.LaserSpectrum;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrystalGrowthTest {
    @Test void basicCultivationRetainsItsOriginalYieldDistribution() {
        int[] counts=new int[4];
        for(int roll=0;roll<1000;roll++)counts[CrystalGrowth.basicYield(roll)]++;
        assertArrayEquals(new int[]{150,550,200,100},counts);
    }
    @Test void oneBucketHasOneYieldRollAndAtMostSixFinishedGems() {
        int[] counts=new int[7];
        double mean=0;
        for(int roll=0;roll<1000;roll++) {
            int gems=CrystalGrowth.cuttingYield(roll);
            counts[gems]++;
            mean+=gems/1000d;
            assertTrue(gems>=3&&gems<=6);
        }
        assertArrayEquals(new int[]{0,0,0,550,300,120,30},counts);
        assertEquals(3.63,mean,1e-9);
        assertThrows(IllegalArgumentException.class,()->CrystalGrowth.cuttingYield(-1));
        assertThrows(IllegalArgumentException.class,()->CrystalGrowth.cuttingYield(1000));
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
            int opposite = java.awt.Color.HSBtoRGB((float)(((crystal.hueMin() + crystal.hueMax()) / 2d + 180) % 360 / 360), .8F, .8F);
            assertFalse(CrystalGrowth.matchesSpectrum(opposite, rgb));
            assertTrue(crystal.minimum() < crystal.reference());
        }
    }
    @Test void photoniteSeedAndSpectrumUsePurpleInsteadOfGold() {
        var crystal = CrystalGrowth.PHOTONITE;
        assertEquals(0xBC09F5, crystal.spectrum());
        assertEquals(ProcessFluid.PHOTONITE.rgb(), crystal.spectrum());
        assertEquals(net.askcraft.justifylasers.laser.LaserColor.VIOLET,
                net.askcraft.justifylasers.laser.LaserColor.nearest(crystal.spectrum()));
        assertTrue(crystal.acceptsSpectrum(0xBC09F5));
        assertFalse(crystal.acceptsSpectrum(0xFFD45A));
        assertFalse(crystal.acceptsSpectrum(0xFFFF00));
    }
    @Test void hexRequiresExactlySixValidDigits() {
        assertEquals(0x1A2BEF, LaserSpectrum.parse("1a2bEF"));
        for (String invalid : new String[]{"", "12345", "1234567", "-00001", "ffffff ", "GG1234"}) assertEquals(-1, LaserSpectrum.parse(invalid));
        assertEquals(-1, LaserSpectrum.parse(null));
    }
}
