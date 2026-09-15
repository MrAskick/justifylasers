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
    void redstoneReceiverUsesTheEmitterInventoryTransform() throws IOException {
        var emitter = resource("assets/justifylasers/models/block/laser_emitter.json");
        var receiver = resource("assets/justifylasers/models/block/laser_receiver.json");
        assertEquals(emitter.get("parent"), receiver.get("parent"));
        assertFalse(emitter.has("display") && emitter.getAsJsonObject("display").has("gui"));
        assertFalse(receiver.has("display") && receiver.getAsJsonObject("display").has("gui"),
                "Both items inherit the same GUI transform from block/block");
    }

    @Test
    void everyPartHasDecorationStateParticleAndExactlyOneOriginalItemDrop() throws IOException {
        var parts = new java.util.ArrayList<String>();
        for (var color : net.askcraft.justifylasers.laser.LaserColor.values()) parts.add(color.asString() + "_crystal");
        for (var module : LaserModule.values()) parts.add(module.id());
        parts.add("advanced_range_module");
        parts.add("control_circuit");
        assertEquals(20, parts.size());
        assertEquals("builtin/entity", resource("assets/justifylasers/models/item/module.json").get("parent").getAsString());
        for (String part : parts) {
            assertEquals("justifylasers:block/" + part, resource("assets/justifylasers/blockstates/" + part + ".json")
                    .getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
            assertEquals("justifylasers:block/particle/" + part,
                    resource("assets/justifylasers/models/block/" + part + ".json").getAsJsonObject("textures").get("particle").getAsString());
            try (var stream = getClass().getResourceAsStream("/assets/justifylasers/textures/block/particle/" + part + ".png")) {
                assertNotNull(stream, part);
                var sprite = ImageIO.read(stream);
                for(int y=0;y<sprite.getHeight();y++) for(int x=0;x<sprite.getWidth();x++)
                    assertEquals(255,sprite.getRGB(x,y)>>>24,"Particle sprites cannot sample transparent atlas gutters");
            }
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
        assertEquals(9, LaserModule.TARGET_FILTER.slot());
        assertEquals(9, LaserModule.values().length);
        for (LaserModule module : LaserModule.values()) {
            assertEquals(module == LaserModule.RANGE || module == LaserModule.THICKNESS ? 64 : 1, module.maxCount());
        }
    }

    @Test
    void newPartsHaveModelsRecipesAndBothTranslations() throws IOException {
        JsonObject english = resource("assets/justifylasers/lang/en_us.json");
        JsonObject russian = resource("assets/justifylasers/lang/ru_ru.json");
        assertEquals(english.keySet(), russian.keySet(), "Localization keys must stay in sync");
        for (String item : new String[]{"block_destruction_module", "entity_damage_module", "range_module", "advanced_range_module", "thickness_module", "target_filter_module", "control_circuit"}) {
            assertTrue(english.has("item.justifylasers." + item));
            assertTrue(resource("assets/justifylasers/models/item/" + item + ".json").has("textures"));
            boolean circuit = item.equals("control_circuit");
            JsonObject recipe = resource("data/justifylasers/recipes/" + (circuit ? "" : "industry/") + item + ".json");
            assertEquals("justifylasers:" + item, recipe.getAsJsonObject("result").get("item").getAsString());
            if (!circuit) {
                assertEquals("justifylasers:industrial", recipe.get("type").getAsString());
                assertEquals(item, recipe.get("blueprint").getAsString());
                assertNull(getClass().getClassLoader().getResource("data/justifylasers/recipes/" + item + ".json"));
            }
        }
    }

    @Test
    void advancedRangeUsesExactlyEightBasicModulesAndOneCircuit() throws IOException {
        JsonObject recipe = resource("data/justifylasers/recipes/industry/advanced_range_module.json");
        var inputs = recipe.getAsJsonArray("inputs");
        assertEquals(2, inputs.size());
        assertEquals("justifylasers:range_module", inputs.get(0).getAsJsonObject().getAsJsonObject("ingredient").get("item").getAsString());
        assertEquals(8, inputs.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("justifylasers:control_circuit", inputs.get(1).getAsJsonObject().getAsJsonObject("ingredient").get("item").getAsString());
        assertEquals(1, inputs.get(1).getAsJsonObject().get("count").getAsInt());
    }

    private static JsonObject resource(String path) throws IOException {
        try (var reader = new InputStreamReader(Objects.requireNonNull(LaserPartResourcesTest.class.getClassLoader().getResourceAsStream(path), path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void opticsHaveRecipesLootAndLocalizedUsageGuides() throws IOException {
        var english = resource("assets/justifylasers/lang/en_us.json");
        for (String id : new String[]{"laser_mirror", "beam_splitter", "energy_receiver", "configurator"}) {
            assertTrue(english.has("guide.justifylasers." + id));
            var model = resource("assets/justifylasers/models/item/" + id + ".json");
            assertEquals("builtin/entity", model.get("parent").getAsString(), "Use the component mesh in inventory and world");
            var recipe = resource("data/justifylasers/recipes/industry/" + id + ".json");
            assertEquals("ASSEMBLY_CHAMBER", recipe.get("machine").getAsString());
            assertEquals(id, recipe.get("blueprint").getAsString());
            if (!id.equals("configurator")) assertNotNull(resource("data/justifylasers/loot_tables/blocks/" + id + ".json"));
        }
        for (LaserModule module : LaserModule.values()) assertTrue(english.has("guide.justifylasers." + module.id()));
        var sounds = resource("assets/justifylasers/sounds.json");
        assertEquals(java.util.Set.of("laser_start", "laser_idle", "laser_stop", "laser_contact", "saber_idle", "saber_ignite", "saber_retract", "saber_swing", "saber_clash",
                "saber_catch", "saber_fire", "staff_ignite", "staff_retract"), sounds.keySet());
        for (String id : sounds.keySet()) {
            assertTrue(english.has("subtitles.justifylasers." + id));
            for (var sound : sounds.getAsJsonObject(id).getAsJsonArray("sounds")) {
                String name = (sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString()).replace("justifylasers:", "");
                try (var stream = getClass().getClassLoader().getResourceAsStream("assets/justifylasers/sounds/" + name + ".ogg")) {
                    assertNotNull(stream, name);
                    assertArrayEquals(new byte[]{'O', 'g', 'g', 'S'}, stream.readNBytes(4));
                }
            }
        }
    }

    @Test
    void configuratorRetainsEveryItemDisplayContext() throws IOException {
        var model = resource("assets/justifylasers/models/item/configurator.json");
        assertEquals("builtin/entity", model.get("parent").getAsString());
        for (String context : new String[]{"gui", "ground", "fixed", "firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"})
            assertTrue(model.getAsJsonObject("display").has(context));
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
            assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
            assertEquals("justifylasers:laser_crystals", recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("tag").getAsString());
            assertTrue(resource("data/justifylasers/recipes/legacy_" + color + "_crystal.json").has("fabric:load_conditions"));
            assertFalse(recipe.has("fabric:load_conditions"), "Crystals also work as optics without technical mods");
            for (String suffix : color.equals("cyan") ? new String[]{"", "_light", "_light_s", "_light_n"} : new String[]{""}) {
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
