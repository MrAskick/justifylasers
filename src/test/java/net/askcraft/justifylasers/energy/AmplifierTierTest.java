package net.askcraft.justifylasers.energy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmplifierTierTest {
    @Test void upgradesAreMonotonicAndCapAtOneTeralumen() {
        long previous=0;
        for(int tier=1;tier<=AmplifierTier.MAX;tier++) {
            long flux=AmplifierTier.lumens(tier);
            assertTrue(flux>previous && flux<=1_000_000_000_000L);
            assertTrue(AmplifierTier.energy(tier,1000)*1000 >= flux);
            assertTrue((AmplifierTier.energy(tier,1000)-1)*1000 < flux);
            previous=flux;
        }
        assertEquals(1,AmplifierTier.lumens(1));
        assertEquals(1_000_000_000_000L,previous);
        assertEquals(1_000_000_000,AmplifierTier.energy(AmplifierTier.MAX,1000));
        assertEquals(0,AmplifierTier.lumens(0)); assertEquals(0,AmplifierTier.lumens(16));
    }
}
