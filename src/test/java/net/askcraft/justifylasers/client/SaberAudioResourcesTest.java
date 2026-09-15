package net.askcraft.justifylasers.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class SaberAudioResourcesTest {
    @Test void allTwentyTwoRecordingsAreDistinctMonoVorbis() throws Exception {
        var hashes = new HashSet<String>();
        var sounds = json("sounds.json");
        for (String event : new String[]{"saber_catch", "saber_ignite", "saber_retract", "staff_ignite", "staff_retract", "saber_idle", "saber_swing", "saber_fire"}) {
            for (var entry : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
                var definition = entry.getAsJsonObject();
                assertTrue(definition.get("preload").getAsBoolean(), "Short effects must be preloaded");
                String path = definition.get("name").getAsString().replace("justifylasers:", "");
                assertTrue(path.startsWith("saber/"));
                try (var input = getClass().getResourceAsStream("/assets/justifylasers/sounds/" + path + ".ogg")) {
                    assertNotNull(input, path);
                    byte[] bytes = input.readAllBytes();
                    assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
                    int header = 27 + (bytes[26] & 255);
                    assertEquals(1, bytes[header]);
                    assertEquals("vorbis", new String(bytes, header + 1, 6, StandardCharsets.US_ASCII));
                    assertEquals(1, bytes[header + 11], "Positional audio must be mono");
                    assertEquals(44100, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(header + 12));
                    assertTrue(hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))), "Duplicate recording: " + path);
                }
            }
        }
        assertEquals(22, hashes.size());
    }

    @Test void randomizedGroupsIncludeEverySuppliedVariation() throws Exception {
        var sounds = json("sounds.json");
        for (var entry : Map.of("saber_idle", 5, "saber_swing", 9, "saber_fire", 3).entrySet()) {
            assertEquals(entry.getValue(), sounds.getAsJsonObject(entry.getKey()).getAsJsonArray("sounds").size());
        }
        for (String event : new String[]{"saber_catch", "saber_ignite", "saber_retract", "staff_ignite", "staff_retract"})
            assertEquals(1, sounds.getAsJsonObject(event).getAsJsonArray("sounds").size());
    }

    @Test void everySoundHasBothLocalizedSubtitles() throws Exception {
        var sounds = json("sounds.json");
        for (String locale : new String[]{"en_us", "ru_ru"}) {
            var language = json("lang/" + locale + ".json");
            for (var entry : sounds.entrySet()) {
                String subtitle = entry.getValue().getAsJsonObject().get("subtitle").getAsString();
                assertTrue(language.has(subtitle), subtitle);
                assertFalse(language.get(subtitle).getAsString().isBlank());
            }
        }
    }

    @Test void oldSynthesizedSaberSamplesAreNotPackaged() {
        for (String name : new String[]{"saber_idle", "saber_swing", "saber_ignite", "saber_retract"})
            assertNull(getClass().getResource("/assets/justifylasers/sounds/" + name + ".ogg"), name);
        assertNotNull(getClass().getResource("/assets/justifylasers/sounds/saber_clash.ogg"));
    }

    private JsonObject json(String path) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/justifylasers/" + path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
