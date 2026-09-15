package net.askcraft.justifylasers.client.render;

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
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LaserCrystalModel {
    enum Material { FRAME, STEEL, CRYSTAL, LIGHT }

    record Face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal, boolean triangle,
                ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud) { }

    private static final Map<LaserColor, List<Face>> MESHES = buildMeshes();
    private static final ItemModelBounds BOUNDS = ItemModelBounds.of(mesh().stream()
            .flatMap(face -> java.util.stream.Stream.of(face.a(), face.b(), face.c(), face.d())).toList());
    private static final ItemModelBounds CRYSTAL_BOUNDS = ItemModelBounds.of(mesh().stream().filter(face -> face.material() == Material.CRYSTAL)
            .flatMap(face -> java.util.stream.Stream.of(face.a(), face.b(), face.c(), face.d())).toList());

    public static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, int overlay) {
        LaserColor color = ((LaserCrystalItem) stack.getItem()).color();
        render(color, false, false, mode, matrices, consumers, light, overlay);
    }

    public static void renderMount(ModelTransformationMode mode, MatrixStack matrices,
                                   VertexConsumerProvider consumers, int light, int overlay) {
        render(LaserColor.WHITE, true, false, mode, matrices, consumers, light, overlay);
    }

    public static void renderBare(ModelTransformationMode mode, MatrixStack matrices,
                                  VertexConsumerProvider consumers, int light, int overlay) {
        render(LaserColor.VIOLET, false, true, mode, matrices, consumers, light, overlay);
    }

    private static void render(LaserColor color, boolean mountOnly, boolean crystalOnly, ModelTransformationMode mode,
                               MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        matrices.push();
        if (mode == ModelTransformationMode.GUI) (crystalOnly ? CRYSTAL_BOUNDS : BOUNDS).fitGui(matrices);
        else matrices.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        try {
            // Keep the item in the normal depth-tested material pass, including with Iris/Oculus.
            for (Material material : new Material[]{Material.FRAME, Material.STEEL, Material.LIGHT, Material.CRYSTAL}) {
                if (mountOnly && (material == Material.CRYSTAL || material == Material.LIGHT)
                        || crystalOnly && material != Material.CRYSTAL) continue;
                boolean emissive = material == Material.CRYSTAL || material == Material.LIGHT;
                Identifier texture = ComponentAtlas.texture("crystal", color.rgb());
                // GUI highlights are unshaded; world materials retain normal fog, depth and LabPBR support.
                VertexConsumer buffer = consumers.getBuffer(material == Material.CRYSTAL
                        ? EmissiveLayers.glass(texture)
                        : emissive && mode == ModelTransformationMode.GUI ? EmissiveLayers.get(texture) : RenderLayer.getEntitySolid(texture));
                int illumination = emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
                for (Face face : MESHES.get(color)) {
                    if (face.material != material) continue;
                    vertex(buffer, matrices, face, face.a, face.ua, illumination, overlay);
                    vertex(buffer, matrices, face, face.b, face.ub, illumination, overlay);
                    vertex(buffer, matrices, face, face.c, face.uc, illumination, overlay);
                    vertex(buffer, matrices, face, face.d, face.ud, illumination, overlay);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    static List<Face> mesh() {
        return MESHES.get(LaserColor.RED);
    }
    static List<Face> mesh(LaserColor color) { return MESHES.get(color); }

    private static Map<LaserColor, List<Face>> buildMeshes() {
        Map<LaserColor, List<Face>> meshes = new EnumMap<>(LaserColor.class);
        for (LaserColor color : LaserColor.values()) meshes.put(color, buildMesh(color));
        return Map.copyOf(meshes);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Face face, Vec3d point,
                               ComponentAtlas.Uv uv, int light, int overlay) {
        Vec3d normal = face.normal;
        int alpha = face.material == Material.CRYSTAL ? 110 : 255;
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                        (float) point.x, (float) point.y, (float) point.z)
                .color(255, 255, 255, alpha).texture(uv.u(), uv.v()).overlay(overlay).light(light),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private static List<Face> buildMesh(LaserColor color) {
        Builder mesh = new Builder(ComponentAtlas.load("crystal", color.asString()));
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
            mesh.skin = mesh.atlas.surface(side == 2 ? "base_back" : "base_front");
            mesh.plate(Material.FRAME, -1.85, 0.8, 1.85, 2.4, -5.265);
            mesh.skin = null;
            mesh.plate(Material.LIGHT, -1.35, 1.4, 1.35, 1.85, -5.28);

            mesh.prism(Material.FRAME, 0, -3.85, 0.85, 0.65, 0.18, 3.2, 9.65, 0.15);
            mesh.prism(Material.STEEL, -0.82, -3.88, 0.21, 0.68, 0.1, 3.8, 9.1, 0.12);
            mesh.prism(Material.STEEL, 0.82, -3.88, 0.21, 0.68, 0.1, 3.8, 9.1, 0.12);
            mesh.prism(Material.STEEL, 0, -3.5, 1.05, 1.0, 0.2, 9.25, 10.05, 0.14);
            mesh.prism(Material.STEEL, 0, -3.85, 1.0, 0.72, 0.18, 3.4, 4.05, 0.12);
            mesh.skin = mesh.atlas.surface("column_light");
            mesh.plate(Material.LIGHT, -0.34, 4.65, 0.34, 8.8, -4.515);
            mesh.skin = null;
        }
        return List.copyOf(mesh.faces);
    }

    private static final class Builder {
        private final List<Face> faces = new ArrayList<>();
        private final ComponentAtlas atlas;
        private ComponentAtlas.Skin skin;
        private int turn;
        private Vec3d min = new Vec3d(5, 3.5, 5), max = new Vec3d(11, 15.75, 11);

        private Builder(ComponentAtlas atlas) { this.atlas = atlas; }

        private Vec3d point(double x, double y, double z) { return new Vec3d(8 + x, y, 8 + z); }

        private Vec3d turn(Vec3d point) {
            double x = point.x - 8, y = point.y, z = point.z - 8;
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
            bounds(b, c);
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
            Vec3d a = point(left, bottom, z), b = point(left, top, z), c = point(right, top, z), d = point(right, bottom, z);
            bounds(new Vec3d[]{a, b, c, d}, new Vec3d[0]);
            face(material, a, b, c, d, false);
        }

        private void triangle(Material material, Vec3d a, Vec3d b, Vec3d c) {
            face(material, a, b, c, c, true);
        }

        private void face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, boolean triangle) {
            Vec3d normal = b.subtract(a).crossProduct(c.subtract(a)).normalize();
            var ua = uv(material, a, normal, triangle && b.y == 15.75);
            var ub = uv(material, b, normal, triangle && b.y == 15.75);
            var uc = uv(material, c, normal, triangle && b.y == 15.75);
            var ud = uv(material, d, normal, triangle && b.y == 15.75);
            a = turn(a); b = turn(b); c = turn(c); d = turn(d);
            normal = b.subtract(a).crossProduct(c.subtract(a)).normalize();
            faces.add(new Face(material, a, b, c, d, normal, triangle,
                    ua, ub, uc, ud));
        }

        private ComponentAtlas.Uv uv(Material material, Vec3d point, Vec3d normal, boolean tip) {
            if (tip) return atlas.region("tip").uv((point.x - 5.45) / 5.1, (point.z - 5.45) / 5.1);
            if (material == Material.CRYSTAL) {
                var crystal = new ComponentAtlas.Skin(atlas.region("front"), atlas.region("back"), atlas.region("left"),
                        atlas.region("right"), atlas.region("tip"), atlas.region("front"), false);
                return crystal.project(point, normal, new Vec3d(5, 3.5, 5), new Vec3d(11, 12.6, 11));
            }
            var selected = skin != null ? skin : material == Material.LIGHT ? atlas.strip("light") : atlas.trim(material == Material.STEEL ? "steel" : "metal");
            return selected.project(point, normal, min, max);
        }

        private void bounds(Vec3d[] a, Vec3d[] b) {
            min = new Vec3d(16, 16, 16); max = Vec3d.ZERO;
            for (Vec3d[] ring : new Vec3d[][]{a, b}) for (Vec3d point : ring) {
                min = new Vec3d(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z));
                max = new Vec3d(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z));
            }
        }
    }

    static final class EmissiveLayers extends RenderLayer {
        private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();
        private static final Map<Identifier, RenderLayer> GLASS = new HashMap<>();
        private static final Map<Identifier, RenderLayer> LENSES = new HashMap<>();

        static RenderLayer lens(Identifier texture) {
            return LENSES.computeIfAbsent(texture, id -> RenderLayer.of("justifylasers_cube_lens",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 4096, false, true,
                    MultiPhaseParameters.builder().program(ENTITY_TRANSLUCENT_PROGRAM)
                            .texture(new RenderPhase.Texture(id, false, false)).transparency(TRANSLUCENT_TRANSPARENCY)
                            .depthTest(LEQUAL_DEPTH_TEST).cull(ENABLE_CULLING).lightmap(ENABLE_LIGHTMAP)
                            .overlay(ENABLE_OVERLAY_COLOR).writeMaskState(COLOR_MASK).build(false)));
        }

        static RenderLayer glass(Identifier texture) {
            return GLASS.computeIfAbsent(texture, id -> RenderLayer.of("justifylasers_crystal_glass",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 4096, false, true,
                    MultiPhaseParameters.builder().program(ENTITY_TRANSLUCENT_PROGRAM)
                            .texture(new RenderPhase.Texture(id, false, false)).transparency(TRANSLUCENT_TRANSPARENCY)
                            .depthTest(LEQUAL_DEPTH_TEST).cull(ENABLE_CULLING).lightmap(ENABLE_LIGHTMAP)
                            .overlay(ENABLE_OVERLAY_COLOR).writeMaskState(ALL_MASK).build(false)));
        }

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
