package net.askcraft.justifylasers.printing;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class PrintDesignTest {
    private static String cube(String faces) {return "{\"textures\":{\"a\":\"examplemod:block/metal\"},\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":"+faces+"}]}";}
    @Test void modTexturesUvsTintAndFaceRotationSurviveCanonicalRoundTrip(){
        var model=PrintDesign.parse(cube("{\"north\":{\"texture\":\"#a\",\"uv\":[1,2,13,14],\"rotation\":90,\"tint\":128}}"));
        var again=PrintDesign.parse(model.json());assertEquals(model.json(),again.json());
        var face=again.elements().get(0).faces().get(Direction.NORTH);
        assertEquals("examplemod:block/metal",face.texture());assertEquals(90,face.rotation());assertEquals(128,face.tint());
        assertEquals(1,face.u0());assertEquals(2,face.v0());assertEquals(13,face.u1());assertEquals(14,face.v1());
        assertEquals(4096,model.occupiedVoxels());
    }
    @Test void allFaceWindingsPointOutwards(){
        var cube=PrintExamples.pedestal().elements().get(0);
        for(var face:Direction.values()){
            var p=cube.corners(face);var n=p[1].subtract(p[0]).crossProduct(p[2].subtract(p[0])).normalize();
            assertEquals(1,n.dotProduct(Vec3d.of(face.getVector())),1e-9);
        }
    }
    @Test void rotationsUseRightHandedJavaBlockModelAxesAndAreInvertible(){
        for(var axis:Direction.Axis.values())for(boolean rescale:new boolean[]{false,true}) {
            var rotation=new PrintDesign.Rotation(new Vec3d(8,8,8),axis,22.5,rescale);var p=new Vec3d(6,7,9);
            assertTrue(rotation.apply(rotation.apply(p,false),true).distanceTo(p)<1e-9);
        }
        assertTrue(new PrintDesign.Rotation(Vec3d.ZERO,Direction.Axis.Y,90,false).apply(new Vec3d(1,0,0),false).distanceTo(new Vec3d(0,0,-1))<1e-9);
    }
    @Test void voxelMeshingMergesEqualMaterialsAndPreservesEveryCell(){
        String[] cells=new String[4096];Arrays.fill(cells,"minecraft:block/stone");var full=PrintDesign.voxels("Cube",cells);
        assertEquals(1,full.elements().size());assertEquals(4096,full.occupiedVoxels());
        for(int i=0;i<2048;i++)cells[i]=null;var half=PrintDesign.voxels("",cells);
        assertEquals(2048,half.occupiedVoxels());assertEquals(1,half.elements().size());
        assertFalse(half.occupied(0,0,0));assertTrue(half.occupied(0,15,0));
    }
    @Test void volumeAndComplexityIncreaseCostsWithoutRequiringMegalumens(){
        String[] cells=new String[4096];cells[0]="minecraft:block/stone";var tiny=PrintDesign.voxels("",cells).cost();
        Arrays.fill(cells,"minecraft:block/stone");var solid=PrintDesign.voxels("",cells).cost();
        assertTrue(solid.polymer()>tiny.polymer());assertTrue(solid.ticks()>tiny.ticks());assertTrue(solid.lumens()>tiny.lumens());
        assertTrue(solid.lumens()<1_000_000);assertEquals(17,solid.energyPerTick());
        cells[0]="minecraft:block/dirt";var detail=PrintDesign.voxels("",cells).cost();assertTrue(detail.polymer()>solid.polymer());
    }
    @Test void violetIsRequiredButNotAnExactRgbPreset(){
        for(int color:new int[]{0xB64EFF,0xBC09F5,0xA024EB})assertTrue(PrintCost.acceptsSpectrum(color));
        for(int color:new int[]{0xFFFFFF,0x000000,0xFF0000,0xFFCC00,0x00FF00,0x00FFFF})assertFalse(PrintCost.acceptsSpectrum(color));
    }
    @Test void overlappingGeometryDoesNotMultiplyMaterialVolume(){
        String json=cube("{\"north\":{\"texture\":\"#a\"}}");var root=PrintDesign.readObject(json);
        root.getAsJsonArray("elements").add(root.getAsJsonArray("elements").get(0).deepCopy());
        var design=PrintDesign.parse(root.toString());assertEquals(4096,design.occupiedVoxels());assertEquals(2,design.elements().size());
    }
    @Test void rejectsRemoteUrlsPathsTraversalAndAliasesCycles(){
        for(String texture:new String[]{"https://bad/image","../secret","minecraft:../a","C:/file","data:image/png;base64,xxx","block/a.png"})
            assertThrows(IllegalArgumentException.class,()->PrintDesign.textureId(texture));
        assertThrows(IllegalArgumentException.class,()->PrintDesign.parse(cube("{\"north\":{\"texture\":\"#a\"}}").replace("examplemod:block/metal","#a")));
    }
    @Test void rejectsOutOfBoundsEmptyAndNonVoxelFormats(){
        for(String value:new String[]{"{}","[]","{\"elements\":[]}",cube("{}").replace("[0,0,0]","[-1,0,0]"),"{\"minecraft:geometry\":[]}"})
            assertThrows(IllegalArgumentException.class,()->PrintDesign.parse(value));
    }
    @Test void rejectsHugeAndDeepPayloadsBeforeParsing(){
        assertThrows(IllegalArgumentException.class,()->PrintDesign.parse(" ".repeat(PrintDesign.MAX_JSON+1)));
        assertThrows(IllegalArgumentException.class,()->PrintDesign.parse("[".repeat(25)+"]".repeat(25)));
    }
    @Test void invalidUvsAndFaceAnglesAreNotClampedSilently(){
        for(String face:new String[]{"{\"north\":{\"texture\":\"#a\",\"rotation\":45}}","{\"north\":{\"texture\":\"#a\",\"uv\":[0,0,32,32]}}"})
            assertThrows(IllegalArgumentException.class,()->PrintDesign.parse(cube(face)));
    }
}
