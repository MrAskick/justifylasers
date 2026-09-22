package net.askcraft.justifylasers.client.render;

import com.google.gson.JsonObject;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only resources already supplied by the game, mods or active resource packs are exposed. */
public final class PrintTextures {
    public record Material(Identifier image, Sprite sprite, boolean translucent) {
        public RenderLayer layer() { return translucent ? RenderLayer.getEntityTranslucent(image) : RenderLayer.getEntityCutoutNoCull(image); }
        public float u(double value) { return sprite == null ? (float)(value / 16) : (float)(sprite.getMinU() + (sprite.getMaxU()-sprite.getMinU()) * value / 16); }
        public float v(double value) { return sprite == null ? (float)(value / 16) : (float)(sprite.getMinV() + (sprite.getMaxV()-sprite.getMinV()) * value / 16); }
    }
    private static final Map<String, Material> MATERIALS = new LinkedHashMap<>(64,.75F,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Material> entry) { return size()>512; }
    };
    private static List<String> catalog;
    public static void clear() { MATERIALS.clear(); catalog=null; SchematicBlockModels.clear(); PrintedModelRenderer.clear(); }
    public static Identifier id(String id) { String[] parts=id.split(":",2); return Identifier.of(parts[0],parts[1]); }
    public static Identifier image(String texture) { var id=id(texture); return Identifier.of(id.getNamespace(),"textures/"+id.getPath()+".png"); }
    public static List<String> catalog() {
        if (catalog == null) catalog = MinecraftClient.getInstance().getResourceManager().findResources("textures", resource ->
                resource.getPath().endsWith(".png") && !resource.getPath().endsWith("_n.png") && !resource.getPath().endsWith("_s.png"))
                .keySet().stream().map(resource -> resource.getNamespace()+":"+resource.getPath().substring(9,resource.getPath().length()-4))
                .sorted(java.util.Comparator.<String,Boolean>comparing(s -> !s.contains(":block/")).thenComparing(s -> s)).toList();
        return catalog;
    }
    public static Material material(String texture) {
        return MATERIALS.computeIfAbsent(texture, key -> {
            var client=MinecraftClient.getInstance();
            Sprite sprite=client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(id(key));
            boolean stitched=!sprite.getContents().getId().getPath().equals("missingno");
            var source=client.getResourceManager().getResource(image(key));
            boolean translucent=false;
            if (source.isPresent()) try (var stream=source.get().getInputStream(); var pixels=NativeImage.read(stream)) {
                if (pixels.getFormat().hasAlpha()) outer: for (int y=0;y<pixels.getHeight();y+=Math.max(1,pixels.getHeight()/128)) for (int x=0;x<pixels.getWidth();x+=Math.max(1,pixels.getWidth()/128)) {
                    int a=pixels.getOpacity(x,y)&255;
                    if (a>0 && a<250) { translucent=true; break outer; }
                }
            } catch (IOException | RuntimeException ignored) { /* A broken pack retains Minecraft's missing texture. */ }
            if (source.isEmpty()) stitched=true;
            return new Material(stitched?SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE:image(key),stitched?sprite:null,translucent);
        });
    }
    public static PrintDesign importJson(String json) {
        JsonObject expanded=expand(PrintDesign.readObject(json),new HashSet<>(),0);
        PrintDesign design=PrintDesign.parse(expanded.toString());
        for (String texture:design.textures()) if (MinecraftClient.getInstance().getResourceManager().getResource(image(texture)).isEmpty())
            throw PrintDesign.invalid("missing_texture");
        return design;
    }
    private static JsonObject expand(JsonObject root, Set<String> parents, int depth) {
        if (!root.has("parent")) return root;
        String parent=PrintDesign.textureId(root.get("parent").getAsString());
        if (depth>=8 || !parents.add(parent)) throw PrintDesign.invalid("parent");
        // builtin/entity cannot contribute printable cuboids. Explicit elements need no parent geometry.
        if (root.has("elements") && parent.equals("minecraft:block/block")) { root.remove("parent"); return root; }
        var resourceId=id(parent);
        var resource=MinecraftClient.getInstance().getResourceManager().getResource(Identifier.of(resourceId.getNamespace(),"models/"+resourceId.getPath()+".json"));
        if (resource.isEmpty()) throw PrintDesign.invalid("parent");
        try (var reader=resource.get().getReader()) {
            char[] buffer=new char[2048]; StringBuilder text=new StringBuilder(); int n;
            while ((n=reader.read(buffer))!=-1) { text.append(buffer,0,n); if(text.length()>PrintDesign.MAX_JSON) throw PrintDesign.invalid("size"); }
            JsonObject result=expand(PrintDesign.readObject(text.toString()),parents,depth+1);
            JsonObject textures=result.has("textures")?result.getAsJsonObject("textures").deepCopy():new JsonObject();
            if(root.has("textures")) root.getAsJsonObject("textures").entrySet().forEach(entry->textures.add(entry.getKey(),entry.getValue()));
            root.entrySet().stream().filter(entry->!entry.getKey().equals("parent")).forEach(entry->result.add(entry.getKey(),entry.getValue()));
            result.add("textures",textures); result.remove("parent"); return result;
        } catch(IOException | IllegalStateException failure) { throw PrintDesign.invalid("parent"); }
    }
    private PrintTextures() { }
}
