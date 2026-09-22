package net.askcraft.justifylasers.printing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** MagicaVoxel 150/200: static palette voxels and the first frame of a bounded scene graph. */
public final class VoxImporter {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private record Model(int[] size, int[][] voxels) { }
    private record Node(String type, Map<String,String> attributes, int[] children, Map<String,String> frame, int layer) { }
    private record Point(int x,int y,int z,int color) { }
    private final List<Model> models = new ArrayList<>();
    private final Map<Integer,Node> nodes = new HashMap<>();
    private final Set<Integer> hiddenLayers = new HashSet<>();
    private final int[] palette = defaultPalette();
    private int[] size;
    private int visits;
    private int inputVoxels;
    private final List<Point> points = new ArrayList<>();

    public static PrintDesign read(byte[] bytes,String name) {
        if(bytes.length>MAX_BYTES)throw PrintDesign.invalid("vox_size");
        if(bytes.length<20)throw PrintDesign.invalid("vox_format");
        try { return new VoxImporter().parse(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN),name); }
        catch(IllegalArgumentException failure){throw failure;}
        catch(RuntimeException failure){throw PrintDesign.invalid("vox_format");}
    }
    private PrintDesign parse(ByteBuffer data,String name) {
        if(!tag(data).equals("VOX "))throw PrintDesign.invalid("vox_format");
        int version=data.getInt();
        if(version!=150&&version!=200)throw PrintDesign.invalid("vox_version");
        if(!tag(data).equals("MAIN"))throw PrintDesign.invalid("vox_format");
        int main=length(data,data.remaining()),children=length(data,data.remaining());
        if(main!=0||children!=data.remaining())throw PrintDesign.invalid("vox_format");
        int chunks=0;
        while(data.hasRemaining()) {
            if(data.remaining()<12||++chunks>8192)throw PrintDesign.invalid("vox_format");
            String kind=tag(data);int content=length(data,data.remaining()),nested=length(data,data.remaining());
            if((long)content+nested>data.remaining())throw PrintDesign.invalid("vox_format");
            var chunk=data.slice().order(ByteOrder.LITTLE_ENDIAN);chunk.limit(content);data.position(data.position()+content+nested);
            switch(kind) {
                case "SIZE" -> {size=new int[]{chunk.getInt(),chunk.getInt(),chunk.getInt()};for(int n:size)if(n<1||n>PrintAssembly.MAX_AXIS)throw PrintDesign.invalid("vox_bounds");}
                case "XYZI" -> {
                    if(size==null||models.size()>=256)throw PrintDesign.invalid("vox_format");
                    int count=length(chunk,PrintAssembly.MAX_VOXELS);inputVoxels+=count;if(inputVoxels>PrintAssembly.MAX_VOXELS)throw PrintDesign.invalid("assembly_limits");if(chunk.remaining()!=count*4)throw PrintDesign.invalid("vox_format");
                    var voxels=new int[count][4];var occupied=new BitSet();
                    for(var voxel:voxels) {
                        for(int i=0;i<4;i++)voxel[i]=Byte.toUnsignedInt(chunk.get());
                        if(voxel[0]>=size[0]||voxel[1]>=size[1]||voxel[2]>=size[2]||voxel[3]==0)throw PrintDesign.invalid("vox_format");
                        int at=voxel[0]+256*(voxel[1]+256*voxel[2]);if(occupied.get(at))throw PrintDesign.invalid("vox_format");occupied.set(at);
                    }
                    models.add(new Model(size,voxels));size=null;
                }
                case "RGBA" -> {if(chunk.remaining()!=1024)throw PrintDesign.invalid("vox_format");for(int i=1;i<=256;i++){int r=chunk.get()&255,g=chunk.get()&255,b=chunk.get()&255,a=chunk.get()&255;if(i<256)palette[i]=a<<24|r<<16|g<<8|b;}}
                case "nTRN","nGRP","nSHP" -> node(kind,chunk);
                case "LAYR" -> {int id=chunk.getInt();if("1".equals(dict(chunk).get("_hidden")))hiddenLayers.add(id);}
                default -> { /* Materials/cameras do not change the printed solid's geometry. */ }
            }
        }
        if(models.isEmpty())throw PrintDesign.invalid("vox_format");
        int[] identity={1,0,0,0,1,0,0,0,1},zero={0,0,0};
        if(nodes.isEmpty())model(0,identity,zero,false);
        else {
            var childrenIds=new HashSet<Integer>();for(var node:nodes.values())if(!node.type.equals("nSHP"))for(int child:node.children)childrenIds.add(child);
            var roots=nodes.keySet().stream().filter(id->!childrenIds.contains(id)).sorted().toList();
            if(roots.isEmpty())throw PrintDesign.invalid("vox_format");
            var seen=new HashSet<Integer>();for(int root:roots)walk(root,identity,zero,seen,new HashSet<>(),0);
            if(seen.size()!=nodes.size())throw PrintDesign.invalid("vox_format");
        }
        if(points.isEmpty())throw PrintDesign.invalid("vox_empty");
        int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
        for(var p:points){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);minZ=Math.min(minZ,p.z);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);maxZ=Math.max(maxZ,p.z);}
        if((long)maxX-minX>=PrintAssembly.MAX_AXIS||(long)maxY-minY>=PrintAssembly.MAX_AXIS||(long)maxZ-minZ>=PrintAssembly.MAX_AXIS)throw PrintDesign.invalid("vox_bounds");
        var materials=new HashMap<Integer,VoxelGrid.Material>();
        // MagicaVoxel is Z-up. Reversing its Y preserves handedness in Minecraft's Y-up space.
        if(maxX-minX<16&&maxY-minY<16&&maxZ-minZ<16){
            var cells=new VoxelGrid.Material[4096];int dx=(15-maxX+minX)/2,dz=(15-maxY+minY)/2;
            for(var p:points)cells[(p.x-minX+dx)+16*((maxY-p.y+dz)+16*(p.z-minZ))]=materials.computeIfAbsent(p.color,key->VoxelGrid.Material.color(key&0xFFFFFF));
            return PrintDesign.voxels(name,cells);
        }
        var parts=new HashMap<net.minecraft.util.math.BlockPos,VoxelGrid.Material[]>();
        for(var p:points){int x=p.x-minX,y=p.z-minZ,z=maxY-p.y;var at=new net.minecraft.util.math.BlockPos(x/16,y/16,z/16);
            var cells=parts.computeIfAbsent(at,key->{if(parts.size()>=PrintAssembly.MAX_PARTS)throw PrintDesign.invalid("assembly_limits");return new VoxelGrid.Material[4096];});
            cells[(x&15)+16*((z&15)+16*(y&15))]=materials.computeIfAbsent(p.color,key->VoxelGrid.Material.color(key&0xFFFFFF));
        }
        return PrintDesign.assembly(name,PrintAssembly.of(new net.minecraft.util.math.BlockPos(maxX-minX+1,maxZ-minZ+1,maxY-minY+1),parts));
    }
    private void node(String kind,ByteBuffer data) {
        int id=data.getInt();var attributes=dict(data);int[] children;var frame=Map.<String,String>of();int layer=-1;
        if(kind.equals("nTRN")) {
            children=new int[]{data.getInt()};data.getInt();layer=data.getInt();int frames=length(data,256);
            if(frames<1)throw PrintDesign.invalid("vox_format");
            int earliest=Integer.MAX_VALUE;for(int i=0;i<frames;i++){var candidate=dict(data);int at=Integer.parseInt(candidate.getOrDefault("_f","0"));if(at<earliest){frame=candidate;earliest=at;}}
        } else {
            int count=length(data,1024);children=new int[kind.equals("nSHP")?1:count];int earliest=Integer.MAX_VALUE;
            if(kind.equals("nSHP")&&count==0)throw PrintDesign.invalid("vox_format");
            for(int i=0;i<count;i++) {int child=data.getInt();if(kind.equals("nGRP"))children[i]=child;else {var f=dict(data);int at=Integer.parseInt(f.getOrDefault("_f","0"));if(at<earliest){children[0]=child;earliest=at;}}}
        }
        if(data.hasRemaining()||id<0||nodes.size()>=1024||nodes.putIfAbsent(id,new Node(kind,attributes,children,frame,layer))!=null)throw PrintDesign.invalid("vox_format");
    }
    private void walk(int id,int[] rotation,int[] translation,Set<Integer> seen,Set<Integer> path,int depth) {
        if(depth>64||++visits>4096||!path.add(id))throw PrintDesign.invalid("vox_format");
        Node node=nodes.get(id);if(node==null)throw PrintDesign.invalid("vox_format");seen.add(id);
        boolean hidden="1".equals(node.attributes.get("_hidden"))||hiddenLayers.contains(node.layer);
        if(node.type.equals("nTRN")) {
            int[] local=rotation(Integer.parseInt(node.frame.getOrDefault("_r","4")));
            String[] xyz=node.frame.getOrDefault("_t","0 0 0").trim().split("\\s+");if(xyz.length!=3)throw PrintDesign.invalid("vox_format");
            int[] offset=new int[3];for(int i=0;i<3;i++){offset[i]=Integer.parseInt(xyz[i]);if(Math.abs((long)offset[i])>1_000_000)throw PrintDesign.invalid("vox_bounds");}
            int[] next=apply(rotation,offset);for(int i=0;i<3;i++)next[i]+=translation[i];
            int[] combined=new int[9];for(int r=0;r<3;r++)for(int c=0;c<3;c++)for(int k=0;k<3;k++)combined[r*3+c]+=rotation[r*3+k]*local[k*3+c];
            if(hidden)hide(node.children[0],seen,path,depth+1);else walk(node.children[0],combined,next,seen,path,depth+1);
        } else if(node.type.equals("nGRP"))for(int child:node.children){if(hidden)hide(child,seen,path,depth+1);else walk(child,rotation,translation,seen,path,depth+1);}
        else if(!hidden)model(node.children[0],rotation,translation,true);
        path.remove(id);
    }
    private void hide(int id,Set<Integer> seen,Set<Integer> path,int depth) {
        if(depth>64||++visits>4096||!path.add(id))throw PrintDesign.invalid("vox_format");
        var node=nodes.get(id);if(node==null)throw PrintDesign.invalid("vox_format");seen.add(id);
        if(!node.type.equals("nSHP"))for(int child:node.children)hide(child,seen,path,depth+1);path.remove(id);
    }
    private void model(int id,int[] rotation,int[] offset,boolean centered) {
        if(id<0||id>=models.size())throw PrintDesign.invalid("vox_format");var model=models.get(id);
        for(var v:model.voxels) {
            int color=palette[v[3]];if((color>>>24)==0)continue;
            // Rotate voxel centers rather than lower corners to avoid mirrored half-cell offsets.
            int[] p=new int[3];for(int i=0;i<3;i++)p[i]=2*v[i]+1-(centered?2*(model.size[i]/2):0);
            p=apply(rotation,p);for(int i=0;i<3;i++)p[i]=Math.floorDiv(p[i],2)+offset[i];
            points.add(new Point(p[0],p[1],p[2],color));if(points.size()>PrintAssembly.MAX_VOXELS)throw PrintDesign.invalid("assembly_limits");
        }
    }
    private static int[] apply(int[] r,int[] p){return new int[]{r[0]*p[0]+r[1]*p[1]+r[2]*p[2],r[3]*p[0]+r[4]*p[1]+r[5]*p[2],r[6]*p[0]+r[7]*p[1]+r[8]*p[2]};}
    private static int[] rotation(int code) {
        int a=code&3,b=code>>2&3;if(code<0||code>127||a>2||b>2||a==b)throw PrintDesign.invalid("vox_format");
        int[] r=new int[9];r[a]=(code&16)==0?1:-1;r[3+b]=(code&32)==0?1:-1;r[6+3-a-b]=(code&64)==0?1:-1;return r;
    }
    private static int length(ByteBuffer data,int max){int n=data.getInt();if(n<0||n>max)throw PrintDesign.invalid("vox_format");return n;}
    private static String tag(ByteBuffer data){byte[] text=new byte[4];data.get(text);return new String(text,StandardCharsets.US_ASCII);}
    private static String string(ByteBuffer data){int count=length(data,4096);if(count>data.remaining())throw PrintDesign.invalid("vox_format");byte[] text=new byte[count];data.get(text);return new String(text,StandardCharsets.UTF_8);}
    private static Map<String,String> dict(ByteBuffer data){int count=length(data,128);var result=new HashMap<String,String>();for(int i=0;i<count;i++)if(result.put(string(data),string(data))!=null)throw PrintDesign.invalid("vox_format");return result;}
    private static int[] defaultPalette() {
        int[] result=new int[256];int at=1;
        for(int r=255;r>=0;r-=51)for(int g=255;g>=0;g-=51)for(int b=255;b>=0;b-=51)if(r+g+b>0)result[at++]=0xFF000000|r<<16|g<<8|b;
        for(int channel=0;channel<4;channel++)for(int v:new int[]{238,221,187,170,136,119,85,68,34,17})result[at++]=0xFF000000|(channel==3?v*0x010101:v<<(16-channel*8));
        return result;
    }
    private VoxImporter() { }
}
