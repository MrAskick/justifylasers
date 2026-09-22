package net.askcraft.justifylasers.printing;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VoxImporterTest {
    private static byte[] join(byte[]... parts){var data=new java.io.ByteArrayOutputStream();for(var p:parts)data.writeBytes(p);return data.toByteArray();}
    private static byte[] ints(int... values){var b=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(int value:values)b.putInt(value);return b.array();}
    private static byte[] text(String value){var bytes=value.getBytes(StandardCharsets.UTF_8);return join(ints(bytes.length),bytes);}
    private static byte[] dict(String... values){var data=new java.io.ByteArrayOutputStream();data.writeBytes(ints(values.length/2));for(var value:values)data.writeBytes(text(value));return data.toByteArray();}
    private static byte[] chunk(String name,byte[] data){return join(name.getBytes(StandardCharsets.US_ASCII),ints(data.length,0),data);}
    private static byte[] file(byte[]... chunks){var data=join(chunks);return join("VOX ".getBytes(StandardCharsets.US_ASCII),ints(200),"MAIN".getBytes(StandardCharsets.US_ASCII),ints(0,data.length),data);}
    private static byte[] size(int x,int y,int z){return chunk("SIZE",ints(x,y,z));}
    private static byte[] vox(int... values){var data=new byte[values.length];for(int i=0;i<values.length;i++)data[i]=(byte)values[i];return chunk("XYZI",join(ints(values.length/4),data));}
    private static byte[] rgba(){var data=new byte[1024];for(int i=0;i<256;i++){data[i*4]=(byte)i;data[i*4+1]=(byte)(255-i);data[i*4+2]=45;data[i*4+3]=(byte)255;}return chunk("RGBA",data);}
    @Test void paletteAndZUpGeometryRoundTripWithoutTextureReplacement(){
        var model=VoxImporter.read(file(size(4,3,2),vox(0,0,0,1,3,2,1,2),rgba()),"VOX test");
        assertEquals(2,model.occupiedVoxels());assertTrue(model.occupied(6,0,8));assertTrue(model.occupied(9,1,6));
        var cells=model.voxelCells();assertEquals(0x00FF2D,cells[6+16*8].tint());assertEquals(VoxelGrid.WHITE,cells[6+16*8].texture());
        assertEquals(model.json(),PrintDesign.parse(model.json()).json());
    }
    @Test void legacyFilesWithoutPaletteUseOfficialDefaultColors(){
        byte[] data=file(size(1,1,1),vox(0,0,0,1));ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(4,150);
        assertEquals(0xFFFFFF,VoxelGrid.palette(VoxImporter.read(data,"").voxelCells()).get(0).tint());
    }
    @Test void scenesApplyRotationTranslationAndIgnoreHiddenLayers(){
        var data=file(size(2,1,1),vox(0,0,0,1,1,0,0,2),rgba(),
                chunk("nTRN",join(ints(0),dict(),ints(1,-1,0,1),dict("_r","17","_t","5 6 0"))),
                chunk("nSHP",join(ints(1),dict(),ints(1,0),dict())));
        var model=VoxImporter.read(data,"");assertEquals(2,model.occupiedVoxels());
        int minX=16,maxX=0,minZ=16,maxZ=0;for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(model.occupied(x,0,z)){minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);}
        assertEquals(minX,maxX);assertEquals(1,maxZ-minZ);
        var hidden=file(size(1,1,1),vox(0,0,0,1),chunk("LAYR",join(ints(3),dict("_hidden","1"),ints(-1))),
                chunk("nTRN",join(ints(0),dict(),ints(1,-1,3,1),dict())),chunk("nSHP",join(ints(1),dict(),ints(1,0),dict())));
        assertThrows(IllegalArgumentException.class,()->VoxImporter.read(hidden,""));
    }
    @Test void unknownChunksAreSkippedAndOversizedCanvasesAreRejected(){
        assertEquals(1,VoxImporter.read(file(chunk("JUNK",new byte[99]),size(1,1,1),vox(0,0,0,1)),"").occupiedVoxels());
        assertEquals(1,VoxImporter.read(file(size(256,1,1),vox(0,0,0,1)),"").occupiedVoxels());
        assertThrows(IllegalArgumentException.class,()->VoxImporter.read(file(size(257,1,1),vox(0,0,0,1)),""));
    }
    @Test void largeVoxIsSplitAtBlockBoundariesWithoutResampling(){
        var model=VoxImporter.read(file(size(40,18,20),vox(0,0,0,1,15,0,0,2,16,0,0,3,39,17,19,4),rgba()),"Large VOX");
        assertNotNull(model.assembly());assertEquals(new net.minecraft.util.math.BlockPos(40,20,18),model.assembly().size());
        assertEquals(4,model.occupiedVoxels());assertEquals(3,model.partCount());
        var decoded=PrintDesign.parse(model.json());assertEquals(model.json(),decoded.json());
        var cells=decoded.assembly().cells(0);assertEquals(0x00FF2D,cells[16].tint());assertEquals(0x01FE2D,cells[31].tint());
        assertEquals(new net.minecraft.util.math.BlockPos(2,1,0),decoded.assembly().offset(2));
        for(int i=0;i<model.partCount();i++){var part=model.part(i);assertEquals(i,part.partInfo().index());assertEquals(part.json(),PrintDesign.parse(part.json()).json());}
    }
    @Test void rejectsTruncationOversizedLengthsDuplicatesAndGraphCycles(){
        var valid=file(size(1,1,1),vox(0,0,0,1));
        for(int i=0;i<valid.length;i++){var truncated=Arrays.copyOf(valid,i);assertThrows(IllegalArgumentException.class,()->VoxImporter.read(truncated,""));}
        byte[] huge=valid.clone();ByteBuffer.wrap(huge).order(ByteOrder.LITTLE_ENDIAN).putInt(24,Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->VoxImporter.read(huge,""));
        assertThrows(IllegalArgumentException.class,()->VoxImporter.read(file(size(1,1,1),vox(0,0,0,1,0,0,0,2)),""));
        var cyclic=file(size(1,1,1),vox(0,0,0,1),chunk("nGRP",join(ints(0),dict(),ints(1,0))));
        assertThrows(IllegalArgumentException.class,()->VoxImporter.read(cyclic,""));
    }
    @Test void fullMulticolorGridUsesCompactStorageAndRemainsBelowMegalumens(){
        var cells=new VoxelGrid.Material[4096];for(int i=0;i<cells.length;i++)cells[i]=VoxelGrid.Material.color(i%255*0x010101);
        var model=PrintDesign.voxels("Detailed",cells);assertEquals(4096,model.occupiedVoxels());assertTrue(model.elements().size()>512);
        assertTrue(model.json().length()<PrintDesign.MAX_JSON);assertArrayEquals(cells,PrintDesign.parse(model.json()).voxelCells());
        assertTrue(model.cost().lumens()<1_000_000);assertTrue(model.faceCount()<4096*6);
    }
    @Test void squareAndRoundBrushesEraseAndStayInsideEveryPlane(){
        for(int axis=0;axis<3;axis++){
            var cells=new VoxelGrid.Material[4096];var color=VoxelGrid.Material.color(0xAC45EF);
            assertTrue(VoxelGrid.brush(cells,axis,8,7,7,16,false,color));assertEquals(256,VoxelGrid.palette(cells).isEmpty()?0:Arrays.stream(cells).filter(Objects::nonNull).count());
            assertTrue(VoxelGrid.brush(cells,axis,8,7,7,16,true,null));long remaining=Arrays.stream(cells).filter(Objects::nonNull).count();assertTrue(remaining>0&&remaining<100);
            VoxelGrid.brush(cells,axis,8,0,0,3,false,color);assertTrue(Arrays.stream(cells).filter(Objects::nonNull).count()<=remaining+4);
        }
    }
    @Test void draftKeepsNameMaterialsAndEditorControlsIncludingEmptyModels(){
        var selected=new VoxelGrid.Material("somemod:block/panel",0xFFCC22);var draft=new EncoderDraft(PrintExamples.pedestal(),"Name",true,selected,9,2,16,true);
        var read=EncoderDraft.parse(draft.json());assertEquals(draft.json(),read.json());assertEquals(16,read.brush());assertEquals(selected,read.selected());
        assertNull(EncoderDraft.parse(new EncoderDraft(null,"Empty",true,selected,0,1,1,false).json()).design());
        assertThrows(IllegalArgumentException.class,()->new EncoderDraft(null,"Bad\nname",true,selected,0,1,1,false));
        var malformed=PrintDesign.readObject(draft.json());malformed.addProperty("tint",1.5);
        assertThrows(IllegalArgumentException.class,()->EncoderDraft.parse(malformed.toString()));
        assertThrows(IllegalArgumentException.class,()->PrintDesign.voxels("Bad§name",PrintExamples.pedestal().voxelCells()));
        var cells=new VoxelGrid.Material[4096];for(int i=0;i<cells.length;i++)cells[i]=new VoxelGrid.Material("test:"+"p".repeat(62),i%512);
        var large=new EncoderDraft(PrintDesign.voxels("n".repeat(48),cells),"n".repeat(48),true,new VoxelGrid.Material("test:"+"p".repeat(155),0xFFFFFF),15,2,16,true);
        assertTrue(large.json().length()>PrintDesign.MAX_JSON);assertEquals(large.json(),EncoderDraft.parse(large.json()).json());
    }
    @Test void slicingPreservesUvsAndProducesAnUpwardCap(){
        var face=List.of(new PrintSlice.Vertex(new net.minecraft.util.math.Vec3d(0,0,0),0,16),new PrintSlice.Vertex(new net.minecraft.util.math.Vec3d(0,16,0),0,0),new PrintSlice.Vertex(new net.minecraft.util.math.Vec3d(16,16,0),16,0),new PrintSlice.Vertex(new net.minecraft.util.math.Vec3d(16,0,0),16,16));
        var sliced=PrintSlice.below(face,8);assertEquals(4,sliced.size());assertTrue(sliced.stream().allMatch(v->v.position().y<=8));assertTrue(sliced.stream().filter(v->v.position().y==8).allMatch(v->v.v()==8));
        var cube=PrintExamples.pedestal().elements().get(0);var cap=PrintSlice.cap(cube,1);
        assertEquals(4,cap.size());assertTrue(cap.get(1).position().subtract(cap.get(0).position()).crossProduct(cap.get(2).position().subtract(cap.get(0).position())).y>0);
    }
    @Test void materialResourcesAreSharpAndPolymerHasTwentyPercentTransparency() throws Exception {
        for(String name:new String[]{"photopolymer_still","photopolymer_flow"})try(var stream=getClass().getResourceAsStream("/assets/justifylasers/textures/block/"+name+".png")){
            assertNotNull(stream);var image=javax.imageio.ImageIO.read(stream);assertEquals(8*image.getWidth(),image.getHeight());
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)assertEquals(204,image.getRGB(x,y)>>>24);
        }
        try(var stream=getClass().getResourceAsStream("/assets/justifylasers/textures/component/photopolymer_printer/base.png")){
            assertNotNull(stream);var image=javax.imageio.ImageIO.read(stream);assertEquals(128,image.getWidth());
            var colors=new HashSet<Integer>();for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)colors.add(image.getRGB(x,y));
            assertTrue(colors.size()<150,"Native pixel-art colors are not blurred by atlas repacking");
        }
    }
}
