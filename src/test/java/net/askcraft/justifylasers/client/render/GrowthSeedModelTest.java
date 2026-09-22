package net.askcraft.justifylasers.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthSeedModelTest {
    @Test void pointedClustersFitTheItemAndHaveFiniteOutwardSurfacesAtEveryWearStage() {
        int previous = Integer.MAX_VALUE;
        for (int stage=0;stage<4;stage++) {
            var mesh=GrowthSeedModel.mesh(stage);
            assertTrue(mesh.size()<previous); previous=mesh.size();
            assertEquals(0,mesh.stream().flatMap(face->face.points().stream()).mapToDouble(p->p.y).min().orElseThrow());
            for(var face:mesh) {
                assertEquals(1,face.normal().length(),1e-9);
                for(var point:face.points()) assertTrue(point.x>=0&&point.x<=16&&point.y>=0&&point.y<=16&&point.z>=0&&point.z<=16);
            }
        }
    }
}
