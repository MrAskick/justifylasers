package net.askcraft.justifylasers.printing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A bounded, self-contained block model. Resource loading belongs to the client, never the design. */
public final class PrintDesign {
    public static final int MAX_ELEMENTS = 512, MAX_JSON = 60_000, MAX_TEXTURES = 128;
    public static final int MAX_DOCUMENT_JSON = 1_000_000;
    public static final int GRID = 16;
    private final String name;
    private final List<Element> elements;
    private final String json;
    private final int occupiedVoxels;
    private final BitSet occupied;
    private final Set<String> textures;
    private final VoxelGrid.Material[] voxelCells;
    private final PrintAssembly assembly;
    private final PrintPart part;

    public record Face(String texture, double u0, double v0, double u1, double v1, int rotation, int tint) { }
    public record Rotation(Vec3d origin, Direction.Axis axis, double degrees, boolean rescale) {
        public Vec3d apply(Vec3d point, boolean inverse) {
            Vec3d local = point.subtract(origin);
            double angle = Math.toRadians(degrees), scale = rescale ? 1 / Math.abs(Math.cos(angle)) : 1;
            if (inverse) local = scale(local, 1 / scale);
            double sin = Math.sin(inverse ? -angle : angle), cos = Math.cos(angle);
            local = switch (axis) {
                case X -> new Vec3d(local.x, local.y*cos-local.z*sin, local.y*sin+local.z*cos);
                case Y -> new Vec3d(local.x*cos+local.z*sin, local.y, -local.x*sin+local.z*cos);
                case Z -> new Vec3d(local.x*cos-local.y*sin, local.x*sin+local.y*cos, local.z);
            };
            if (!inverse) local = scale(local, scale);
            return local.add(origin);
        }
        private Vec3d scale(Vec3d p, double s) {
            return new Vec3d(p.x * (axis == Direction.Axis.X ? 1 : s), p.y * (axis == Direction.Axis.Y ? 1 : s), p.z * (axis == Direction.Axis.Z ? 1 : s));
        }
    }
    public record Element(Vec3d from, Vec3d to, Rotation rotation, Map<Direction, Face> faces, String blockState) {
        public Element(Vec3d from, Vec3d to, Rotation rotation, Map<Direction, Face> faces) { this(from, to, rotation, faces, ""); }
        public Element { faces = Collections.unmodifiableMap(new EnumMap<>(faces)); }
        public Vec3d transform(Vec3d point) { return rotation == null ? point : rotation.apply(point, false); }
        public net.minecraft.util.math.Box bounds() {
            if (rotation == null) return new net.minecraft.util.math.Box(from, to);
            Vec3d min = new Vec3d(16,16,16), max = Vec3d.ZERO;
            for (var side : Direction.values()) for (var p : corners(side)) {
                min = new Vec3d(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));
                max = new Vec3d(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));
            }
            return new net.minecraft.util.math.Box(min,max);
        }
        public boolean contains(double x, double y, double z) {
            Vec3d p = new Vec3d(x, y, z);
            if (rotation != null) p = rotation.apply(p, true);
            return p.x >= from.x && p.y >= from.y && p.z >= from.z && p.x < to.x && p.y < to.y && p.z < to.z;
        }
        public Vec3d[] corners(Direction face) {
            double x = from.x, y = from.y, z = from.z, X = to.x, Y = to.y, Z = to.z;
            Vec3d[] points = switch (face) {
                case NORTH -> new Vec3d[]{new Vec3d(X,y,z),new Vec3d(x,y,z),new Vec3d(x,Y,z),new Vec3d(X,Y,z)};
                case SOUTH -> new Vec3d[]{new Vec3d(x,y,Z),new Vec3d(X,y,Z),new Vec3d(X,Y,Z),new Vec3d(x,Y,Z)};
                case WEST -> new Vec3d[]{new Vec3d(x,y,z),new Vec3d(x,y,Z),new Vec3d(x,Y,Z),new Vec3d(x,Y,z)};
                case EAST -> new Vec3d[]{new Vec3d(X,y,Z),new Vec3d(X,y,z),new Vec3d(X,Y,z),new Vec3d(X,Y,Z)};
                case UP -> new Vec3d[]{new Vec3d(x,Y,Z),new Vec3d(X,Y,Z),new Vec3d(X,Y,z),new Vec3d(x,Y,z)};
                case DOWN -> new Vec3d[]{new Vec3d(x,y,z),new Vec3d(X,y,z),new Vec3d(X,y,Z),new Vec3d(x,y,Z)};
            };
            for (int i = 0; i < 4; i++) points[i] = transform(points[i]);
            return points;
        }
    }

    private PrintDesign(String name, List<Element> elements) {
        this(name, elements, null);
    }
    private PrintDesign(String name, List<Element> elements, VoxelGrid.Material[] cells) {
        assembly=null;part=null;
        this.name = name == null ? "" : name.substring(0, Math.min(48, name.length())); this.elements = List.copyOf(elements);
        if(this.name.chars().anyMatch(c -> Character.isISOControl(c) || c == 0xA7))throw invalid("name");
        voxelCells = cells == null ? null : cells.clone();
        var used = new java.util.TreeSet<String>();
        for (var cube : elements) for (var face : cube.faces.values()) used.add(face.texture);
        if (used.size() > MAX_TEXTURES) throw invalid("textures");
        textures = Collections.unmodifiableSet(used);
        occupied = new BitSet(4096);
        for (var cube : elements) {
            // Unrotated micro-details still pay for the cells they intersect.
            var bounds = cube.bounds();
            boolean found = false;
            for (int y = Math.max(0,(int)Math.floor(bounds.minY)); y < Math.min(16,Math.ceil(bounds.maxY)); y++)
                for (int z = Math.max(0,(int)Math.floor(bounds.minZ)); z < Math.min(16,Math.ceil(bounds.maxZ)); z++)
                    for (int x = Math.max(0,(int)Math.floor(bounds.minX)); x < Math.min(16,Math.ceil(bounds.maxX)); x++) {
                boolean inside = cube.rotation == null
                        ? cube.from.x < x + 1 && cube.to.x > x && cube.from.y < y + 1 && cube.to.y > y && cube.from.z < z + 1 && cube.to.z > z
                        : cube.contains(x + .5, y + .5, z + .5);
                if (inside) { occupied.set(x + 16 * (z + y * 16)); found = true; }
            }
            // Sub-voxel rotated details must remain pickable and cannot be free material.
            if (!found) for (int y=Math.max(0,(int)Math.floor(bounds.minY));y<Math.min(16,Math.ceil(bounds.maxY));y++)
                for(int z=Math.max(0,(int)Math.floor(bounds.minZ));z<Math.min(16,Math.ceil(bounds.maxZ));z++)
                    for(int x=Math.max(0,(int)Math.floor(bounds.minX));x<Math.min(16,Math.ceil(bounds.maxX));x++) occupied.set(x+16*(z+y*16));
        }
        occupiedVoxels = Math.max(1, occupied.cardinality());
        json = serialize();
        if (json.length() > MAX_JSON) throw invalid("size");
    }

    private PrintDesign(String name,PrintAssembly assembly) {
        this.name=cleanName(name);this.assembly=assembly;part=null;elements=List.of();voxelCells=null;
        occupied=new BitSet();occupiedVoxels=assembly.occupied();textures=assembly.textures();json=serialize();
        if(json.length()>MAX_DOCUMENT_JSON)throw invalid("assembly_limits");
    }
    private PrintDesign(PrintDesign source,String name,PrintPart part) {
        this.name=cleanName(name);this.part=part;assembly=source.assembly;elements=source.elements;voxelCells=source.voxelCells;
        occupied=source.occupied;occupiedVoxels=source.occupiedVoxels;textures=source.textures;json=serialize();
        if(json.length()>(assembly==null?MAX_JSON+1024:MAX_DOCUMENT_JSON))throw invalid("size");
    }
    private static String cleanName(String name){String value=name==null?"":name.substring(0,Math.min(48,name.length()));if(value.chars().anyMatch(c->Character.isISOControl(c)||c==0xA7))throw invalid("name");return value;}
    public static PrintDesign assembly(String name,PrintAssembly assembly){return new PrintDesign(name,assembly);}
    public PrintAssembly assembly(){return assembly;}
    public PrintPart partInfo(){return part;}
    public int partCount(){return assembly==null?1:assembly.count();}
    public PrintDesign part(int index){return assembly==null?this:assembly.part(index,name);}
    public PrintDesign preview(){return assembly==null?this:assembly.preview();}
    public PrintDesign withName(String value){return name.equals(value)?this:new PrintDesign(this,value,part);}
    public PrintDesign withPart(PrintPart value){if(assembly!=null)throw invalid("assembly");return new PrintDesign(this,name,value);}

    public String name() { return name; }
    public VoxelGrid.Material[] voxelCells() { return voxelCells == null ? null : voxelCells.clone(); }
    public boolean hasBlockModels() { return voxelCells != null && java.util.Arrays.stream(voxelCells).anyMatch(material -> material != null && material.isBlockModel()); }
    public Face capMaterial(Element cube) {
        if(cube.faces.containsKey(Direction.UP))return cube.faces.get(Direction.UP);
        if(!cube.faces.isEmpty())return cube.faces.values().iterator().next();
        if(voxelCells==null)return null;
        var material=voxelCells[(int)cube.from.x+16*((int)cube.from.z+16*(int)cube.from.y)];
        return material==null?null:new Face(material.texture(),0,0,16,16,0,material.tint());
    }
    public List<Element> elements() { return elements; }
    public Set<String> textures() { return textures; }
    public String json() { return json; }
    public int occupiedVoxels() { return occupiedVoxels; }
    public boolean occupied(int x, int y, int z) { return occupied.get(x+16*(z+16*y)); }
    public int faceCount() { return elements.stream().mapToInt(element -> element.faces.size()).sum(); }
    public PrintCost cost() { return PrintCost.of(assembly==null?this:part(0)); }

    public static PrintDesign parse(String json) {
        JsonObject root = readObject(json);
        if(root.has("assembly")) {
            try { if(root.has("voxels")||root.has("elements")||root.has("part"))throw invalid("assembly");return assembly(root.has("name")?root.get("name").getAsString():"",PrintAssembly.decode(root.get("assembly").getAsString())); }
            catch(RuntimeException failure){throw invalid("assembly");}
        }
        if (root.has("voxels")) {
            try { var design=voxels(root.has("name") ? root.get("name").getAsString() : "", VoxelGrid.decode(root.getAsJsonObject("voxels")));return root.has("part")?design.withPart(PrintPart.parse(root.getAsJsonObject("part"))):design; }
            catch (IllegalArgumentException failure) { throw failure; }
            catch (RuntimeException failure) { throw invalid("format"); }
        }
        if (!root.has("elements") || !root.get("elements").isJsonArray()) throw invalid("format");
        if (root.has("animations") || root.has("minecraft:geometry")) throw invalid("format");
        if (root.has("textures") && !root.get("textures").isJsonObject()) throw invalid("format");
        var textures = root.has("textures") ? root.getAsJsonObject("textures") : new JsonObject();
        if (textures.size() > MAX_TEXTURES * 2) throw invalid("textures");
        var shapes = root.getAsJsonArray("elements");
        if (shapes.isEmpty() || shapes.size() > MAX_ELEMENTS) throw invalid("complexity");
        var elements = new ArrayList<Element>();
        try {
            for (JsonElement entry : shapes) {
                JsonObject cube = entry.getAsJsonObject();
                if (cube.has("type") && !cube.get("type").getAsString().equals("cube")) throw invalid("format");
                Vec3d from = point(cube.get("from")), to = point(cube.get("to"));
                if (from.x >= to.x || from.y >= to.y || from.z >= to.z) throw invalid("bounds");
                Rotation rotation = null;
                if (cube.has("rotation")) {
                    var r = cube.getAsJsonObject("rotation");
                    double angle = number(r.get("angle"), -180, 180);
                    Direction.Axis axis = Direction.Axis.fromName(r.get("axis").getAsString());
                    if (axis == null) throw invalid("rotation");
                    boolean rescale = r.has("rescale") && r.get("rescale").getAsBoolean();
                    if (rescale && Math.abs(angle) > 45) throw invalid("rotation");
                    if (angle != 0) rotation = new Rotation(point(r.get("origin")), axis, angle, rescale);
                }
                var faces = new EnumMap<Direction, Face>(Direction.class);
                JsonObject rawFaces = cube.getAsJsonObject("faces");
                if (rawFaces == null || rawFaces.size() > 6) throw invalid("faces");
                for (var raw : rawFaces.entrySet()) {
                    Direction side = Direction.byName(raw.getKey());
                    if (side == null) throw invalid("faces");
                    var face = raw.getValue().getAsJsonObject();
                    if (!face.has("texture") || face.get("texture").isJsonNull()) continue;
                    String texture = resolve(face.get("texture").getAsString(), textures);
                    double[] uv = face.has("uv") ? numbers(face.get("uv"), 4, 0, 16) : defaultUv(side, from, to);
                    double angle = face.has("rotation") ? number(face.get("rotation"), 0, 270) : 0;
                    if (angle % 90 != 0) throw invalid("rotation");
                    int tint = face.has("tint") ? (int)number(face.get("tint"), 0, 0xFFFFFF) : 0xFFFFFF;
                    faces.put(side, new Face(texture, uv[0], uv[1], uv[2], uv[3], (int)angle, tint));
                }
                if (faces.isEmpty()) throw invalid("faces");
                Element element = new Element(from, to, rotation, faces);
                for (var side : Direction.values()) for (var p : element.corners(side))
                    if (p.x < -.00001 || p.y < -.00001 || p.z < -.00001 || p.x > 16.00001 || p.y > 16.00001 || p.z > 16.00001) throw invalid("bounds");
                elements.add(element);
            }
            String name = root.has("name") ? root.get("name").getAsString().strip() : "";
            if (name.length() > 48 || name.chars().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) throw invalid("name");
            var design=new PrintDesign(name,elements);return root.has("part")?design.withPart(PrintPart.parse(root.getAsJsonObject("part"))):design;
        } catch (IllegalStateException | UnsupportedOperationException | NullPointerException exception) {
            throw invalid("format");
        }
    }

    public static JsonObject readObject(String json) {
        return readObject(json,MAX_DOCUMENT_JSON);
    }
    static JsonObject readObject(String json,int limit) {
        if (json == null || json.isBlank() || json.length() > limit) throw invalid("size");
        boolean quoted = false, escaped = false; int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 24) throw invalid("depth"); }
            else if (c == '}' || c == ']') { if (--depth < 0) throw invalid("format"); }
        }
        if (depth != 0 || quoted) throw invalid("format");
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) throw invalid("format");
            return element.getAsJsonObject();
        } catch (com.google.gson.JsonParseException exception) { throw invalid("format"); }
    }

    public static String textureId(String value) {
        if (value == null || value.length() > 160 || value.contains("..") || value.contains("//")) throw invalid("texture");
        if (!value.contains(":")) value = "minecraft:" + value;
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.endsWith(".png") || value.contains(":/")) throw invalid("texture");
        return value;
    }
    private static String resolve(String value, JsonObject textures) {
        var seen = new HashSet<String>();
        while (value.startsWith("#")) {
            if (!seen.add(value) || seen.size() > 32 || !textures.has(value.substring(1))) throw invalid("texture");
            var target = textures.get(value.substring(1));
            if (!target.isJsonPrimitive() || !target.getAsJsonPrimitive().isString()) throw invalid("texture");
            value = target.getAsString();
        }
        return textureId(value);
    }
    private static Vec3d point(JsonElement json) { double[] p = numbers(json, 3, 0, 16); return new Vec3d(p[0], p[1], p[2]); }
    private static double[] numbers(JsonElement value, int count, double min, double max) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != count) throw invalid("format");
        double[] result = new double[count];
        for (int i = 0; i < count; i++) result[i] = number(value.getAsJsonArray().get(i), min, max);
        return result;
    }
    private static double number(JsonElement value, double min, double max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("number");
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < min || result > max) throw invalid("number");
        return result == 0 ? 0 : result;
    }
    private static double[] defaultUv(Direction side, Vec3d a, Vec3d b) {
        return switch (side) {
            case DOWN -> new double[]{a.x, 16 - b.z, b.x, 16 - a.z};
            case UP -> new double[]{a.x, a.z, b.x, b.z};
            case NORTH -> new double[]{16 - b.x, 16 - b.y, 16 - a.x, 16 - a.y};
            case SOUTH -> new double[]{a.x, 16 - b.y, b.x, 16 - a.y};
            case WEST -> new double[]{a.z, 16 - b.y, b.z, 16 - a.y};
            case EAST -> new double[]{16 - b.z, 16 - b.y, 16 - a.z, 16 - a.y};
        };
    }
    public static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException(reason); }

    private String serialize() {
        var root = new JsonObject(); root.addProperty("name", name);
        if(assembly!=null){root.addProperty("assembly",assembly.encoded());return root.toString();}
        if(part!=null)root.add("part",part.json());
        if (voxelCells != null) { root.add("voxels", VoxelGrid.encode(voxelCells)); return root.toString(); }
        var textureMap = new LinkedHashMap<String, String>();
        for (String texture : textures) textureMap.put(texture, "t" + textureMap.size());
        var tex = new JsonObject(); textureMap.forEach((value, id) -> tex.addProperty(id, value)); root.add("textures", tex);
        var shapes = new JsonArray();
        for (var cube : elements) {
            var value = new JsonObject(); value.add("from", array(cube.from.x, cube.from.y, cube.from.z)); value.add("to", array(cube.to.x, cube.to.y, cube.to.z));
            if (cube.rotation != null) {
                var r = new JsonObject(); r.add("origin", array(cube.rotation.origin.x, cube.rotation.origin.y, cube.rotation.origin.z));
                r.addProperty("axis", cube.rotation.axis.asString()); r.addProperty("angle", cube.rotation.degrees); r.addProperty("rescale", cube.rotation.rescale); value.add("rotation", r);
            }
            var sides = new JsonObject();
            for (var entry : cube.faces.entrySet()) {
                Face f = entry.getValue(); var face = new JsonObject(); face.addProperty("texture", "#" + textureMap.get(f.texture));
                face.add("uv", array(f.u0, f.v0, f.u1, f.v1));
                if (f.rotation != 0) face.addProperty("rotation", f.rotation);
                if (f.tint != 0xFFFFFF) face.addProperty("tint", f.tint);
                sides.add(entry.getKey().asString(), face);
            }
            value.add("faces", sides); shapes.add(value);
        }
        root.add("elements", shapes); return root.toString();
    }
    private static JsonArray array(double... values) { var array = new JsonArray(); for (double value : values) array.add(value); return array; }

    public static PrintDesign voxels(String name, String[] cells) {
        return voxels(name, java.util.Arrays.stream(cells).map(texture -> texture == null ? null : new VoxelGrid.Material(texture,0xFFFFFF)).toArray(VoxelGrid.Material[]::new));
    }
    public static PrintDesign voxels(String name, VoxelGrid.Material[] cells) {
        if (cells.length != 4096) throw invalid("bounds");
        boolean[] visited = new boolean[4096]; var elements = new ArrayList<Element>();
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int index = x + 16 * (z + 16 * y); var texture = cells[index];
            if (texture == null || visited[index]) continue;
            int X = x + 1, Z = z + 1, Y = y + 1;
            if (texture.isBlockModel()) {
                int size = texture.modelSize(); X = x + size; Y = y + size; Z = z + size;
                if (X > 16 || Y > 16 || Z > 16 || !plane(cells, visited, texture, x, X, y, Y, z, Z)) throw invalid("schematic_cells");
            } else {
                while (X < 16 && same(cells, visited, texture, X, y, z)) X++;
                while (Z < 16 && plane(cells, visited, texture, x, X, y, y + 1, Z, Z + 1)) Z++;
                while (Y < 16 && plane(cells, visited, texture, x, X, Y, Y + 1, z, Z)) Y++;
            }
            for (int yy = y; yy < Y; yy++) for (int zz = z; zz < Z; zz++) for (int xx = x; xx < X; xx++) visited[xx + 16 * (zz + 16 * yy)] = true;
            var faces = new EnumMap<Direction, Face>(Direction.class); var a = new Vec3d(x,y,z); var b = new Vec3d(X,Y,Z);
            for (var side : Direction.values()) if (texture.isBlockModel() || exposed(cells,side,x,y,z,X,Y,Z)) {
                double[] uv = defaultUv(side, a, b); faces.put(side, new Face(texture.texture(), uv[0],uv[1],uv[2],uv[3],0,texture.tint()));
            }
            elements.add(new Element(a, b, null, faces, texture.blockState()));
        }
        if (elements.isEmpty()) throw invalid("complexity");
        return new PrintDesign(name, elements, cells);
    }
    private static boolean same(VoxelGrid.Material[] cells, boolean[] visited, VoxelGrid.Material texture, int x, int y, int z) {
        int index = x + 16 * (z + 16 * y); return !visited[index] && texture.equals(cells[index]);
    }
    private static boolean plane(VoxelGrid.Material[] cells, boolean[] visited, VoxelGrid.Material texture, int x, int X, int y, int Y, int z, int Z) {
        for (int yy=y; yy<Y; yy++) for (int zz=z; zz<Z; zz++) for (int xx=x; xx<X; xx++) if (!same(cells,visited,texture,xx,yy,zz)) return false;
        return true;
    }
    private static boolean exposed(VoxelGrid.Material[] cells,Direction side,int x,int y,int z,int X,int Y,int Z) {
        for(int yy=y;yy<Y;yy++)for(int zz=z;zz<Z;zz++)for(int xx=x;xx<X;xx++) {
            if(side==Direction.WEST&&xx!=x || side==Direction.EAST&&xx!=X-1 || side==Direction.DOWN&&yy!=y
                    || side==Direction.UP&&yy!=Y-1 || side==Direction.NORTH&&zz!=z || side==Direction.SOUTH&&zz!=Z-1)continue;
            int nx=xx+side.getOffsetX(),ny=yy+side.getOffsetY(),nz=zz+side.getOffsetZ();
            if(nx<0||nx>15||ny<0||ny>15||nz<0||nz>15||cells[nx+16*(nz+16*ny)]==null)return true;
        }
        return false;
    }
}
