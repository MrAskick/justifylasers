package net.askcraft.justifylasers.energy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class LaserPartResourcesTest {
    @Test
    void everyPartHasDecorationStateParticleAndExactlyOneOriginalItemDrop() throws IOException {
        var parts = new java.util.ArrayList<String>();
        for (var color : net.askcraft.justifylasers.laser.LaserColor.values()) parts.add(color.asString() + "_crystal");
        for (var module : LaserModule.values()) parts.add(module.id());
        parts.add("advanced_range_module");
        parts.add("control_circuit");
        assertEquals(19, parts.size());
        assertEquals("builtin/entity", resource("assets/justifylasers/models/item/module.json").get("parent").getAsString());
        for (String part : parts) {
            assertEquals("justifylasers:block/" + part, resource("assets/justifylasers/blockstates/" + part + ".json")
                    .getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
            assertTrue(resource("assets/justifylasers/models/block/" + part + ".json").getAsJsonObject("textures").has("particle"));
            var pool = resource("data/justifylasers/loot_tables/blocks/" + part + ".json").getAsJsonArray("pools").get(0).getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            assertEquals(1, pool.getAsJsonArray("entries").size());
            assertEquals("justifylasers:" + part, pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
            if (!part.endsWith("_crystal")) assertEquals("justifylasers:item/module",
                    resource("assets/justifylasers/models/item/" + part + ".json").get("parent").getAsString());
        }
    }
    @Test
    void originalSlotIdsRemainStableAndOnlyGeometryModulesStack() {
        assertEquals(1, LaserModule.SILK_TOUCH.slot());
        assertEquals(2, LaserModule.BLOCK_DROPS.slot());
        assertEquals(3, LaserModule.SCORCH_MARKS.slot());
        assertEquals(4, LaserModule.IGNITION.slot());
        assertEquals(8, LaserModule.values().length);
        for (LaserModule module : LaserModule.values()) {
            assertEquals(module == LaserModule.RANGE || module == LaserModule.THICKNESS ? 64 : 1, module.maxCount());
        }
    }

    @Test
    void newPartsHaveModelsRecipesAndBothTranslations() throws IOException {
        JsonObject english = resource("assets/justifylasers/lang/en_us.json");
        JsonObject russian = resource("assets/justifylasers/lang/ru_ru.json");
        assertEquals(english.keySet(), russian.keySet(), "Localization keys must stay in sync");
        for (String item : new String[]{"block_destruction_module", "entity_damage_module", "range_module", "advanced_range_module", "thickness_module", "control_circuit"}) {
            assertTrue(english.has("item.justifylasers." + item));
            assertTrue(resource("assets/justifylasers/models/item/" + item + ".json").has("textures"));
            JsonObject recipe = resource("data/justifylasers/recipes/" + item + ".json");
            assertEquals("justifylasers:" + item, recipe.getAsJsonObject("result").get("item").getAsString());
            assertTrue(recipe.has("fabric:load_conditions"), "Technical parts must use server recipe conditions");
        }
    }

    @Test
    void advancedRangeUsesExactlyEightBasicModulesAndOneCircuit() throws IOException {
        JsonObject recipe = resource("data/justifylasers/recipes/advanced_range_module.json");
        assertEquals("[\"RRR\",\"RCR\",\"RRR\"]", recipe.getAsJsonArray("pattern").toString());
        assertEquals("justifylasers:range_module", recipe.getAsJsonObject("key").getAsJsonObject("R").get("item").getAsString());
        assertEquals("justifylasers:control_circuit", recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
    }

    private static JsonObject resource(String path) throws IOException {
        try (var reader = new InputStreamReader(Objects.requireNonNull(LaserPartResourcesTest.class.getClassLoader().getResourceAsStream(path), path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void allCrystalColorsUseTheVolumetricModelAndKeepTheirRecipes() throws IOException {
        JsonObject model = resource("assets/justifylasers/models/item/crystal.json");
        assertEquals("builtin/entity", model.get("parent").getAsString());
        for (String context : new String[]{"gui", "ground", "fixed", "firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"}) {
            assertTrue(model.getAsJsonObject("display").has(context));
        }
        for (String color : new String[]{"red", "orange", "yellow", "green", "cyan", "blue", "violet", "magenta", "white"}) {
            assertEquals("justifylasers:item/crystal", resource("assets/justifylasers/models/item/" + color + "_crystal.json").get("parent").getAsString());
            JsonObject recipe = resource("data/justifylasers/recipes/" + color + "_crystal.json");
            assertEquals("justifylasers:" + color + "_crystal", recipe.getAsJsonObject("result").get("item").getAsString());
            assertEquals("[\" Q \",\"QDQ\",\" A \"]", recipe.getAsJsonArray("pattern").toString());
            assertTrue(recipe.has("fabric:load_conditions"));
            for (String suffix : new String[]{"", "_s", "_n", "_light", "_light_s", "_light_n"}) {
                String path = "assets/justifylasers/textures/item/crystal/" + color + suffix + ".png";
                try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertNotNull(stream, path);
                    var texture = ImageIO.read(stream);
                    assertNotNull(texture, path);
                    assertTrue(texture.getWidth() >= 16 && texture.getHeight() >= 16);
                }
            }
        }
    }
}
