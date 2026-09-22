package net.askcraft.justifylasers.printing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Fixed-size editor data; palette encoding avoids thousands of duplicated JSON faces. */
public final class VoxelGrid {
    public static final String WHITE = "justifylasers:block/print_white";
    public record Material(String texture, int tint, String blockState, int modelSize) {
        public Material(String texture, int tint) { this(texture, tint, "", 1); }
        public Material {
            texture = PrintDesign.textureId(texture);
            if (tint < 0 || tint > 0xFFFFFF) throw PrintDesign.invalid("number");
            if (blockState == null || modelSize != 1 && modelSize != 16) throw PrintDesign.invalid("schematic_state");
            if (!blockState.isEmpty()) blockState = PrintBlockState.canonical(blockState);
            else modelSize = 1;
        }
        public static Material color(int rgb) { return new Material(WHITE, rgb); }
        public Material withModelSize(int size) { return new Material(texture, tint, blockState, size); }
        public boolean isBlockModel() { return !blockState.isEmpty(); }
        public JsonObject json() {
            var entry = new JsonObject(); entry.addProperty("texture", texture); entry.addProperty("tint", tint);
            if (isBlockModel()) { entry.addProperty("block", blockState); entry.addProperty("scale", modelSize); }
            return entry;
        }
        public static Material parse(JsonObject value) {
            double tint = value.get("tint").getAsDouble();
            if (!Double.isFinite(tint) || tint != Math.rint(tint) || tint < 0 || tint > 0xFFFFFF) throw PrintDesign.invalid("number");
            double size = value.has("scale") ? value.get("scale").getAsDouble() : 1;
            if (size != 1 && size != 16) throw PrintDesign.invalid("schematic_scale");
            return new Material(value.get("texture").getAsString(), (int) tint, value.has("block") ? value.get("block").getAsString() : "", (int) size);
        }
    }
    public static JsonObject encode(Material[] cells) {
        if (cells.length != 4096) throw PrintDesign.invalid("bounds");
        var palette = new LinkedHashMap<Material,Integer>();
        var data = new StringBuilder(4096 * 3);
        for (var cell : cells) {
            int index = cell == null ? 0 : palette.computeIfAbsent(cell, key -> palette.size() + 1);
            if (index > 512) throw PrintDesign.invalid("complexity");
            data.append(Character.forDigit(index >> 8,16)).append(Character.forDigit(index >> 4 & 15,16)).append(Character.forDigit(index & 15,16));
        }
        var entries = new JsonArray();
        for (var material : palette.keySet()) {
            entries.add(material.json());
        }
        var root = new JsonObject(); root.add("palette",entries); root.addProperty("data",data.toString()); return root;
    }
    public static Material[] decode(JsonObject root) {
        try {
            var entries = root.getAsJsonArray("palette");
            if (entries.size() > 512) throw PrintDesign.invalid("complexity");
            var palette = new ArrayList<Material>(); palette.add(null);
            for (var entry : entries) {
                var value = entry.getAsJsonObject();
                palette.add(Material.parse(value));
            }
            String data = root.get("data").getAsString();
            if (data.length() != 4096 * 3) throw PrintDesign.invalid("bounds");
            var cells = new Material[4096];
            for (int i=0; i<cells.length; i++) {
                int a=Character.digit(data.charAt(i*3),16),b=Character.digit(data.charAt(i*3+1),16),c=Character.digit(data.charAt(i*3+2),16);
                int index=a*256+b*16+c;
                if (a<0 || b<0 || c<0 || index>=palette.size()) throw PrintDesign.invalid("format");
                cells[i]=palette.get(index);
            }
            return cells;
        } catch (IllegalArgumentException failure) { throw failure; }
        catch (RuntimeException failure) { throw PrintDesign.invalid("format"); }
    }
    public static List<Material> palette(Material[] cells) {
        return java.util.Arrays.stream(cells).filter(java.util.Objects::nonNull).distinct().toList();
    }
    public static int index(int axis,int layer,int u,int v) {
        return switch(axis) { case 0 -> layer+16*(u+16*(15-v)); case 2 -> u+16*(layer+16*(15-v)); default -> u+16*(v+16*layer); };
    }
    public static boolean brush(Material[] cells,int axis,int layer,int u,int v,int size,boolean circle,Material material) {
        if(cells.length!=4096 || axis<0 || axis>2 || layer<0 || layer>15 || size<1 || size>16) throw PrintDesign.invalid("bounds");
        boolean changed=false; int startU=u-(size-1)/2,startV=v-(size-1)/2;
        for(int j=0;j<size;j++) for(int i=0;i<size;i++) {
            if(circle && Math.pow(i+.5-size/2d,2)+Math.pow(j+.5-size/2d,2)>size*size/4d) continue;
            int x=startU+i,y=startV+j;
            if(x<0||x>15||y<0||y>15)continue;
            int at=index(axis,layer,x,y);
            if(!java.util.Objects.equals(cells[at],material)){cells[at]=material;changed=true;}
        }
        return changed;
    }
    private VoxelGrid() { }
}
