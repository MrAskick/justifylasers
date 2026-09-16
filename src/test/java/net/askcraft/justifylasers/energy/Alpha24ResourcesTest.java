package net.askcraft.justifylasers.energy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class Alpha24ResourcesTest {
    @Test void tabletAndCombinerAreAssemblyOnlyWithFourInputs() throws Exception {
        for (String id : new String[]{"extraterrestrial_tablet", "beam_combiner"}) {
            assertNull(getClass().getResource("/data/justifylasers/recipes/" + id + ".json"));
            var recipe = json("data/justifylasers/recipes/industry/" + id + ".json");
            assertEquals(id, recipe.get("blueprint").getAsString());
            assertEquals("ASSEMBLY_CHAMBER", recipe.get("machine").getAsString());
            assertEquals(4, recipe.getAsJsonArray("inputs").size());
            assertTrue(recipe.get("ticks").getAsInt() > 0);
            assertTrue(recipe.get("energy").getAsInt() > 0);
        }
    }

    @Test void workbenchRecipesUseTheRequestedMaterials() throws Exception {
        assertEquals("minecraft:diamond", ingredient("control_circuit", "G"));
        assertEquals("minecraft:iron_ingot", ingredient("crystal_mount", "I"));
        assertEquals("minecraft:iron_ingot", ingredient("blank_schematic", "I"));
        assertEquals("minecraft:lapis_lazuli", ingredient("blank_schematic", "L"));
        assertEquals("IRL", json("data/justifylasers/recipes/blank_schematic.json").getAsJsonArray("pattern").get(1).getAsString());
    }

    @Test void bothHandsShowTheSchematicFaceAndBlankUsesTheSameTransforms() throws Exception {
        var display = json("assets/justifylasers/models/item/assembly_blueprint.json").getAsJsonObject("display");
        for (String mode : new String[]{"firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"})
            assertEquals(180, display.getAsJsonObject(mode).getAsJsonArray("rotation").get(1).getAsInt());
        assertEquals("justifylasers:item/assembly_blueprint", json("assets/justifylasers/models/item/blank_schematic.json").get("parent").getAsString());
    }

    private String ingredient(String id, String key) throws Exception {
        return json("data/justifylasers/recipes/" + id + ".json").getAsJsonObject("key").getAsJsonObject(key).get("item").getAsString();
    }
    private JsonObject json(String path) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/" + path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
