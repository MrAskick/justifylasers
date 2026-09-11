package net.askcraft.justifylasers.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class PoweredGuiTextureTest {
    @Test
    void backgroundUsesRealAlphaForItsExteriorAndPanel() throws IOException {
        var resource = Objects.requireNonNull(getClass().getResource("/assets/justifylasers/textures/gui/powered_emitter.png"));
        var image = ImageIO.read(resource);
        assertTrue(image.getColorModel().hasAlpha(), "A painted checkerboard is not transparency");
        assertTrue((image.getRGB(0, 0) >>> 24) <= 1, "Outer corner must be transparent");
        assertEquals(0, image.getRGB(image.getWidth() / 2, 0) >>> 24, "Space outside the contour must be transparent");
        int panelAlpha = image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24;
        assertTrue(panelAlpha > 0 && panelAlpha < 255, "Panel must retain partial transparency");
    }
}
