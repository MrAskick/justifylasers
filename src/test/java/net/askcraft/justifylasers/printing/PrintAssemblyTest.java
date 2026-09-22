package net.askcraft.justifylasers.printing;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PrintAssemblyTest {
    private static VoxelGrid.Material[] solid(int rgb){var cells=new VoxelGrid.Material[4096];Arrays.fill(cells,VoxelGrid.Material.color(rgb));return cells;}
    @Test void maximumSolidPartsCompressAndRetainCoordinatesAndVoxels(){
        var parts=new HashMap<BlockPos,VoxelGrid.Material[]>();
        for(int y=0;y<8;y++)for(int z=0;z<8;z++)for(int x=0;x<8;x++)parts.put(new BlockPos(x,y,z),solid(0xA051EC));
        var assembly=PrintAssembly.of(new BlockPos(128,128,128),parts);
        assertEquals(512,assembly.count());assertEquals(2_097_152,assembly.occupied());assertTrue(assembly.encoded().length()<15_000);
        var decoded=PrintAssembly.decode(assembly.encoded());assertEquals(assembly.id(),decoded.id());
        for(int i=0;i<decoded.count();i++)assertArrayEquals(parts.get(decoded.offset(i)),decoded.cells(i));
        assertEquals(4096,decoded.preview().occupiedVoxels());assertEquals(4096,decoded.part(511,"Big").occupiedVoxels());
    }
    @Test void editingOnePartAndReplacingMaterialsPreserveTheOthers(){
        var a=solid(0xFF2200);var b=solid(0x0033FF);
        var assembly=PrintAssembly.of(new BlockPos(32,16,16),Map.of(BlockPos.ORIGIN,a,new BlockPos(1,0,0),b));
        var edited=b.clone();edited[44]=new VoxelGrid.Material("anothermod:block/casing",0xFFFFFF);
        var changed=assembly.replacePart(1,edited);assertArrayEquals(a,changed.cells(0));assertArrayEquals(edited,changed.cells(1));
        var replaced=changed.replaceMaterial(a[0],VoxelGrid.Material.color(0x11EE55));
        assertEquals(0x11EE55,replaced.cells(0)[0].tint());assertEquals(edited[44],replaced.cells(1)[44]);
        assertNotEquals(assembly.id(),changed.id());assertNotEquals(changed.id(),replaced.id());
        assertEquals(1,assembly.replacePart(0,new VoxelGrid.Material[4096]).count());
    }
    @Test void maliciousLengthsAndOutOfBoundsPartsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->PrintAssembly.decode("invalid"));
        assertThrows(IllegalArgumentException.class,()->PrintAssembly.of(new BlockPos(16,16,16),Map.of(new BlockPos(1,0,0),solid(1))));
        assertThrows(IllegalArgumentException.class,()->PrintAssembly.of(new BlockPos(257,1,1),Map.of(BlockPos.ORIGIN,solid(1))));
        assertThrows(IllegalArgumentException.class,()->PrintAssembly.of(new BlockPos(16,16,16),Map.of(BlockPos.ORIGIN,new VoxelGrid.Material[4096])));
        assertThrows(IllegalArgumentException.class,()->new PrintPart("a".repeat(64),0,1,new BlockPos(1,0,0),new BlockPos(1,1,1)));
    }
    @Test void partCoordinatesRotateAroundTheAssemblyAnchor(){
        var p=new BlockPos(2,3,1);
        assertEquals(p,PrintPart.rotate(p,Direction.NORTH));
        assertEquals(new BlockPos(-1,3,2),PrintPart.rotate(p,Direction.EAST));
        assertEquals(new BlockPos(-2,3,-1),PrintPart.rotate(p,Direction.SOUTH));
        assertEquals(new BlockPos(1,3,-2),PrintPart.rotate(p,Direction.WEST));
    }
    @Test void multipartDraftRemembersTheSelectedPartAndRenamingDoesNotChangeAssembly(){
        var model=PrintDesign.assembly("Original",PrintAssembly.of(new BlockPos(32,16,16),Map.of(BlockPos.ORIGIN,solid(1),new BlockPos(1,0,0),solid(2))));
        var draft=new EncoderDraft(model,"Edited",true,VoxelGrid.Material.color(1),5,2,4,true,1);
        var restored=EncoderDraft.parse(draft.json());assertEquals(1,restored.partIndex());assertEquals(model.json(),restored.design().json());
        assertEquals(model.assembly().id(),model.withName("Renamed").assembly().id());
        assertThrows(IllegalArgumentException.class,()->new EncoderDraft(model,"",true,VoxelGrid.Material.color(1),0,1,1,false,2));
    }
}
