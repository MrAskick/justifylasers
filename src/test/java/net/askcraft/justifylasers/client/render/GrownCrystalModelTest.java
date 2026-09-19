package net.askcraft.justifylasers.client.render;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class GrownCrystalModelTest {
    @Test void clusterMatchesRawPhotoniteAndRestsOnTheMountingFace() {
        assertEquals(18,GrownCrystalModel.FACES.size());
        var points=GrownCrystalModel.FACES.stream().flatMap(face->face.points().stream()).toList();
        assertEquals(0,points.stream().mapToDouble(p->p.y).min().orElseThrow(),1e-9);
        for(var face:GrownCrystalModel.FACES) {
            assertEquals(1,face.normal().length(),1e-8);
            for(var p:face.points()) assertTrue(p.x>=0 && p.x<=16 && p.y>=0 && p.y<=16 && p.z>=0 && p.z<=16);
        }
    }
    @Test void crystalAndBucketTexturesAreSmallRuntimeAssetsWithShaderMaterials() throws Exception {
        for(String kind:new String[]{"amethyst","diamond","emerald","photonite"}) {
            for(String map:new String[]{"base","base_n","base_s"}) {
                var image=ImageIO.read(getClass().getResource("/assets/justifylasers/textures/component/grown_"+kind+"_crystal/"+map+".png"));
                assertEquals(128,image.getWidth()); assertEquals(128,image.getHeight());
            }
            var bucket=ImageIO.read(getClass().getResource("/assets/justifylasers/textures/item/"+kind+"_nutrient_bucket.png"));
            assertEquals(16,bucket.getWidth()); assertEquals(16,bucket.getHeight());
        }
    }
}
