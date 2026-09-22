package net.askcraft.justifylasers.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightBridgeMeshTest {
    @Test void descriptorsAreCompleteQuadsForPartialAndMaximumSpans() {
        for (int width = 1; width <= 3; width++) for (double length : new double[]{.001,.75,1,2.125,256,513}) {
            int actualWidth = width;
            int[] counts = new int[5];
            LightBridgeMesh.emit(width,length,(x,y,z,kind,palette,alpha) -> {
                assertTrue(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z));
                assertTrue(kind >= 0 && kind <= 4 && palette >= 0 && palette <= 3);
                assertTrue(alpha > 0 && alpha <= 255);
                assertTrue(z >= 0 && z <= length);
                assertTrue(kind == LightBridgeMesh.FILAMENT ? x >= -1 && x <= actualWidth*3+1 : x >= 0 && x <= actualWidth);
                if (kind == LightBridgeMesh.FILAMENT) assertTrue(Math.abs(y) == .016 || Math.abs(y) == .004);
                if (kind == LightBridgeMesh.WALL) assertTrue(y == .5 || y == .4375);
                counts[kind]++;
            });
            for (int count : counts) assertEquals(0,count%4);
            assertEquals((int)Math.ceil(length/.5)*width*4*4,counts[LightBridgeMesh.DENSITY]);
            assertEquals(8,counts[LightBridgeMesh.WALL]);
            assertTrue(counts[LightBridgeMesh.FLAT] >= 36);
        }
    }

    @Test void invalidSizesCannotAllocateGpuMeshes() {
        for (int width : new int[]{0,4,-1}) assertThrows(IllegalArgumentException.class,
                () -> LightBridgeMesh.emit(width,1,(x,y,z,kind,palette,alpha) -> fail()));
        for (double length : new double[]{0,-1,513.001,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,() -> LightBridgeMesh.emit(1,length,(x,y,z,kind,palette,alpha) -> fail()));
    }
}
