package net.askcraft.justifylasers.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClientSettingsTest {
    @TempDir Path directory;
    @AfterEach void defaults() { ClientSettings.reset(); }

    @Test void defaultsPreserveTheOpticsAndUseOnePointEight() {
        var settings = new ClientSettings();
        assertEquals(1.8, settings.cubeMagnification);
        assertTrue(settings.cubeLenses && settings.scopeLens && settings.scorchMarks && settings.weaponSway);
        assertEquals(64, settings.cubeLensDistance);
        assertEquals(96, settings.scorchDistance);
    }

    @Test void outOfRangeAndNonfiniteValuesAreSafe() {
        var settings = new ClientSettings();
        settings.cubeMagnification = Double.NaN;
        settings.cubeLensDistance = Integer.MAX_VALUE;
        settings.maxLensCubes = -1;
        settings.scorchDistance = Integer.MIN_VALUE;
        settings.soundVolume = Double.POSITIVE_INFINITY;
        settings.maxSoundSources = 100;
        settings.normalize();
        assertEquals(1.8, settings.cubeMagnification);
        assertEquals(128, settings.cubeLensDistance);
        assertEquals(1, settings.maxLensCubes);
        assertEquals(16, settings.scorchDistance);
        assertEquals(1, settings.soundVolume);
        assertEquals(64, settings.maxSoundSources);
    }

    @Test void settingsRoundTripAndNewFieldsKeepTheirDefaults() throws Exception {
        Path path = directory.resolve("client.json");
        Files.writeString(path, "{\"cubeMagnification\":2.4,\"scopeLens\":false}");
        ClientSettings.load(path);
        assertTrue(ClientSettings.get().cubeLenses);
        assertFalse(ClientSettings.get().scopeLens);
        assertEquals(2.4, ClientSettings.get().cubeMagnification);
        ClientSettings.get().scorchMarks = false;
        assertTrue(ClientSettings.save());
        ClientSettings.load(path);
        assertFalse(ClientSettings.get().scorchMarks);
        try (var files = Files.list(directory)) { assertEquals(1, files.count(), "No stranded temporary files"); }
    }

    @Test void malformedInputIsKeptForRecovery() throws Exception {
        Path path = directory.resolve("client.json");
        Files.writeString(path, "broken settings");
        ClientSettings.load(path);
        assertEquals(1.8, ClientSettings.get().cubeMagnification);
        assertEquals("broken settings", Files.readString(directory.resolve("client.json.invalid")));
        assertTrue(ClientSettings.save());
    }

    @Test void failedWritePreservesTheExistingPathAndLeavesNoTemporaryFile() throws Exception {
        Path target = directory.resolve("client.json");
        ClientSettings.load(target);
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep.txt"), "untouched");
        assertFalse(ClientSettings.save());
        assertEquals("untouched", Files.readString(target.resolve("keep.txt")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
