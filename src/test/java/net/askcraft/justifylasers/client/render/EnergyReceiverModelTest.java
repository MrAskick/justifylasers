package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.OpticPortMode;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnergyReceiverModelTest {
    @Test
    void chassisAndAllSocketOrientationsHaveValidGeometryAndComponentUvs() {
        var faces = new ArrayList<>(EnergyReceiverModel.chassisMesh());
        EnergyReceiverModel.portMeshes().values().forEach(panels -> panels.values().forEach(faces::addAll));
        var atlas = ComponentAtlas.load("energy_receiver", "base");
        for (var face : faces) {
            assertEquals(1, face.normal().length(), 1e-7);
            for (var point : List.of(face.a(), face.b(), face.c(), face.d())) {
                assertTrue(Math.abs(point.x) <= 0.5 && Math.abs(point.y) <= 0.5 && Math.abs(point.z) <= 0.5);
            }
            var cross = face.b().subtract(face.a()).crossProduct(face.c().subtract(face.a()));
            if (cross.lengthSquared() < 1e-12) cross = face.c().subtract(face.a()).crossProduct(face.d().subtract(face.a()));
            assertTrue(cross.dotProduct(face.normal()) > 0, "Face winding must agree with the outward normal");
            assertTrue(atlas.regions().values().stream().anyMatch(region ->
                    List.of(face.ua(), face.ub(), face.uc(), face.ud()).stream().allMatch(region::contains)));
        }
    }

    @Test
    void everySideStillSupportsInputOutputAndDisabledModes() {
        for (Direction facing : Direction.values()) for (Direction side : Direction.values()) {
            assertEquals(EnergyReceiverModel.Panel.INPUT, EnergyReceiverModel.panel(side, facing, OpticPortMode.INPUT));
            assertEquals(EnergyReceiverModel.Panel.DISABLED, EnergyReceiverModel.panel(side, facing, OpticPortMode.DISABLED));
            var output = EnergyReceiverModel.panel(side, facing, OpticPortMode.OUTPUT);
            assertNotEquals(EnergyReceiverModel.Panel.INPUT, output);
            assertNotEquals(EnergyReceiverModel.Panel.DISABLED, output);
            assertFalse(EnergyReceiverModel.portMeshes().get(side).get(output).isEmpty());
            assertTrue(EnergyReceiverModel.portMeshes().get(side).get(EnergyReceiverModel.Panel.DISABLED)
                    .stream().noneMatch(EnergyReceiverModel.Face::glowing));
        }
    }

    @Test
    void emittedColorIsNotBakedIntoTheMaskAndIdleMaterialsDoNotEmit() throws Exception {
        String root = "/assets/justifylasers/textures/component/energy_receiver/";
        var atlas = ComponentAtlas.load("energy_receiver", "base");
        int luminousPixels = 0;
        for (String name : List.of("base", "base_n", "base_s", "glow", "glow_n", "glow_s", "indicator", "indicator_n", "indicator_s")) {
            try (var stream = getClass().getResourceAsStream(root + name + ".png")) {
                assertNotNull(stream, name);
                var image = ImageIO.read(stream);
                assertEquals(atlas.size(), image.getWidth());
                assertEquals(atlas.size(), image.getHeight());
                for (var region : atlas.regions().values()) for (int y = region.y(); y < region.y() + region.height(); y++)
                    for (int x = region.x(); x < region.x() + region.width(); x++) {
                        int pixel = image.getRGB(x, y);
                        if (name.equals("glow") || name.equals("indicator")) {
                            assertEquals(pixel >> 16 & 255, pixel >> 8 & 255);
                            assertEquals(pixel >> 16 & 255, pixel & 255);
                            if (name.equals("glow") && pixel >>> 24 > 25) luminousPixels++;
                        }
                        if (name.equals("base_s") || name.equals("indicator_s")) assertEquals(255, pixel >>> 24);
                    }
            }
        }
        assertTrue(luminousPixels > 1000);
    }
}
