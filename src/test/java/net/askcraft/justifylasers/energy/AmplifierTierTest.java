package net.askcraft.justifylasers.energy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmplifierTierTest {
    @Test void onlyTheFirstFourTiersAreAvailableWithoutRemovingHigherTierData() {
        assertEquals(java.util.List.of(1, 2, 3, 4), AmplifierTier.AVAILABLE);
        assertEquals(131_072_000L, AmplifierTier.lumens(5));
        assertEquals(1_048_576_000L, AmplifierTier.lumens(6));
    }

    @Test void sixLevelsScaleFluxAndEnergyByEight() {
        long previous=0;
        for(int tier=1;tier<=AmplifierTier.MAX;tier++) {
            long flux=AmplifierTier.lumens(tier);
            assertTrue(flux>previous && flux<=1_000_000_000_000L);
            assertEquals(32_000L << (3 * (tier - 1)), flux);
            assertEquals(34L << (3 * (tier - 1)), AmplifierTier.energy(tier));
            assertTrue(AmplifierTier.energy(tier)*1000 >= flux);
            previous=flux;
        }
        assertEquals(32_000,AmplifierTier.lumens(1));
        assertEquals(1_048_576_000L,previous);
        assertEquals(1_114_112,AmplifierTier.energy(AmplifierTier.MAX));
        assertEquals(6, AmplifierTier.MAX);
        assertEquals(0, AmplifierTier.energy(7));
        assertEquals(0,AmplifierTier.lumens(0)); assertEquals(0,AmplifierTier.lumens(7));
    }
}
