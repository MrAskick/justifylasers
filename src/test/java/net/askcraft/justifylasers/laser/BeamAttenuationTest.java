package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.config.LaserConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeamAttenuationTest {
    @Test void disabledByDefaultAndLossCompoundsOverTheWholeOpticalRoute() {
        var config = LaserConfig.get();
        boolean enabled = config.beamAttenuation;
        double rate = config.beamLossPerBlock;
        try {
            assertFalse(new LaserConfig().beamAttenuation);
            config.beamAttenuation = false;
            assertEquals(1, BeamAttenuation.retention(512));
            config.beamAttenuation = true; config.beamLossPerBlock = .001;
            assertEquals(Math.pow(.999, 256), BeamAttenuation.retention(256), 1e-12);
            assertEquals(BeamAttenuation.retention(256), BeamAttenuation.retention(64) * BeamAttenuation.retention(192), 1e-12);
            assertTrue(BeamAttenuation.retention(1) > BeamAttenuation.retention(512));
            config.beamLossPerBlock = 1;
            assertThrows(IllegalArgumentException.class, config::validate);
        } finally { config.beamAttenuation = enabled; config.beamLossPerBlock = rate; }
    }
}
