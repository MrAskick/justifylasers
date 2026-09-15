package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.LaserColor;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComponentAtlasTest {
    @Test
    void everyComponentHasMatchingAlbedoNormalAndEmissionMaps() throws Exception {
        for (String kind : List.of("gun", "crystal", "turret"))
            for (LaserColor color : LaserColor.values()) checkImages(kind, color.asString());
        for (String id : LaserModuleModel.meshes().keySet()) checkImages("module/" + id, "default");
    }

    private void checkImages(String kind, String variant) throws Exception {
        var atlas = ComponentAtlas.load(kind, variant);
        for (String suffix : List.of("", "_n", "_s")) {
            String path = "assets/justifylasers/textures/component/" + kind + "/" + variant + suffix + ".png";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertEquals(atlas.size(), image.getWidth());
                assertEquals(atlas.size(), image.getHeight());
                for (var region : atlas.regions().values()) {
                    assertTrue(region.x() >= 4 && region.y() >= 4);
                    assertTrue(region.x() + region.width() + 4 <= atlas.size());
                    assertTrue(region.y() + region.height() + 4 <= atlas.size());
                    int x = region.x(), y = region.y();
                    assertEquals(image.getRGB(x, y), image.getRGB(x - 4, y - 4), "Extruded corner: " + path);
                    assertEquals(image.getRGB(x + region.width() - 1, y), image.getRGB(x + region.width() + 3, y), path);
                }
                if (suffix.equals("_s")) {
                    var metal = atlas.region("metal");
                    assertEquals(255, image.getRGB(metal.x() + metal.width()/2, metal.y() + metal.height()/2) >>> 24, path);
                    var light = atlas.region("light");
                    assertTrue((image.getRGB(light.x() + light.width()/2, light.y() + light.height()/2) >>> 24) < 255, path);
                }
            }
        }
        var regions = new ArrayList<>(atlas.regions().values());
        for (int i = 0; i < regions.size(); i++) for (int j = i+1; j < regions.size(); j++) {
            var a = regions.get(i); var b = regions.get(j);
            assertTrue(a.x() + a.width() + 8 <= b.x() || b.x() + b.width() + 8 <= a.x()
                    || a.y() + a.height() + 8 <= b.y() || b.y() + b.height() + 8 <= a.y(), "Overlapping atlas regions");
        }
    }

    @Test
    void eachFaceSamplesOneComponentRegionIncludingEveryColorLayout() {
        var gun = ComponentAtlas.load("gun", "red");
        for (var face : LaserGunModel.mesh()) check(gun, face.ua(), face.ub(), face.uc(), face.ud());
        for (LaserColor color : LaserColor.values()) {
            var crystal = ComponentAtlas.load("crystal", color.asString());
            for (var face : LaserCrystalModel.mesh(color)) check(crystal, face.ua(), face.ub(), face.uc(), face.ud());
            var turret = ComponentAtlas.load("turret", color.asString());
            for (var mesh : List.of(LaserGunModel.standMesh(color), LaserGunModel.cradleMesh(color)))
                for (var face : mesh) check(turret, face.ua(), face.ub(), face.uc(), face.ud());
        }
        LaserModuleModel.meshes().forEach((id, mesh) -> {
            var atlas = ComponentAtlas.load("module/" + id, "default");
            for (var face : mesh) check(atlas, face.ua(), face.ub(), face.uc(), face.ud());
        });
    }

    @Test
    void frontBackAndSidePanelsUseTheirOwnRegions() {
        var atlas = ComponentAtlas.load("crystal", "red");
        var skin = new ComponentAtlas.Skin(atlas.region("front"), atlas.region("back"), atlas.region("left"),
                atlas.region("right"), atlas.region("tip"), atlas.region("front"), false);
        assertSame(atlas.region("front"), skin.face(new Vec3d(0,0,-1)));
        assertSame(atlas.region("back"), skin.face(new Vec3d(0,0,1)));
        assertSame(atlas.region("left"), skin.face(new Vec3d(-1,0,0)));
        assertSame(atlas.region("right"), skin.face(new Vec3d(1,0,0)));
        for (LaserColor color : LaserColor.values()) {
            var crystal = ComponentAtlas.load("crystal", color.asString());
            var body = LaserCrystalModel.mesh(color).stream().filter(face -> face.material() == LaserCrystalModel.Material.CRYSTAL
                    && face.a().y == 4 && face.b().y == 11.75).toList();
            assertEquals(8, body.size());
            assertTrue(body.stream().anyMatch(face -> crystal.region("front").contains(face.ua())));
            assertTrue(body.stream().anyMatch(face -> crystal.region("back").contains(face.ua())));
            assertTrue(body.stream().anyMatch(face -> crystal.region("left").contains(face.ua())));
            assertTrue(body.stream().anyMatch(face -> crystal.region("right").contains(face.ua())));
        }
    }

    @Test
    void crownTrianglesShareTheSamePlanarTipInsteadOfRepeatingIt() {
        for (LaserColor color : LaserColor.values()) {
            var tip = ComponentAtlas.load("crystal", color.asString()).region("tip");
            var crown = LaserCrystalModel.mesh(color).stream().filter(face -> face.b().y == 15.75).toList();
            for (int i = 0; i < crown.size(); i++) {
                assertEquals(tip.uv(0.5, 0.5), crown.get(i).ub());
                assertEquals(crown.get(i).uc(), crown.get((i + 1) % crown.size()).ua(), "Adjacent tip edge must be continuous");
            }
        }
    }

    private static void check(ComponentAtlas atlas, ComponentAtlas.Uv... uvs) {
        for (var uv : uvs) {
            assertTrue(Float.isFinite(uv.u()) && Float.isFinite(uv.v()));
            assertTrue(uv.u() > 0 && uv.u() < 1 && uv.v() > 0 && uv.v() < 1);
        }
        assertTrue(atlas.regions().values().stream().anyMatch(region -> List.of(uvs).stream().allMatch(region::contains)),
                "A face must not cross into an unrelated component or an empty atlas gutter");
    }
}
