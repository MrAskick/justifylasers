package net.askcraft.justifylasers.printing;

import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Palette/RLE storage keeps large solid models small without changing their voxel resolution. */
public final class PrintAssembly {
    public static final int MAX_AXIS = 256, MAX_PARTS = 512, MAX_VOXELS = MAX_PARTS * 4096;
    private static final int MAX_BINARY = 9 * 1024 * 1024;
    private record Section(BlockPos offset, short[] runs) { }
    private final BlockPos size;
    private final List<VoxelGrid.Material> palette;
    private final List<Section> sections;
    private final String encoded, id;
    private final Set<String> textures;
    private final int occupied;
    private final Map<Integer,PrintDesign> models = new LinkedHashMap<>(4,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,PrintDesign> entry) { return size()>4; }
    };
    private PrintDesign preview;

    private PrintAssembly(BlockPos size,List<VoxelGrid.Material> palette,List<Section> sections) {
        this.size=size.toImmutable(); this.palette=List.copyOf(palette); this.sections=List.copyOf(sections);
        if(size.getX()<1||size.getY()<1||size.getZ()<1||size.getX()>MAX_AXIS||size.getY()>MAX_AXIS||size.getZ()>MAX_AXIS
                ||palette.isEmpty()||palette.size()>512||sections.isEmpty()||sections.size()>MAX_PARTS)throw PrintDesign.invalid("assembly_limits");
        var used=new TreeSet<String>(); for(var material:palette)used.add(material.texture());
        if(used.size()>PrintDesign.MAX_TEXTURES)throw PrintDesign.invalid("textures"); textures=Collections.unmodifiableSet(used);
        int voxels=0;var offsets=new HashSet<BlockPos>();
        for(var section:sections) {
            var p=section.offset;
            if(p.getX()<0||p.getY()<0||p.getZ()<0||p.getX()*16>=size.getX()||p.getY()*16>=size.getY()||p.getZ()*16>=size.getZ()
                    ||!offsets.add(p)||section.runs.length<2||section.runs.length>8192||section.runs.length%2!=0)throw PrintDesign.invalid("assembly");
            int at=0,filled=0;
            for(int i=0;i<section.runs.length;i+=2){int count=section.runs[i]&65535,index=section.runs[i+1]&65535;
                if(count<1||at+count>4096||index>palette.size())throw PrintDesign.invalid("assembly");
                if(index>0&&palette.get(index-1).modelSize()==16&&(section.runs.length!=2||count!=4096))throw PrintDesign.invalid("schematic_cells");
                if(index>0) { filled+=count;for(int v=at;v<at+count;v++)if(p.getX()*16+(v&15)>=size.getX()||p.getY()*16+(v>>8)>=size.getY()||p.getZ()*16+(v>>4&15)>=size.getZ())throw PrintDesign.invalid("assembly"); }
                at+=count;
            }
            if(at!=4096||filled==0)throw PrintDesign.invalid("assembly");voxels+=filled;
        }
        occupied=voxels;encoded=encode();
        try{id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static PrintAssembly of(BlockPos size,Map<BlockPos,VoxelGrid.Material[]> parts) {
        if(parts.size()>MAX_PARTS)throw PrintDesign.invalid("assembly_limits");
        var palette=new LinkedHashMap<VoxelGrid.Material,Integer>();var sections=new ArrayList<Section>();
        parts.entrySet().stream().sorted(Comparator.<Map.Entry<BlockPos,VoxelGrid.Material[]>>comparingInt(e->e.getKey().getY()).thenComparingInt(e->e.getKey().getZ()).thenComparingInt(e->e.getKey().getX())).forEach(entry->{
            var cells=entry.getValue();if(cells.length!=4096)throw PrintDesign.invalid("assembly");
            var indices=new short[4096];boolean nonempty=false;
            for(int i=0;i<cells.length;i++)if(cells[i]!=null){int index=palette.computeIfAbsent(cells[i],key->palette.size()+1);if(index>512)throw PrintDesign.invalid("complexity");indices[i]=(short)index;nonempty=true;}
            if(nonempty)sections.add(new Section(entry.getKey().toImmutable(),runs(indices)));
        });
        return new PrintAssembly(size,new ArrayList<>(palette.keySet()),sections);
    }
    private static short[] runs(short[] indices) {
        var values=new short[8192];int n=0;
        for(int i=0;i<4096;){int end=i+1;while(end<4096&&indices[end]==indices[i])end++;values[n++]=(short)(end-i);values[n++]=indices[i];i=end;}
        return Arrays.copyOf(values,n);
    }
    public BlockPos size() { return size; }
    public BlockPos blockSize() { return new BlockPos((size.getX()+15)/16,(size.getY()+15)/16,(size.getZ()+15)/16); }
    public int count() { return sections.size(); }
    public int occupied() { return occupied; }
    public String encoded() { return encoded; }
    public String id() { return id; }
    public Set<String> textures() { return textures; }
    public List<VoxelGrid.Material> palette() { return palette; }
    public boolean hasBlockModels() { return palette.stream().anyMatch(VoxelGrid.Material::isBlockModel); }
    public int modelCount() {
        int count=0;
        for(var section:sections)for(int i=0;i<section.runs.length;i+=2){int index=section.runs[i+1]&65535;
            if(index>0){int size=palette.get(index-1).modelSize();count+=(section.runs[i]&65535)/(size*size*size);}}
        return count;
    }
    public BlockPos offset(int index) { return sections.get(index).offset; }
    public VoxelGrid.Material[] cells(int index) {
        var section=sections.get(index);var result=new VoxelGrid.Material[4096];int at=0;
        for(int i=0;i<section.runs.length;i+=2){int end=at+(section.runs[i]&65535),material=section.runs[i+1]&65535;Arrays.fill(result,at,end,material==0?null:palette.get(material-1));at=end;}return result;
    }
    public synchronized PrintDesign part(int index,String name) {
        if(index<0||index>=count())throw PrintDesign.invalid("assembly");
        var model=models.get(index);
        if(model==null)model=PrintDesign.voxels(name,cells(index)).withPart(new PrintPart(id,index,count(),offset(index),blockSize()));
        else model=model.withName(name);
        models.put(index,model);return model;
    }
    public PrintAssembly replaceMaterial(VoxelGrid.Material from,VoxelGrid.Material to) {
        if(from.equals(to)||!palette.contains(from))return this;
        var changed=new ArrayList<>(palette);Collections.replaceAll(changed,from,to);return new PrintAssembly(size,changed,sections);
    }
    public PrintAssembly replacePart(int index,VoxelGrid.Material[] cells) {
        if(cells.length!=4096||index<0||index>=count())throw PrintDesign.invalid("assembly");
        var changed=new ArrayList<>(palette);var lookup=new HashMap<VoxelGrid.Material,Integer>();
        for(int i=0;i<changed.size();i++)lookup.putIfAbsent(changed.get(i),i+1);
        var indices=new short[4096];boolean nonempty=false;
        for(int i=0;i<cells.length;i++)if(cells[i]!=null){
            var value=lookup.get(cells[i]);if(value==null){if(changed.size()>=512)throw PrintDesign.invalid("complexity");changed.add(cells[i]);value=changed.size();lookup.put(cells[i],value);}
            indices[i]=value.shortValue();nonempty=true;
        }
        var parts=new ArrayList<>(sections);if(nonempty)parts.set(index,new Section(offset(index),runs(indices)));else parts.remove(index);
        return new PrintAssembly(size,changed,parts);
    }
    public synchronized PrintDesign preview() {
        if(preview!=null)return preview;
        var cells=new VoxelGrid.Material[4096];double scale=16d/Math.max(size.getX(),Math.max(size.getY(),size.getZ()));
        int dx=(16-(int)Math.ceil(size.getX()*scale))/2,dz=(16-(int)Math.ceil(size.getZ()*scale))/2;
        for(int part=0;part<count();part++){var source=cells(part);var pos=offset(part);
            for(int v=0;v<4096;v++)if(source[v]!=null){int x=dx+(int)((pos.getX()*16+(v&15))*scale),y=(int)((pos.getY()*16+(v>>8))*scale),z=dz+(int)((pos.getZ()*16+(v>>4&15))*scale);cells[x+16*(z+16*y)]=source[v].withModelSize(1);}
        }
        return preview=PrintDesign.voxels("",cells);
    }
    private String encode() {
        try {
            var bytes=new ByteArrayOutputStream();
            try(var output=new DataOutputStream(new DeflaterOutputStream(bytes))) {
                boolean blocks=palette.stream().anyMatch(VoxelGrid.Material::isBlockModel);
                output.writeInt(blocks?0x4A4C4102:0x4A4C4101);output.writeShort(size.getX());output.writeShort(size.getY());output.writeShort(size.getZ());output.writeShort(palette.size());
                for(var material:palette){output.writeUTF(material.texture());output.writeInt(material.tint());if(blocks){output.writeUTF(material.blockState());output.writeByte(material.modelSize());}}
                output.writeShort(count());for(var section:sections){output.writeByte(section.offset.getX());output.writeByte(section.offset.getY());output.writeByte(section.offset.getZ());output.writeShort(section.runs.length/2);for(short value:section.runs)output.writeShort(value&65535);}
            }
            String result=Base64.getEncoder().encodeToString(bytes.toByteArray());if(result.length()>PrintDesign.MAX_DOCUMENT_JSON-256)throw PrintDesign.invalid("assembly_limits");return result;
        } catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    public static PrintAssembly decode(String encoded) {
        if(encoded.length()>PrintDesign.MAX_DOCUMENT_JSON)throw PrintDesign.invalid("assembly_limits");
        try(var stream=new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            byte[] bytes=stream.readNBytes(MAX_BINARY+1);if(bytes.length>MAX_BINARY)throw PrintDesign.invalid("assembly_limits");
            var input=new DataInputStream(new ByteArrayInputStream(bytes));int version=input.readInt();if(version!=0x4A4C4101&&version!=0x4A4C4102)throw PrintDesign.invalid("assembly");
            var size=new BlockPos(input.readUnsignedShort(),input.readUnsignedShort(),input.readUnsignedShort());int n=input.readUnsignedShort();
            if(n<1||n>512)throw PrintDesign.invalid("assembly");var palette=new ArrayList<VoxelGrid.Material>();
            for(int i=0;i<n;i++){String texture=input.readUTF();int tint=input.readInt();palette.add(version==0x4A4C4101?new VoxelGrid.Material(texture,tint):new VoxelGrid.Material(texture,tint,input.readUTF(),input.readUnsignedByte()));}
            n=input.readUnsignedShort();if(n<1||n>MAX_PARTS)throw PrintDesign.invalid("assembly_limits");var sections=new ArrayList<Section>();
            for(int i=0;i<n;i++){var pos=new BlockPos(input.readUnsignedByte(),input.readUnsignedByte(),input.readUnsignedByte());int count=input.readUnsignedShort();if(count<1||count>4096)throw PrintDesign.invalid("assembly");var runs=new short[count*2];for(int j=0;j<runs.length;j++)runs[j]=input.readShort();sections.add(new Section(pos,runs));}
            if(input.available()!=0)throw PrintDesign.invalid("assembly");return new PrintAssembly(size,palette,sections);
        } catch(IOException|IllegalArgumentException failure){throw PrintDesign.invalid("assembly");}
    }
}
