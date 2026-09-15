package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BasicBakedModel;
import net.minecraft.client.render.model.ModelBakeSettings;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

/** Regular chunk-baked quads: no block entity renderer or separate shader pass. */
public final class OreBakedModel {
    public static BakedModel bake(JsonUnbakedModel model, BakedModel original, Function<SpriteIdentifier, Sprite> textures, ModelBakeSettings settings) {
        if (!model.id.startsWith("justifylasers:")) return original;
        int variant = -1;
        for (int i = 0; i < 4; i++) if (model.textureExists("justifylasers_ore_" + i)) { variant = i; break; }
        if (variant < 0) return original;
        var material = model.resolveSprite("mineral");
        String path = material.getTextureId().getPath();
        String mineral = path.contains("/wolframite/") ? "wolframite" : "photonite";
        var sprite = textures.apply(material);
        var random = Random.create(0);
        var faces = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction face : Direction.values()) faces.put(face, new ArrayList<>(original.getQuads(null, face, random)));
        Matrix4f rotation = settings.getRotation().getMatrix();
        for (var shard : OreInclusions.build(mineral, variant)) {
            var n = rotation.transformDirection(new Vector3f(shard.face().getOffsetX(), shard.face().getOffsetY(), shard.face().getOffsetZ()));
            var cullFace = Direction.getFacing(n.x, n.y, n.z);
            for (var facet : shard.facets()) {
                if (facet.buried()) continue;
                int count = facet.vertices().size();
                if (count == 4) faces.get(cullFace).add(quad(facet, new int[]{0,1,2,3}, sprite, rotation));
                else for (int i = 1; i < count - 1; i += 2)
                    faces.get(cullFace).add(quad(facet, new int[]{0,i,i+1,Math.min(i+2,count-1)}, sprite, rotation));
            }
        }
        return new BasicBakedModel(new ArrayList<>(original.getQuads(null, null, random)), faces, original.useAmbientOcclusion(),
                original.hasDepth(), original.isSideLit(), original.getParticleSprite(), original.getTransformation(), original.getOverrides());
    }

    private static BakedQuad quad(OreInclusions.Facet facet, int[] indices, Sprite sprite, Matrix4f rotation) {
        var points = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            var p = facet.vertices().get(indices[i]);
            points[i] = rotation.transformPosition(new Vector3f((float)p.x/16-.5F, (float)p.y/16-.5F, (float)p.z/16-.5F)).add(.5F,.5F,.5F);
        }
        var normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();
        int packedNormal = ((byte)Math.round(normal.x*127) & 255) | (((byte)Math.round(normal.y*127) & 255) << 8)
                | (((byte)Math.round(normal.z*127) & 255) << 16);
        int[] vertices = new int[32];
        for (int i = 0; i < 4; i++) {
            var p = points[i]; var uv = facet.uv().get(indices[i]); int offset = i*8;
            vertices[offset] = Float.floatToRawIntBits(p.x); vertices[offset+1] = Float.floatToRawIntBits(p.y); vertices[offset+2] = Float.floatToRawIntBits(p.z);
            vertices[offset+3] = -1;
            vertices[offset+4] = Float.floatToRawIntBits(sprite.getMinU() + uv.u()*(sprite.getMaxU()-sprite.getMinU()));
            vertices[offset+5] = Float.floatToRawIntBits(sprite.getMinV() + uv.v()*(sprite.getMaxV()-sprite.getMinV()));
            vertices[offset+7] = packedNormal;
        }
        return new BakedQuad(vertices, -1, Direction.getFacing(normal.x, normal.y, normal.z), sprite, true);
    }

    private OreBakedModel() { }
}
