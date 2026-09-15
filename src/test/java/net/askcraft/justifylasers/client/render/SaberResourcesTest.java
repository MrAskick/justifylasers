package net.askcraft.justifylasers.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class SaberResourcesTest {
    @Test void bothWeaponsHaveModelsAssemblyRecipesAndUsageGuides() throws Exception {
        for (String id : new String[]{"laser_saber", "light_staff"}) {
            var model = resource("assets/justifylasers/models/item/" + id + ".json");
            assertEquals("builtin/entity", model.get("parent").getAsString());
            assertNull(getClass().getResourceAsStream("/data/justifylasers/recipes/" + id + ".json"));
            var recipe = resource("data/justifylasers/recipes/industry/" + id + ".json");
            assertEquals("justifylasers:industrial", recipe.get("type").getAsString());
            assertEquals(id, recipe.get("blueprint").getAsString());
            assertEquals("justifylasers:" + id, recipe.getAsJsonObject("result").get("item").getAsString());
            assertFalse(recipe.has("fabric:load_conditions"));
            for (String locale : new String[]{"en_us", "ru_ru"}) {
                var lang = resource("assets/justifylasers/lang/" + locale + ".json");
                assertTrue(lang.has("item.justifylasers." + id));
                assertTrue(lang.has("guide.justifylasers." + id));
            }
        }
    }

    private JsonObject resource(String name) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/" + name)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
