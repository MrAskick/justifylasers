package net.askcraft.justifylasers.client.render;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AtlasMipmapsTest {
    @Test void blockItemAndComponentParticleSpritesSupportFourMipLevels() throws Exception {
        var root = Path.of(getClass().getResource("/assets/justifylasers/textures").toURI());
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(file -> file.toString().endsWith(".png")).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (!name.startsWith("block/") && !name.startsWith("item/") && !name.endsWith("/particle.png")) continue;
                var sprite = ImageIO.read(path.toFile());
                assertEquals(0, sprite.getWidth() % 16, name + " width");
                assertEquals(0, sprite.getHeight() % 16, name + " height");
            }
        }
    }

    @Test void printingWhiteSupportsFourMipLevelsWithoutChangingColor() throws Exception {
        var white = texture("block/print_white");
        assertEquals(0, white.getWidth() % 16);
        assertEquals(0, white.getHeight() % 16);
        for (int y = 0; y < white.getHeight(); y++) for (int x = 0; x < white.getWidth(); x++)
            assertEquals(0xFFFFFFFF, white.getRGB(x, y));
    }

    @Test void crystalParticlesKeepTheirExactSwatchAndFourMipLevels() throws Exception {
        for (String crystal : List.of("amethyst", "diamond", "emerald", "photonite")) {
            var sprite = texture("block/particle/grown_" + crystal + "_crystal");
            var original = texture("component/grown_" + crystal + "_crystal/base");
            assertEquals(48, sprite.getWidth());
            assertEquals(80, sprite.getHeight());
            for (int y = 0; y < 80; y++) for (int x = 0; x < 48; x++)
                assertEquals(original.getRGB(4 + x / 2, 4 + y / 2), sprite.getRGB(x, y), crystal);
        }
    }

    @Test void printingGuideEntriesExistInBothLanguages() throws Exception {
        for (String language : List.of("en_us", "ru_ru")) {
            try (var input = getClass().getResourceAsStream("/assets/justifylasers/lang/" + language + ".json")) {
                assertNotNull(input);
                var texts = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String entry : List.of("photopolymer_bucket", "model_schematic", "printed_model")) {
                    var description = texts.get("guide.justifylasers." + entry);
                    assertNotNull(description, language + "/" + entry);
                    assertTrue(description.getAsString().length() > 40, language + "/" + entry);
                }
            }
        }
    }

    private BufferedImage texture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/justifylasers/textures/" + name + ".png")) {
            assertNotNull(input, name);
            return ImageIO.read(input);
        }
    }
}
