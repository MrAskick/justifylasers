package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LaserCrystalModel {
    enum Material { FRAME, STEEL, CRYSTAL, LIGHT }

    record Face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal, boolean triangle) { }

    private static final List<Face> MESH = buildMesh();
    private static final Map<LaserColor, Map<Material, Identifier>> TEXTURES = new EnumMap<>(LaserColor.class);

    static {
        for (LaserColor color : LaserColor.values()) {
            TEXTURES.put(color, Map.of(
                    Material.FRAME, texture("frame"), Material.STEEL, texture("steel"),
                    Material.CRYSTAL, texture(color.asString()), Material.LIGHT, texture(color.asString() + "_light")));
        }
    }

    public static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, int overlay) {
        LaserColor color = ((LaserCrystalItem) stack.getItem()).color();
        matrices.push();
        matrices.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        try {
            // Keep the item in the normal depth-tested material pass, including with Iris/Oculus.
            for (Material material : Material.values()) {
                boolean emissive = material == Material.CRYSTAL || material == Material.LIGHT;
                Identifier texture = TEXTURES.get(color).get(material);
                // GUI highlights are unshaded; world materials retain normal fog, depth and LabPBR support.
                VertexConsumer buffer = consumers.getBuffer(emissive && mode == ModelTransformationMode.GUI
                        ? EmissiveLayers.get(texture) : RenderLayer.getEntitySolid(texture));
                int illumination = emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
                for (Face face : MESH) {
                    if (face.material != material) continue;
                    vertex(buffer, matrices, face.a, face.normal, 0, textureV(face, face.a, 1), illumination, overlay);
                    vertex(buffer, matrices, face.b, face.normal, face.triangle ? 0.5F : 0, textureV(face, face.b, 0), illumination, overlay);
                    vertex(buffer, matrices, face.c, face.normal, 1, textureV(face, face.c, face.triangle ? 1 : 0), illumination, overlay);
                    vertex(buffer, matrices, face.d, face.normal, 1, textureV(face, face.d, 1), illumination, overlay);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    static List<Face> mesh() {
        return MESH;
    }

    private static Identifier texture(String name) {
        return JustifyLasers.id("textures/item/crystal/" + name + ".png");
    }

    private static float textureV(Face face, Vec3d point, float fallback) {
        // Continuous crystal UVs prevent the narrow bevels from repeating the whole facet texture.
        return face.material == Material.CRYSTAL ? (float) ((15.75 - point.y) / 12.25) : fallback;
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d point, Vec3d normal,
                               float u, float v, int light, int overlay) {
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                        (float) point.x, (float) point.y, (float) point.z)
                .color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private static List<Face> buildMesh() {
        Builder mesh = new Builder();
        mesh.prism(Material.FRAME, 0, 0, 4.9, 4.9, 1.1, 0.35, 3.0, 0.25);
        mesh.prism(Material.STEEL, 0, 0, 5.0, 5.0, 1.15, 2.65, 3.35, 0.15);
        mesh.prism(Material.FRAME, 0, 0, 3.4, 3.4, 0.7, 3.3, 3.7, 0.12);

        Vec3d[] lower = mesh.ring(0, 0, 2.6, 2.6, 0.55, 3.5);
        Vec3d[] body = mesh.ring(0, 0, 3.0, 3.0, 0.65, 4.0);
        Vec3d[] shoulder = mesh.ring(0, 0, 3.0, 3.0, 0.65, 11.75);
        Vec3d[] crown = mesh.ring(0, 0, 2.55, 2.55, 0.55, 12.15);
        Vec3d[] tipBase = mesh.ring(0, 0, 2.55, 2.55, 0.55, 12.6);
        mesh.join(Material.CRYSTAL, lower, body);
        mesh.join(Material.CRYSTAL, body, shoulder);
        mesh.join(Material.CRYSTAL, shoulder, crown);
        mesh.join(Material.CRYSTAL, crown, tipBase);
        mesh.cap(Material.CRYSTAL, lower, false);
        Vec3d apex = new Vec3d(8, 15.75, 8);
        for (int i = 0; i < 8; i++) {
            mesh.triangle(Material.CRYSTAL, tipBase[i], apex, tipBase[(i + 1) % 8]);
        }

        for (int side = 0; side < 4; side++) {
            mesh.turn = side;
            mesh.prism(Material.STEEL, 0, -4.85, 2.4, 0.4, 0.18, 0.6, 2.65, 0.13);
            mesh.plate(Material.FRAME, -1.85, 1.0, 1.85, 2.25, -5.265);
            mesh.plate(Material.LIGHT, -1.35, 1.4, 1.35, 1.85, -5.28);

            mesh.prism(Material.FRAME, 0, -3.85, 0.85, 0.65, 0.18, 3.2, 9.65, 0.15);
            mesh.prism(Material.STEEL, -0.82, -3.88, 0.21, 0.68, 0.1, 3.8, 9.1, 0.12);
            mesh.prism(Material.STEEL, 0.82, -3.88, 0.21, 0.68, 0.1, 3.8, 9.1, 0.12);
            mesh.prism(Material.STEEL, 0, -3.5, 1.05, 1.0, 0.2, 9.25, 10.05, 0.14);
            mesh.prism(Material.STEEL, 0, -3.85, 1.0, 0.72, 0.18, 3.4, 4.05, 0.12);
            mesh.plate(Material.LIGHT, -0.34, 4.65, 0.34, 8.8, -4.515);
        }
        return List.copyOf(mesh.faces);
    }

    private static final class Builder {
        private final List<Face> faces = new ArrayList<>();
        private int turn;

        private Vec3d point(double x, double y, double z) {
            return switch (turn) {
                case 1 -> new Vec3d(8 - z, y, 8 + x);
                case 2 -> new Vec3d(8 - x, y, 8 - z);
                case 3 -> new Vec3d(8 + z, y, 8 - x);
                default -> new Vec3d(8 + x, y, 8 + z);
            };
        }

        private Vec3d[] ring(double x, double z, double halfX, double halfZ, double cut, double y) {
            return new Vec3d[]{point(x - halfX + cut, y, z - halfZ), point(x + halfX - cut, y, z - halfZ),
                    point(x + halfX, y, z - halfZ + cut), point(x + halfX, y, z + halfZ - cut),
                    point(x + halfX - cut, y, z + halfZ), point(x - halfX + cut, y, z + halfZ),
                    point(x - halfX, y, z + halfZ - cut), point(x - halfX, y, z - halfZ + cut)};
        }

        private void prism(Material material, double x, double z, double halfX, double halfZ,
                           double cut, double bottom, double top, double bevel) {
            double inset = Math.min(bevel, Math.min(halfX, halfZ) * 0.45);
            Vec3d[] a = ring(x, z, halfX - inset, halfZ - inset, cut * 0.7, bottom);
            Vec3d[] b = ring(x, z, halfX, halfZ, cut, bottom + bevel);
            Vec3d[] c = ring(x, z, halfX, halfZ, cut, top - bevel);
            Vec3d[] d = ring(x, z, halfX - inset, halfZ - inset, cut * 0.7, top);
            join(material, a, b);
            join(material, b, c);
            join(material, c, d);
            cap(material, a, false);
            cap(material, d, true);
        }

        private void join(Material material, Vec3d[] lower, Vec3d[] upper) {
            for (int i = 0; i < 8; i++) {
                int next = (i + 1) % 8;
                face(material, lower[i], upper[i], upper[next], lower[next], false);
            }
        }

        private void cap(Material material, Vec3d[] ring, boolean top) {
            Vec3d center = ring[0].add(ring[4]).multiply(0.5);
            for (int i = 0; i < 8; i++) {
                int next = (i + 1) % 8;
                triangle(material, center, ring[top ? next : i], ring[top ? i : next]);
            }
        }

        private void plate(Material material, double left, double bottom, double right, double top, double z) {
            face(material, point(left, bottom, z), point(left, top, z), point(right, top, z), point(right, bottom, z), false);
        }

        private void triangle(Material material, Vec3d a, Vec3d b, Vec3d c) {
            face(material, a, b, c, c, true);
        }

        private void face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, boolean triangle) {
            faces.add(new Face(material, a, b, c, d, b.subtract(a).crossProduct(c.subtract(a)).normalize(), triangle));
        }
    }

    static final class EmissiveLayers extends RenderLayer {
        private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

        static RenderLayer get(Identifier texture) {
            return LAYERS.computeIfAbsent(texture, id -> RenderLayer.of("justifylasers_crystal_emission",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 4096, false, false,
                    MultiPhaseParameters.builder().program(EYES_PROGRAM)
                            .texture(new RenderPhase.Texture(id, false, false)).transparency(NO_TRANSPARENCY)
                            .depthTest(LEQUAL_DEPTH_TEST).cull(ENABLE_CULLING).lightmap(ENABLE_LIGHTMAP)
                            .overlay(ENABLE_OVERLAY_COLOR).writeMaskState(ALL_MASK).build(false)));
        }

        private EmissiveLayers() {
            super("unused", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS, 0, false, false, () -> { }, () -> { });
        }
    }

    private LaserCrystalModel() { }
}
