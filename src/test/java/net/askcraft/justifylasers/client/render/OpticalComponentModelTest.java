package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.OpticPortMode;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpticalComponentModelTest {
    @Test
    void meshesHaveConsistentWindingAndComponentUvs() {
        verify("configurator", LaserConfiguratorModel.MESH, 1.45);
        verify("refocusing_cube", RefocusingCubeChassis.MESH, 0.451);
        verify("laser_mirror", LaserMirrorModel.PLATE, 0.5);
        for (var mesh : List.of(LaserMirrorModel.BASE, LaserMirrorModel.STAND, LaserMirrorModel.GLASS)) verify("laser_mirror", mesh, 0.5);
        verify("beam_splitter", BeamSplitterModel.CHASSIS, 0.5);
        verify("spectrum_module", SpectrumModuleModel.FRAME, .5);
        verify("spectrum_module", SpectrumModuleModel.PRISM, .5);
        SpectrumModuleModel.DIAL.forEach(mesh -> verify("spectrum_module", mesh, .5));
        verify("laser_cutter", LaserCutterModel.FRAME, 1.01);
        verify("laser_cutter", LaserCutterModel.CASING, .51);
        BeamSplitterModel.PORTS.values().forEach(modes -> modes.values().forEach(mesh -> verify("beam_splitter", mesh, 0.5)));
    }

    @Test
    void splitterRetainsSixIndependentlyConfigurableSockets() {
        assertEquals(6, BeamSplitterModel.PORTS.size());
        for (Direction side : Direction.values()) {
            assertEquals(3, BeamSplitterModel.PORTS.get(side).size());
            for (OpticPortMode mode : OpticPortMode.values()) assertFalse(BeamSplitterModel.PORTS.get(side).get(mode).faces().isEmpty());
        }
    }

    @Test
    void mirrorSupportLeavesTheRotatingApertureClear() {
        for (var mount : List.of(LaserMirrorModel.BASE, LaserMirrorModel.STAND)) for (var face : mount.faces()) {
            for (int u = 0; u <= 8; u++) for (int v = 0; v <= 8; v++) {
                var bottom = face.a().lerp(face.b(), u / 8d);
                var top = face.d().lerp(face.c(), u / 8d);
                var point = bottom.lerp(top, v / 8d);
                assertTrue(point.length() > 5.11 / 16, "Support intersects the rotating mirror: " + point);
            }
        }
    }

    @Test
    void neutralMasksDoNotBakeInTheReferenceCyan() throws Exception {
        for (String kind : List.of("refocusing_cube", "laser_mirror", "beam_splitter")) {
            String path = "/assets/justifylasers/textures/component/" + kind + "/";
            int luminous = 0;
            try (var stream = getClass().getResourceAsStream(path + "glow.png")) {
                var image = ImageIO.read(stream);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int p = image.getRGB(x, y);
                    assertEquals(p >> 16 & 255, p >> 8 & 255);
                    assertEquals(p >> 16 & 255, p & 255);
                    if (p >>> 24 > 25) luminous++;
                }
            }
            assertTrue(luminous > 1000, kind + " must have a usable emission mask");
            try (var stream = getClass().getResourceAsStream(path + "base_s.png")) {
                var image = ImageIO.read(stream);
                var atlas = ComponentAtlas.load(kind, "base");
                for (var region : atlas.regions().values()) for (int y = region.y(); y < region.y() + region.height(); y++)
                    for (int x = region.x(); x < region.x() + region.width(); x++) assertEquals(255, image.getRGB(x, y) >>> 24, "Idle parts cannot emit");
            }
        }
    }

    private static void verify(String kind, OpticalComponentMesh mesh, double bound) {
        var atlas = ComponentAtlas.load(kind, "base");
        assertFalse(mesh.faces().isEmpty());
        for (var f : mesh.faces()) {
            assertEquals(1, f.normal().length(), 1e-7, kind);
            for (var p : List.of(f.a(), f.b(), f.c(), f.d())) {
                assertTrue(Math.abs(p.x) <= bound && Math.abs(p.y) <= bound && Math.abs(p.z) <= bound, kind + ": " + p);
            }
            var cross = f.b().subtract(f.a()).crossProduct(f.c().subtract(f.a()));
            if (cross.lengthSquared() < 1e-14) cross = f.c().subtract(f.a()).crossProduct(f.d().subtract(f.a()));
            assertTrue(cross.dotProduct(f.normal()) > 0, kind + " winding");
            assertTrue(atlas.regions().values().stream().anyMatch(r -> List.of(f.ua(), f.ub(), f.uc(), f.ud()).stream().allMatch(r::contains)), kind + " UV");
        }
    }
}
