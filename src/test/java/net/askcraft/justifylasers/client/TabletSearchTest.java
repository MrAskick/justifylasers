package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.screen.TabletScreen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletSearchTest {
    @Test void localizedWordsRegistryNamesAndWhitespaceAreSearchable() {
        assertTrue(TabletScreen.matchesQuery("  ЛАЗЕРНЫЙ   ПРИЕМНИК  ","Лазерный приёмник","justifylasers:laser_receiver"));
        assertTrue(TabletScreen.matchesQuery("laser_receiver","Лазерный приёмник","justifylasers:laser_receiver"));
        assertTrue(TabletScreen.matchesQuery("  ","Laser Emitter"));
        assertFalse(TabletScreen.matchesQuery("laser receiver","Laser Emitter"));
        assertFalse(TabletScreen.matchesQuery("нет_такой_схемы","Лазерный приёмник"));
    }
}
