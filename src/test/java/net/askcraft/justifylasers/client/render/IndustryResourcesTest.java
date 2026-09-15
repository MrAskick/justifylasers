package net.askcraft.justifylasers.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class IndustryResourcesTest {
    @Test void mineralsUseResourcePackStoneAndRegisteredAtlasSprites() throws Exception {
        var sources = new HashSet<String>();
        for (var source : resource("assets/minecraft/atlases/blocks.json").getAsJsonArray("sources"))
            sources.add(source.getAsJsonObject().get("resource").getAsString());
        for (String mineral : new String[]{"photonite", "wolframite"}) {
            assertTrue(sources.contains("justifylasers:component/" + mineral + "/base"));
            assertNotNull(getClass().getResource("/assets/justifylasers/textures/component/" + mineral + "/base_s.png"));
        }
        for (String id : new String[]{"wolframite_ore", "deepslate_wolframite_ore", "photonic_crystal_ore", "deepslate_photonic_crystal_ore"}) {
            var model = resource("assets/justifylasers/models/block/" + id + ".json");
            assertTrue(model.getAsJsonObject("textures").asMap().values().stream().anyMatch(value ->
                    value.getAsString().equals("minecraft:block/" + (id.startsWith("deepslate") ? "deepslate" : "stone"))));
            assertEquals(1, model.getAsJsonArray("elements").size(), "Stone remains a native cube; the ore baker adds closed mineral solids");
            assertEquals("#mineral", model.getAsJsonObject("textures").get("justifylasers_ore_0").getAsString());
            assertNull(getClass().getResource("/assets/justifylasers/textures/block/industry/" + id + ".png"));
        }
    }

    @Test void rawFragmentsAndIngotHaveVolumetricModels() throws Exception {
        for (String id : new String[]{"raw_wolframite", "raw_photonic_crystal", "wolframite_ingot"}) {
            var model = resource("assets/justifylasers/models/item/" + id + ".json");
            assertEquals(3, model.getAsJsonArray("elements").size());
            for (var element : model.getAsJsonArray("elements")) {
                var from = element.getAsJsonObject().getAsJsonArray("from");
                var to = element.getAsJsonObject().getAsJsonArray("to");
                for (int axis = 0; axis < 3; axis++) assertTrue(to.get(axis).getAsDouble() > from.get(axis).getAsDouble());
            }
        }
    }

    @Test void oreInclusionsHaveFourStableIrregularVariants() throws Exception {
        for (String id : new String[]{"wolframite_ore", "deepslate_wolframite_ore", "photonic_crystal_ore", "deepslate_photonic_crystal_ore"}) {
            var variants = resource("assets/justifylasers/blockstates/" + id + ".json").getAsJsonObject("variants").getAsJsonArray("");
            assertEquals(4, variants.size());
            var layouts = new HashSet<String>();
            for (var variant : variants) {
                String modelName = variant.getAsJsonObject().get("model").getAsString().substring("justifylasers:".length());
                var elements = resource("assets/justifylasers/models/" + modelName + ".json").getAsJsonArray("elements");
                assertEquals(1, elements.size());
                layouts.add(modelName);
                var textures = resource("assets/justifylasers/models/" + modelName + ".json").getAsJsonObject("textures");
                assertTrue(textures.keySet().stream().anyMatch(key -> key.startsWith("justifylasers_ore_")), "The variant must opt into native mineral geometry");
            }
            assertEquals(4, layouts.size());
        }
    }

    @Test void crystalMountUsesItsOwnPackagedBreakingSprite() throws Exception {
        for (String model : new String[]{"block", "item"})
            assertEquals("justifylasers:block/particle/crystal_mount", resource("assets/justifylasers/models/" + model + "/crystal_mount.json")
                    .getAsJsonObject("textures").get("particle").getAsString());
        try (var stream = getClass().getResourceAsStream("/assets/justifylasers/textures/block/particle/crystal_mount.png")) {
            assertNotNull(stream, "The baked model must not resolve to missingno");
            var sprite = javax.imageio.ImageIO.read(stream);
            assertNotNull(sprite);
            assertEquals(64, sprite.getWidth());
            assertEquals(64, sprite.getHeight());
        }
    }

    private JsonObject resource(String name) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/" + name), name), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
