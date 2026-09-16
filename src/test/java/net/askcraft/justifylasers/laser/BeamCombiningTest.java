package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeamCombiningTest {
    @Test void lightMixIsFluxWeightedAndOrderIndependent() {
        var a = new LightMixture(); a.add(0xFF0000, 300); a.add(0x0000FF, 100);
        var b = new LightMixture(); b.add(0x0000FF, 100); b.add(0xFF0000, 300);
        assertEquals(a.rgb(), b.rgb());
        assertEquals(255, a.rgb() >> 16 & 255);
        assertTrue((a.rgb() & 255) > 100 && (a.rgb() & 255) < 255);
        assertEquals(0, a.rgb() >> 8 & 255);
        a.add(0xFFFFFF, Double.NaN); a.add(0xFFFFFF, -1);
        assertEquals(b.rgb(), a.rgb());
    }

    @Test void coincidentBeamsAreDrawnOnceAndShortRangeContributionsExpire() {
        var beams = BeamContributions.merge(List.of(beam(4, 0xFF0000, 3), beam(8, 0x0000FF, 1)));
        assertEquals(2, beams.size());
        assertEquals(4, beams.get(0).trace().length(), 1e-6);
        assertEquals(4, beams.get(1).trace().length(), 1e-6);
        assertEquals(beams.get(0).trace().end(), beams.get(1).trace().start());
        assertEquals(4, beams.get(0).flux());
        assertEquals(0x0000FF, beams.get(1).trace().rgb());
        assertEquals(1, BeamContributions.merge(List.of(beam(4, 0xFF0000, 1), beam(4, 0x0000FF, 1))).size());
    }

    @Test void separateParallelBeamsNeverCollapseTogether() {
        var a = beam(4, 0xFF0000, 1);
        var trace = new LaserBeamTrace(new Vec3d(0, 1, 0), new Vec3d(4, 1, 0), Direction.EAST, null).withOptics(0x0000FF, 1);
        var b = new BeamContributions.Beam(trace, BlockPos.ORIGIN, 1, false, 1, 0);
        assertEquals(2, BeamContributions.merge(List.of(a, b)).size());
    }

    @Test void lossesRemainBoundedAtHugeFluxAndThroughCascades() {
        long initial = 4_000_000_000_000L, flux = initial;
        for (int i = 0; i < 16; i++) {
            long next = LuminousFlux.share(flux, .95);
            assertTrue(next < flux && next > 0);
            flux = next;
        }
        assertTrue(flux <= initial * Math.pow(.95, 16));
        var config = new LaserConfig(); config.validate();
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, -1, 1.01}) {
            config.beamCombinerEfficiency = invalid;
            assertThrows(IllegalArgumentException.class, config::validate);
        }
    }

    private static BeamContributions.Beam beam(double length, int rgb, double flux) {
        var trace = new LaserBeamTrace(Vec3d.ZERO, new Vec3d(length, 0, 0), Direction.EAST, null)
                .withOptics(rgb, 1).combinedBy(BlockPos.ORIGIN);
        return new BeamContributions.Beam(trace, BlockPos.ORIGIN, 1, true, flux, 0);
    }
}
