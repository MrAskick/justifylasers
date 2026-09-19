package net.askcraft.justifylasers.client.screen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguratorRadialTest {
    @Test void everySectorHasCorrectMouseSelectionAndTheCenterCancels() {
        for (int i = 0; i < 6; i++) {
            for (int offset = -28; offset <= 28; offset++) for (int r : new int[]{44, 80, 121}) {
                double angle = Math.toRadians(-90 + i * 60 + offset);
                assertEquals(i, ConfiguratorRadialScreen.selection(Math.cos(angle) * r, Math.sin(angle) * r));
            }
        }
        assertEquals(-1, ConfiguratorRadialScreen.selection(0, 0));
        assertEquals(-1, ConfiguratorRadialScreen.selection(20, 20));
        assertEquals(-1, ConfiguratorRadialScreen.selection(123, 0));
    }
}
