package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.printing.PrintBlockState;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.printing.PrintSlice;
import net.askcraft.justifylasers.printing.VoxelGrid;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the baked model, so state rotations, UV locking and resource-pack overrides share Minecraft's result. */
public final class SchematicBlockModels {
    public record Face(PrintDesign.Face material, List<PrintSlice.Vertex> vertices, Direction cull) { }
    public record Model(String particle, int tint, List<Face> faces, boolean opaque, int hiddenAgainstSame) { }
    private static final Map<String, Model> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Model> entry) { return size() > 512; }
    };
    public static void clear() { CACHE.clear(); }

    public static VoxelGrid.Material material(String text) {
        var state = PrintBlockState.resolve(text);
        if (state.isAir()) return null;
        var model = model(text);
        return new VoxelGrid.Material(model.particle, model.tint, text, 1);
    }

    public static Model model(String text) {
        try { return CACHE.computeIfAbsent(text, SchematicBlockModels::load); }
        catch (PrintBlockState.ImportFailure failure) { throw failure; }
        catch (RuntimeException unsupported) { throw new PrintBlockState.ImportFailure("schematic_model", text); }
    }

    private static Model load(String text) {
        var state = PrintBlockState.resolve(text);
        var client = MinecraftClient.getInstance();
        if (state.getRenderType() != BlockRenderType.MODEL)
            throw new PrintBlockState.ImportFailure("schematic_model", text);
        var baked = client.getBlockRenderManager().getModel(state);
        var faces = new ArrayList<Face>();
        for (int side = 0; side <= 6; side++) {
            Direction cull = side == 6 ? null : Direction.values()[side];
            for (BakedQuad quad : baked.getQuads(state, cull, Random.create(42))) {
                if (faces.size() >= 512) throw new PrintBlockState.ImportFailure("schematic_model", text);
                var sprite = quad.getSprite();
                if (sprite.getContents().getId().getPath().equals("missingno"))
                    throw new PrintBlockState.ImportFailure("schematic_missing_texture", text);
                int rgb = quad.hasColor() ? client.getBlockColors().getColor(state, null, null, quad.getColorIndex()) & 0xFFFFFF : 0xFFFFFF;
                var face = new PrintDesign.Face(sprite.getContents().getId().toString(), 0, 0, 16, 16, 0, rgb);
                int[] data = quad.getVertexData();
                int stride = data.length / 4;
                if (stride < 6 || data.length % 4 != 0) throw new PrintBlockState.ImportFailure("schematic_model", text);
                var vertices = new ArrayList<PrintSlice.Vertex>(4);
                for (int v = 0; v < 4; v++) {
                    int at = v * stride;
                    var pos = new Vec3d(Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]));
                    // Models extending into a neighbouring block cannot be clipped into an independent printed cell safely.
                    if (!Double.isFinite(pos.x + pos.y + pos.z) || pos.x < -1e-5 || pos.y < -1e-5 || pos.z < -1e-5
                            || pos.x > 1.00001 || pos.y > 1.00001 || pos.z > 1.00001)
                        throw new PrintBlockState.ImportFailure("schematic_model", text);
                    double u = 16 * (Float.intBitsToFloat(data[at + 4]) - sprite.getMinU()) / (sprite.getMaxU() - sprite.getMinU());
                    double w = 16 * (Float.intBitsToFloat(data[at + 5]) - sprite.getMinV()) / (sprite.getMaxV() - sprite.getMinV());
                    if (!Double.isFinite(u + w)) throw new PrintBlockState.ImportFailure("schematic_model", text);
                    vertices.add(new PrintSlice.Vertex(pos, u, w));
                }
                faces.add(new Face(face, List.copyOf(vertices), cull));
            }
        }
        if (faces.isEmpty()) throw new PrintBlockState.ImportFailure("schematic_model", text);
        var particle = baked.getParticleSprite().getContents().getId();
        boolean opaque = state.isOpaqueFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, net.minecraft.util.math.BlockPos.ORIGIN);
        int same=0;for(var side:Direction.values())if(state.isSideInvisible(state,side))same|=1<<side.ordinal();
        return new Model(particle.toString(), client.getBlockColors().getColor(state, null, null, 0) & 0xFFFFFF, List.copyOf(faces), opaque, same);
    }

    private SchematicBlockModels() { }
}
