package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LaserModuleModel {
    enum Material {
        FRAME(false), STEEL(false), GOLD(false), CYAN(true), RED(true), ORANGE(true),
        WHITE(true), BLUE(true), YELLOW(true), ENERGY(true), HEAT(true);
        final boolean emissive;
        Material(boolean emissive) { this.emissive = emissive; }
    }

    record Face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal,
                ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud) { }

    private static final Map<String, List<Face>> MESHES = buildMeshes();
    private static final Map<String, ItemModelBounds> BOUNDS = MESHES.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> ItemModelBounds.of(entry.getValue().stream()
                    .flatMap(face -> java.util.stream.Stream.of(face.a(), face.b(), face.c(), face.d())).toList())));

    public static void render(String id, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, int overlay) {
        if (id.equals("spectrum_module")) { SpectrumModuleModel.render(mode, matrices, consumers, light); return; }
        List<Face> mesh = MESHES.get(id);
        if (mesh == null) return;
        matrices.push();
        try {
            if (mode == ModelTransformationMode.GUI) BOUNDS.get(id).fitGui(matrices);
            else matrices.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
            var texture = ComponentAtlas.texture("module/" + atlasId(id), "default");
            int accent = switch (id) {
                case "entity_heal_module" -> 0x50FF99;
                case "entity_lift_module" -> 0x66DDFF;
                case "entity_lower_module" -> 0xCB8CFF;
                default -> 0xFFFFFF;
            };
            for (Material material : Material.values()) {
                VertexConsumer buffer = consumers.getBuffer(material.emissive && mode == ModelTransformationMode.GUI
                        ? LaserCrystalModel.EmissiveLayers.get(texture) : RenderLayer.getEntitySolid(texture));
                int illumination = material.emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
                for (Face face : mesh) {
                    if (face.material != material) continue;
                    int rgb = material.emissive ? accent : 0xFFFFFF;
                    vertex(buffer, matrices, face, face.a, face.ua, illumination, overlay, rgb);
                    vertex(buffer, matrices, face, face.b, face.ub, illumination, overlay, rgb);
                    vertex(buffer, matrices, face, face.c, face.uc, illumination, overlay, rgb);
                    vertex(buffer, matrices, face, face.d, face.ud, illumination, overlay, rgb);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    static Map<String, List<Face>> meshes() {
        return MESHES;
    }

    static String atlasId(String id) {
        if (id.equals("advanced_thickness_module")) return "thickness_module";
        if (id.equals("block_collection_module")) return "block_drops_module";
        return id.equals("entity_heal_module") || id.equals("entity_lift_module") || id.equals("entity_lower_module") ? "entity_damage_module" : id;
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Face face, Vec3d point,
                               ComponentAtlas.Uv uv, int light, int overlay, int rgb) {
        Vec3d normal = face.normal;
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                        (float) point.x, (float) point.y, (float) point.z)
                .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).texture(uv.u(), uv.v()).overlay(overlay).light(light),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private static Map<String, List<Face>> buildMeshes() {
        Map<String, List<Face>> result = new LinkedHashMap<>();
        result.put("block_destruction_module", drill());
        result.put("entity_damage_module", damage());
        result.put("entity_heal_module", effect(0));
        result.put("entity_lift_module", effect(1));
        result.put("entity_lower_module", effect(-1));
        result.put("range_module", range(false));
        result.put("advanced_range_module", range(true));
        result.put("thickness_module", lens());
        result.put("advanced_thickness_module", advancedLens());
        result.put("electric_motor", motor());
        result.put("control_circuit", circuit());
        result.put("silk_touch_module", silk());
        result.put("block_drops_module", collector(false));
        result.put("block_collection_module", cargoCollector());
        result.put("scorch_marks_module", thermal(false));
        result.put("ignition_module", thermal(true));
        result.put("target_filter_module", targetFilter());
        result.replaceAll((id, mesh) -> removeCoveredPanels(mesh));
        return Map.copyOf(result);
    }

    // Keep the later detail panel and trim the coplanar chassis underneath it.
    // Unlike a depth bias, this also produces a single surface in shader shadow passes.
    private static List<Face> removeCoveredPanels(List<Face> mesh) {
        List<Face> result = new ArrayList<>();
        for (Face detail : mesh) {
            List<Face> visible = new ArrayList<>();
            for (Face base : result) visible.addAll(trimPanel(base, detail));
            visible.add(detail);
            result = visible;
        }
        return List.copyOf(result);
    }

    private static List<Face> trimPanel(Face base, Face detail) {
        if (base.normal.dotProduct(detail.normal) < 0.999999
                || Math.abs(base.normal.dotProduct(base.a.subtract(detail.a))) > 1e-7) return List.of(base);
        int axis = Math.abs(base.normal.x) > 0.999999 ? 0 : Math.abs(base.normal.y) > 0.999999 ? 1
                : Math.abs(base.normal.z) > 0.999999 ? 2 : -1;
        if (axis < 0 || !rectangular(base) || !rectangular(detail)) return List.of(base);
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        double[] a = bounds(base, u, v), b = bounds(detail, u, v);
        double x1 = Math.max(a[0], b[0]), y1 = Math.max(a[1], b[1]);
        double x2 = Math.min(a[2], b[2]), y2 = Math.min(a[3], b[3]);
        if (x2 - x1 <= 1e-7 || y2 - y1 <= 1e-7) return List.of(base);
        List<Face> pieces = new ArrayList<>(4);
        panelPiece(pieces, base, u, v, a[0], a[1], x1, a[3]);
        panelPiece(pieces, base, u, v, x2, a[1], a[2], a[3]);
        panelPiece(pieces, base, u, v, x1, a[1], x2, y1);
        panelPiece(pieces, base, u, v, x1, y2, x2, a[3]);
        return pieces;
    }

    private static void panelPiece(List<Face> pieces, Face face, int u, int v, double x1, double y1, double x2, double y2) {
        if (x2 - x1 < 1e-7 || y2 - y1 < 1e-7) return;
        double[] bounds = bounds(face, u, v);
        Vec3d[] points = {face.a, face.b, face.c, face.d};
        ComponentAtlas.Uv[] uvs = new ComponentAtlas.Uv[4];
        Vec3d along = face.b.subtract(face.a), across = face.d.subtract(face.a);
        for (int i = 0; i < points.length; i++) {
            double x = component(points[i], u) < (bounds[0] + bounds[2]) / 2 ? x1 : x2;
            double y = component(points[i], v) < (bounds[1] + bounds[3]) / 2 ? y1 : y2;
            points[i] = coordinate(coordinate(points[i], u, x), v, y);
            Vec3d delta = points[i].subtract(face.a);
            double s = delta.dotProduct(along) / along.lengthSquared(), t = delta.dotProduct(across) / across.lengthSquared();
            uvs[i] = new ComponentAtlas.Uv((float) (face.ua.u() + s * (face.ub.u() - face.ua.u()) + t * (face.ud.u() - face.ua.u())),
                    (float) (face.ua.v() + s * (face.ub.v() - face.ua.v()) + t * (face.ud.v() - face.ua.v())));
        }
        pieces.add(new Face(face.material, points[0], points[1], points[2], points[3], face.normal, uvs[0], uvs[1], uvs[2], uvs[3]));
    }

    private static double[] bounds(Face face, int u, int v) {
        double x1 = Double.POSITIVE_INFINITY, y1 = x1, x2 = Double.NEGATIVE_INFINITY, y2 = x2;
        for (Vec3d p : new Vec3d[]{face.a, face.b, face.c, face.d}) {
            x1 = Math.min(x1, component(p, u)); y1 = Math.min(y1, component(p, v));
            x2 = Math.max(x2, component(p, u)); y2 = Math.max(y2, component(p, v));
        }
        return new double[]{x1, y1, x2, y2};
    }

    private static boolean rectangular(Face face) {
        Vec3d[] p = {face.a, face.b, face.c, face.d};
        for (int i = 0; i < p.length; i++) {
            Vec3d edge = p[(i + 1) % 4].subtract(p[i]);
            int changes = 0;
            for (int axis = 0; axis < 3; axis++) if (Math.abs(component(edge, axis)) > 1e-7) changes++;
            if (changes != 1) return false;
        }
        return true;
    }

    private static double component(Vec3d p, int axis) { return axis == 0 ? p.x : axis == 1 ? p.y : p.z; }
    private static Vec3d coordinate(Vec3d p, int axis, double value) {
        return new Vec3d(axis == 0 ? value : p.x, axis == 1 ? value : p.y, axis == 2 ? value : p.z);
    }

    private static List<Face> drill() {
        Builder b = new Builder("block_destruction_module");
        b.skin = b.body().front(b.atlas.region("metal"));
        b.box(Material.FRAME, 2.5, 0.5, 7.8, 13.5, 10.7, 15.7);
        b.skin = null;
        b.cylinder(Material.FRAME, 8, 6, 6.4, 9, 5.7, 5.7);
        b.cylinder(Material.ORANGE, 8, 6, 7.4, 7.8, 5.8, 5.8);
        b.cylinder(Material.STEEL, 8, 6, 6.6, 7.25, 5.6, 5.6);
        for (int i = 0; i < 5; i++) {
            double z = i * 1.3;
            double radius = 0.35 + i * 1.0;
            b.skin = b.atlas.surface("drill_cap");
            b.wrap = b.atlas.region("drill_wrap");
            b.cylinder(Material.STEEL, 8, 6, z, z + 0.95, radius, radius + 0.8);
            b.skin = null; b.wrap = null;
            b.cylinder(Material.FRAME, 8, 6, z + 0.95, z + 1.3, radius + 0.75, radius + 0.75);
        }
        for (double z : new double[]{8.5, 14.6}) {
            b.box(Material.STEEL, 2, 0.4, z, 3, 11.2, z + 0.8);
            b.box(Material.STEEL, 13, 0.4, z, 14, 11.2, z + 0.8);
            b.box(Material.STEEL, 2, 10.4, z, 14, 11.4, z + 0.8);
            for (double x : new double[]{2, 12}) {
                b.box(Material.FRAME, x - 0.02, 0, z - 0.22, x + 2.02, 2.02, z + 1.12);
                b.box(Material.FRAME, x - 0.02, 9.98, z - 0.22, x + 2.02, 12.02, z + 1.12);
                b.box(Material.CYAN, x + 0.6, 10.65, z - 0.23, x + 1.4, 11.35, z - 0.21);
            }
        }
        b.box(Material.FRAME, 4.1, 10.8, 10, 11.9, 11.35, 14);
        for (int i = 0; i < 5; i++) b.box(Material.STEEL, 4.3, 11.35, 10.1 + i * 0.75, 11.7, 11.65, 10.4 + i * 0.75);
        for (double x : new double[]{2.35, 13.55}) {
            b.box(Material.CYAN, x, 7.5, 10.2, x + 0.08, 8.3, 13.6);
            for (int i = 0; i < 3; i++) b.box(Material.ORANGE, x, 3, 10 + i, x + 0.08, 5.8, 10.45 + i);
        }
        return b.faces;
    }

    private static List<Face> targetFilter() {
        Builder b = new Builder("target_filter_module");
        b.box(Material.FRAME, 1.5, 0, 1.5, 14.5, 2.5, 14.5);
        b.skin = b.body().front(b.atlas.region("metal"));
        b.box(Material.STEEL, 2, 2.5, 3, 14, 10, 13);
        b.skin = null;
        b.box(Material.FRAME, 3, 3, 2.5, 13, 10, 3.1);
        b.ring(Material.CYAN, 8, 6.5, 2.3, 2.5, 3.1, 2.8);
        b.ring(Material.WHITE, 8, 6.5, 2.2, 2.3, 1.5, 1.3);
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            double x = 8 + Math.cos(angle) * 2.1, y = 6.5 + Math.sin(angle) * 2.1;
            b.box(Material.GOLD, x - 0.25, y - 0.25, 2.15, x + 0.25, y + 0.25, 2.3);
        }
        for (double x : new double[]{3, 12}) {
            b.box(Material.FRAME, x, 10, 10, x + 1, 14, 11);
            b.box(Material.CYAN, x - 0.2, 12.6, 9.8, x + 1.2, 13.2, 11.2);
        }
        return b.faces;
    }

    private static List<Face> damage() {
        Builder b = new Builder("entity_damage_module");
        b.skin = b.body().front(b.atlas.region("metal"));
        b.box(Material.FRAME, 3, 0.5, 4, 13, 13.4, 12.5);
        b.skin = null;
        b.ring(Material.STEEL, 8, 7, 2.7, 3.7, 5.25, 4.4);
        b.ring(Material.HEAT, 8, 7, 2.65, 3.6, 4.4, 3.65);
        b.ring(Material.FRAME, 8, 7, 2.1, 3.8, 3.7, 2.85);
        b.skin = b.atlas.surface("core");
        b.cylinder(Material.RED, 8, 7, 2, 3.9, 2.8, 2.8);
        b.skin = null;
        b.cylinder(Material.WHITE, 8, 7, 1.8, 2.05, 0.75, 0.95);
        for (double x : new double[]{1, 13}) for (double z : new double[]{1.8, 11.3}) {
            b.box(Material.FRAME, x, 0, z, x + 2, 14, z + 2.3);
            b.box(Material.STEEL, x, 0, z - 0.2, x + 2, 2.3, z + 0.2);
            b.box(Material.STEEL, x, 11.7, z - 0.2, x + 2, 14, z + 0.2);
            b.box(Material.RED, x + 0.7, 2.5, z - 0.05, x + 1.3, 11.5, z - 0.02);
        }
        for (double x : new double[]{4, 11}) b.box(Material.STEEL, x, 13.4, 4.1, x + 1, 14, 12.2);
        for (int i = 0; i < 3; i++) b.box(Material.RED, 6 + i * 1.4, 13.41, 6.1, 6.65 + i * 1.4, 13.6, 10.3);
        for (double y : new double[]{0.7, 12}) {
            b.box(Material.STEEL, 6.2, y, 2.6, 9.8, y + 1.2, 4.2);
            b.box(Material.CYAN, 7.2, y + 0.3, 2.57, 8.8, y + 0.8, 2.59);
        }
        for (double x : new double[]{2.8, 12.8}) for (int i = 0; i < 3; i++) {
            b.box(Material.RED, x, 3.5, 6 + i * 1.6, x + 0.35, 10.5, 6.65 + i * 1.6);
        }
        return b.faces;
    }

    private static List<Face> effect(int arrow) {
        // Reuse neutral metal and white luminous atlas regions, not a recolored red texture.
        Builder b = new Builder("entity_damage_module");
        b.box(Material.FRAME, 3, .5, 4, 13, 13.4, 12.5);
        for (double x : new double[]{1, 13}) for (double z : new double[]{1.8, 11.3}) {
            b.box(Material.FRAME, x, 0, z, x + 2, 14, z + 2.3);
            b.box(Material.STEEL, x, 0, z - .2, x + 2, 2.3, z + .2);
            b.box(Material.STEEL, x, 11.7, z - .2, x + 2, 14, z + .2);
            b.box(Material.WHITE, x + .7, 2.5, z - .05, x + 1.3, 11.5, z - .02);
        }
        b.ring(Material.STEEL, 8, 7, 2.7, 3.7, 5.25, 4.4);
        b.ring(Material.WHITE, 8, 7, 2.64, 2.7, 4.4, 4.05);
        b.cylinder(Material.FRAME, 8, 7, 2.72, 3.9, 4.05, 4.05);
        if (arrow == 0) {
            b.box(Material.WHITE, 7.2, 4.3, 2.52, 8.8, 9.7, 2.7);
            b.box(Material.WHITE, 5.3, 6.2, 2.52, 10.7, 7.8, 2.7);
        } else {
            for (int step = 0; step < 3; step++) {
                double y = 7 + arrow * (2 - step);
                for (int side : new int[]{-1, 1}) {
                    double x = 8 + side * step;
                    b.box(Material.WHITE, x - .65, y - .65, 2.52, x + .65, y + .65, 2.7);
                }
            }
            b.box(Material.WHITE, 7.4, 5, 2.52, 8.6, 8.4, 2.7);
        }
        for (double x : new double[]{4, 11}) b.box(Material.STEEL, x, 13.4, 4.1, x + 1, 14, 12.2);
        for (int i = 0; i < 3; i++) b.box(Material.WHITE, 6 + i * 1.4, 13.41, 6.1, 6.65 + i * 1.4, 13.6, 10.3);
        return b.faces;
    }

    private static List<Face> range(boolean advanced) {
        Builder b = new Builder(advanced ? "advanced_range_module" : "range_module");
        if (advanced) {
            b.box(Material.FRAME, 6.9, 0.2, 1.2, 9.1, 11.3, 14.8);
            for (double y : new double[]{3.3, 8.5}) {
                b.skin = b.atlas.surface("front").back(b.atlas.region("back"));
                b.wrap = b.atlas.region("energy");
                b.cylinder(Material.ENERGY, 8, y, 1, 14.5, 2.2, 2.2);
                b.skin = null; b.wrap = null;
                b.ring(Material.STEEL, 8, y, 0.6, 1.5, 3.05, 2.1);
                b.cylinder(Material.WHITE, 8, y, 0.55, 0.7, 0.6, 0.6);
                for (double x : new double[]{3.1, 12.1}) {
                    b.box(Material.FRAME, x - 0.15, y - 1, 2, x + 0.95, y + 1, 14);
                    double outer = x < 8 ? x - 0.2 : x + 0.99;
                    b.box(Material.CYAN, outer, y - 0.55, 2.3, outer + 0.05, y + 0.55, 13.7);
                    b.box(Material.STEEL, x, y + 1.25, 2, x + 0.8, y + 1.8, 14);
                }
            }
            for (double z : new double[]{1, 7.2, 13.8}) {
                for (double x : new double[]{1.7, 12.8}) {
                    b.box(Material.STEEL, x, 0, z, x + 1.5, 12.6, z + 0.9);
                    b.box(Material.GOLD, x - 0.1, 0, z - 0.1, x + 1.6, 1.4, z + 1);
                    b.box(Material.GOLD, x - 0.1, 11.2, z - 0.1, x + 1.6, 12.6, z + 1);
                }
                b.box(Material.FRAME, 2.8, 11.2, z, 13.2, 12.1, z + 0.9);
                b.box(Material.CYAN, 6.5, 12.1, z + 0.1, 9.5, 12.25, z + 0.8);
            }
            for (double x : new double[]{1.45, 14.2}) {
                b.skin = b.atlas.trim("metal").sides(b.atlas.region("left"), b.atlas.region("right"));
                b.box(Material.FRAME, x, 3.2, 6.6, x + 0.35, 9.4, 9.6);
                b.skin = null;
            }
        } else {
            b.skin = b.body();
            b.box(Material.FRAME, 3.4, 0.18, 1.12, 12.6, 7.6, 14.5);
            b.skin = null;
            for (double z : new double[]{1, 13.7}) {
                b.box(Material.STEEL, 3, 0, z, 4, 8, z + 1);
                b.box(Material.STEEL, 12, 0, z, 13, 8, z + 1);
                b.box(Material.STEEL, 4, 7, z, 12, 8, z + 1);
                b.box(Material.STEEL, 4, 0, z, 12, 1, z + 1);
            }
            b.box(Material.CYAN, 6, 2, 0.96, 10, 6, 0.99);
            b.box(Material.FRAME, 4.8, 7.6, 3, 11.2, 8, 11);
            for (int i = 0; i < 5; i++) b.box(Material.CYAN, 5.6, 8, 3.4 + i * 1.4, 6.8 + i * 0.7, 8.15, 4.25 + i * 1.4);
            b.box(Material.STEEL, 6.6, 7.6, 11, 9.4, 8.6, 13.8);
            b.box(Material.FRAME, 7.45, 8.6, 11.8, 8.55, 12.8, 12.9);
            b.box(Material.CYAN, 7, 11.4, 11.35, 9, 12.3, 13.35);
            b.box(Material.STEEL, 7, 12.3, 11.35, 9, 14, 13.35);
            b.box(Material.YELLOW, 10, 8, 12.3, 11, 8.1, 13.1);
        }
        return b.faces;
    }

    private static List<Face> lens() {
        Builder b = new Builder("thickness_module");
        b.skin = b.atlas.trim("metal").back(b.atlas.region("back"));
        b.wrap = b.atlas.region("wrap");
        b.cylinder(Material.FRAME, 8, 7, 2.4, 14.5, 6, 6);
        b.skin = null; b.wrap = null;
        for (double z : new double[]{2, 10.5, 14}) b.ring(Material.STEEL, 8, 7, z, z + 0.8, 6.65, 5.6);
        b.skin = b.atlas.surface("front");
        b.capRadius = 5.6;
        b.ring(Material.BLUE, 8, 7, 1.7, 2.5, 5.6, 4.65);
        b.cylinder(Material.CYAN, 8, 7, 1.6, 2.6, 4.65, 4.65);
        b.cylinder(Material.BLUE, 8, 7, 1.55, 1.6, 3.9, 3.9);
        b.cylinder(Material.CYAN, 8, 7, 1.5, 1.55, 2.8, 2.8);
        b.cylinder(Material.WHITE, 8, 7, 1.45, 1.5, 1.35, 1.35);
        b.skin = null; b.capRadius = 0;
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2;
            double x = 8 + Math.cos(angle) * 5.9, y = 7 + Math.sin(angle) * 5.9;
            b.box(Material.STEEL, x - 0.9, y - 0.9, 1.25, x + 0.9, y + 0.9, 3);
            b.box(Material.CYAN, x - 0.4, y - 0.4, 1.2, x + 0.4, y + 0.4, 1.24);
        }
        for (double x : new double[]{1.96, 13.96}) {
            b.box(Material.CYAN, x, 6.55, 4, x + 0.08, 7.45, 10);
            b.box(Material.FRAME, x - 0.35, 5.4, 10.3, x + 0.4, 8.6, 11.8);
        }
        b.box(Material.CYAN, 7.6, 13.02, 4, 8.4, 13.1, 10);
        b.box(Material.FRAME, 3, 0, 4, 5, 2, 13.5);
        b.box(Material.FRAME, 11, 0, 4, 13, 2, 13.5);
        return b.faces;
    }

    private static List<Face> advancedLens() {
        Builder b = new Builder("thickness_module");
        // Twin optical cartridges and a square amplifier frame distinguish tier II at inventory scale.
        b.box(Material.FRAME, .5, 0, 2, 15.5, 2, 15.5);
        b.box(Material.STEEL, 1, 2, 12.8, 15, 13.8, 15.2);
        b.box(Material.FRAME, 2, 3, 11.6, 14, 12.8, 12.8);
        for (double x : new double[]{4.7, 11.3}) {
            b.skin = b.atlas.trim("metal").back(b.atlas.region("back"));
            b.wrap = b.atlas.region("wrap");
            b.cylinder(Material.FRAME, x, 8.1, 3, 12.6, 2.8, 2.8);
            b.skin = null; b.wrap = null;
            for (double z : new double[]{3.2, 6.6, 10.1}) {
                b.ring(Material.GOLD, x, 8.1, z, z + .55, 3.12, 2.72);
                b.ring(Material.CYAN, x, 8.1, z + .6, z + .86, 2.97, 2.7);
            }
            b.ring(Material.STEEL, x, 8.1, 1.45, 3.19, 3.2, 2.55);
            b.skin = b.atlas.surface("front");
            b.capRadius = 2.55;
            b.ring(Material.BLUE, x, 8.1, 1.40, 2.1, 2.55, 2.1);
            b.cylinder(Material.CYAN, x, 8.1, 1.32, 1.4, 2.1, 2.1);
            b.skin = null; b.capRadius = 0;
            b.cylinder(Material.BLUE, x, 8.1, 1.28, 1.32, 1.6, 1.6);
            b.cylinder(Material.WHITE, x, 8.1, 1.23, 1.28, .62, .62);
        }
        for (double x : new double[]{.4, 14.2}) {
            b.box(Material.STEEL, x, 2, 2.1, x + 1.4, 14.4, 3.5);
            b.box(Material.GOLD, x + .25, 3, 2.02, x + 1.15, 13.8, 2.1);
            b.box(Material.FRAME, x, 13, 3.5, x + 1.4, 14.4, 14.7);
        }
        b.box(Material.FRAME, 1.8, 13, 2.1, 14.2, 14.4, 3.5);
        for (double x : new double[]{6, 9}) b.box(Material.WHITE, x, 13.15, 2.03, x + 1, 14.2, 2.09);
        b.box(Material.FRAME, 3, 2, 3.4, 13, 3.8, 11.6);
        for (int i = 0; i < 5; i++) b.box(Material.CYAN, 3.5 + i * 2, 2.3, 3.3, 4.5 + i * 2, 3.5, 3.39);
        return b.faces;
    }

    private static List<Face> cargoCollector() {
        Builder b = new Builder("block_drops_module");
        b.box(Material.FRAME, 1, 0, 2, 15, 2, 15);
        b.skin = b.body();
        b.box(Material.FRAME, 2, 2, 6, 14, 12, 14.5);
        b.skin = null;
        // A rectangular storage cassette with three drawers is distinct from the round Drops funnel.
        for (int row = 0; row < 3; row++) {
            double y = 2.4 + row * 3.1;
            b.box(Material.STEEL, 2.7, y, 5.3, 13.3, y + 2.5, 6.1);
            b.box(Material.FRAME, 4, y + .6, 5.05, 12, y + 1.9, 5.29);
            b.box(Material.CYAN, 4.2, y + .75, 5.01, 5.8, y + 1.75, 5.04);
            b.box(Material.STEEL, 7, y + .9, 4.7, 10.5, y + 1.5, 5.04);
        }
        for (double x : new double[]{1.5, 13.4}) {
            b.box(Material.STEEL, x, 1.3, 3.1, x + 1.1, 12.8, 4.2);
            b.box(Material.YELLOW, x + .15, 3.5, 3.01, x + .95, 10.6, 3.1);
        }
        b.box(Material.FRAME, 3, 12, 7, 13, 13.1, 13.5);
        b.box(Material.STEEL, 4, 13.1, 8, 5, 14.8, 11);
        b.box(Material.STEEL, 11, 13.1, 8, 12, 14.8, 11);
        b.box(Material.STEEL, 4, 14.8, 8, 12, 15.5, 11);
        for (int i = 0; i < 5; i++) b.box(Material.CYAN, 4.4 + i * 1.5, 12.01, 6, 5.1 + i * 1.5, 12.1, 6.8);
        return b.faces;
    }

    private static List<Face> motor() {
        Builder b = new Builder("electric_motor");
        b.skin = b.atlas.surface("front").back(b.atlas.region("back"));
        b.wrap = b.atlas.region("wrap");
        b.cylinder(Material.FRAME, 8, 6.8, 4, 14.5, 5.45, 5.45);
        b.skin = null; b.wrap = null;
        for (double z : new double[]{3.9, 13.1}) b.ring(Material.STEEL, 8, 6.8, z, z + .65, 5.7, 5.15);
        b.skin = b.atlas.surface("copper");
        b.ring(Material.GOLD, 8, 6.8, 3.82, 3.88, 4.3, 2.4);
        b.skin = b.atlas.surface("front");
        b.ring(Material.FRAME, 8, 6.8, 3.7, 3.81, 5.4, 2.35);
        b.skin = null;
        b.ring(Material.STEEL, 8, 6.8, 3.1, 4, 2.4, 1.5);
        b.cylinder(Material.STEEL, 8, 6.8, .2, 3.15, 1.35, 1.35);
        b.ring(Material.FRAME, 8, 6.8, .15, .2, .8, .55);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double x = 8 + Math.cos(angle) * 4.9, y = 6.8 + Math.sin(angle) * 4.9;
            b.skin = b.atlas.surface("bolt");
            b.cylinder(Material.STEEL, x, y, 3.52, 3.69, .42, .42);
            b.skin = null;
            if (i % 2 != 0) b.cylinder(Material.STEEL, x, y, 4.56, 13.05, .22, .22);
        }
        for (double x : new double[]{1.7, 11.3}) for (double z : new double[]{4.6, 12.1}) {
            b.box(Material.FRAME, x, 0, z, x + 3, .9, z + 3.3);
            b.box(Material.STEEL, x + .65, .9, z + .45, x + 2.35, 3.6, z + 2.85);
            b.skin = b.atlas.surface("bolt");
            b.box(Material.STEEL, x + .8, .91, z + .1, x + 2.2, .95, z + .4);
            b.skin = null;
        }
        b.skin = b.atlas.trim("metal").front(b.atlas.region("box_front")).top(b.atlas.region("box_top"));
        b.box(Material.FRAME, 4.7, 11.5, 6, 11.3, 14.7, 11);
        b.skin = null;
        b.box(Material.CYAN, 5.2, 14.71, 6.1, 10.8, 14.78, 6.45);
        for (double x : new double[]{5.7, 10.3}) {
            b.skin = b.atlas.surface("cable");
            Vec3d last = new Vec3d(x, 13.5, 10.9);
            for (int i = 1; i <= 8; i++) {
                double t = i / 8.0;
                Vec3d next = new Vec3d(x, 13.5 + Math.sin(t * Math.PI) * 1.6 - t * 2, 10.9 + t * 3.5);
                b.tube(Material.FRAME, last, next, .38);
                last = next;
            }
            b.skin = null;
            b.cylinder(Material.CYAN, x, 13.5, 10.95, 11.2, .48, .48);
        }
        return b.faces;
    }

    private static List<Face> circuit() {
        Builder b = new Builder("control_circuit");
        b.skin = b.atlas.surface("side").top(b.atlas.region("top")).bottom(b.atlas.region("bottom"));
        b.box(Material.FRAME, 1, 0, 1, 15, 1.15, 15);
        b.skin = b.atlas.surface("processor_side").top(b.atlas.region("processor"));
        b.box(Material.FRAME, 5.2, 1.15, 5.2, 10.8, 2.6, 10.8);
        b.skin = null;
        b.box(Material.CYAN, 6.7, 2.6, 6.7, 9.3, 2.7, 9.3);
        b.box(Material.WHITE, 7.45, 2.7, 7.45, 8.55, 2.76, 8.55);
        for (int side = 0; side < 4; side++) {
            b.turn = side;
            b.box(Material.STEEL, 2.5, 1.15, 1, 13.5, 1.65, 1.6);
            b.box(Material.CYAN, 4, 0.4, 0.97, 12, 0.8, 1);
            for (int i = 0; i < 4; i++) {
                double x = 5.5 + i * 1.35;
                b.box(Material.STEEL, x, 1.25, 4.65, x + 0.55, 2, 5.3);
            }
            b.box(Material.FRAME, 2.5, 1.15, 3.8, 4.3, 1.9, 5.5);
            b.box(Material.STEEL, 2.4, 1.15, 5.6, 3.1, 1.65, 6.3);
            b.box(Material.STEEL, 3.5, 1.15, 5.6, 4.2, 1.65, 6.3);
            b.box(Material.STEEL, 1, 1.15, 1, 3, 2.5, 1.7);
            b.box(Material.STEEL, 1, 1.15, 1.7, 1.7, 2.5, 3);
            b.box(Material.CYAN, 1.8, 1.16, 1.8, 2.7, 1.7, 2.7);
        }
        return b.faces;
    }

    private static List<Face> silk() {
        Builder b = new Builder("silk_touch_module");
        for (double y : new double[]{0, 13.3}) {
            b.skin = b.atlas.surface("side").top(b.atlas.region("top")).bottom(b.atlas.region("bottom"));
            b.box(Material.FRAME, 2, y, 2, 14, y + 2.5, 14);
            b.skin = null;
            b.box(Material.STEEL, 4, y + 2.1, 4, 12, y + 2.6, 12);
            b.box(Material.CYAN, 6, y + 2.6, 6, 10, y + 2.7, 10);
        }
        for (double x : new double[]{1, 12}) for (double z : new double[]{1, 12}) {
            b.box(Material.STEEL, x + 0.5, 2.5, z + 0.5, x + 2.5, 13.5, z + 2.5);
            b.box(Material.FRAME, x, 0, z, x + 3, 3, z + 3);
            b.box(Material.FRAME, x, 13, z, x + 3, 16, z + 3);
            b.box(Material.CYAN, x + 1.15, 3.2, z + 0.43, x + 1.85, 12.8, z + 0.48);
            b.box(Material.CYAN, x + 1.15, 3.2, z + 2.52, x + 1.85, 12.8, z + 2.57);
        }
        b.box(Material.CYAN, 7.6, 2.7, 7.6, 8.4, 13.5, 8.4);
        b.skin = b.atlas.surface("cube").back(b.atlas.region("cube_back"));
        b.box(Material.FRAME, 5.2, 5.2, 5.2, 10.8, 10.8, 10.8);
        b.skin = null;
        for (int side = 0; side < 4; side++) {
            b.turn = side;
            b.box(Material.STEEL, 5, 5, 4.95, 11, 5.8, 5.3);
            b.box(Material.STEEL, 5, 10.2, 4.95, 11, 11, 5.3);
            b.box(Material.STEEL, 5, 5.8, 4.95, 5.8, 10.2, 5.3);
            b.box(Material.STEEL, 10.2, 5.8, 4.95, 11, 10.2, 5.3);
            b.box(Material.CYAN, 6.1, 6.1, 5.1, 9.9, 9.9, 5.18);
            b.box(Material.WHITE, 7.1, 7.1, 5.04, 8.9, 8.9, 5.09);
        }
        return b.faces;
    }

    private static List<Face> collector(boolean storage) {
        Builder b = new Builder("block_drops_module");
        b.skin = b.body();
        b.wrap = b.atlas.region("wrap");
        b.cylinder(Material.FRAME, 8, 7, 5.8, 14.8, 3.8, 3.8);
        b.skin = null; b.wrap = null;
        for (double z : new double[]{7.8, 13.5}) b.ring(Material.STEEL, 8, 7, z, z + 0.7, 4.2, 3.3);
        b.ring(Material.CYAN, 8, 7, 12.6, 13.1, 4, 3.5);
        // The intake is a hollow tapered funnel, not a luminous flat disc.
        b.skin = b.atlas.surface("intake");
        b.funnel(Material.FRAME, 8, 7, 1.6, 6.5, 6.4, 2.5);
        b.skin = null;
        b.ring(Material.STEEL, 8, 7, 1.2, 1.9, 6.7, 5.9);
        b.cylinder(Material.CYAN, 8, 7, 5.65, 5.8, 2.45, 2.45);
        b.cylinder(Material.WHITE, 8, 7, 5.6, 5.65, 0.8, 0.8);
        for (double x : new double[]{1, 12.7}) for (double y : new double[]{0.5, 11.3}) {
            b.box(Material.FRAME, x, y, 1, x + 2.3, y + 2.3, 3.3);
            b.box(Material.CYAN, x + 0.6, y + 0.6, 0.95, x + 1.7, y + 1.7, 0.99);
        }
        for (double x : new double[]{3, 12}) {
            b.box(Material.STEEL, x, 0, 8, x + 1, 12, 9);
            b.box(Material.YELLOW, x + 0.1, 10.4, 7.95, x + 0.9, 11.3, 7.99);
            b.box(Material.CYAN, x + 0.1, 4.5, 7.95, x + 0.9, 8.5, 7.99);
        }
        b.box(Material.FRAME, 6.2, 10.8, 9.3, 9.8, 13.6, 12.9);
        b.box(Material.STEEL, 5.9, 12.4, 9, 10.1, 13, 13.2);
        b.box(Material.CYAN, 7.2, 13.6, 10.3, 8.8, 13.7, 11.9);
        b.box(Material.STEEL, 6.5, 5.5, 3.1, 7.4, 6.4, 4);
        b.box(Material.STEEL, 9.2, 7.8, 4, 9.9, 8.5, 4.7);
        if (storage) {
            b.box(Material.FRAME, 2.6, .2, 9.4, 13.4, 3.1, 15.2);
            for (double x : new double[]{3, 12.2}) {
                b.box(Material.STEEL, x, 3.1, 12, x + .8, 14.2, 13.1);
                b.box(Material.CYAN, x + .1, 4, 11.94, x + .7, 10.5, 11.99);
            }
            b.box(Material.STEEL, 3, 14.2, 12, 13, 15, 13.1);
        }
        return b.faces;
    }

    private static List<Face> thermal(boolean ignition) {
        Builder b = new Builder(ignition ? "ignition_module" : "scorch_marks_module");
        Material glow = ignition ? Material.HEAT : Material.ORANGE;
        b.skin = new ComponentAtlas.Skin(b.atlas.region("chamber_0"), b.atlas.region("chamber_2"),
                b.atlas.region("chamber_3"), b.atlas.region("chamber_1"), b.atlas.region("orange"), b.atlas.region("orange"), false);
        b.box(glow, 4.3, 2, 4.3, 11.7, 14, 11.7);
        b.skin = null;
        for (double y : new double[]{0, 13.5}) {
            b.skin = b.atlas.surface(y == 0 ? "vent" : "hazard").top(b.atlas.region("top")).bottom(b.atlas.region("bottom"));
            b.box(Material.FRAME, 3, y, 3, 13, y + 2.5, 13);
            b.skin = null;
            b.box(Material.STEEL, 3.4, y + 0.4, 3.4, 12.6, y + 0.9, 12.6);
        }
        for (int side = 0; side < 4; side++) {
            b.turn = side;
            for (double x : new double[]{3, 11.7}) {
                b.box(Material.STEEL, x, 2, 3, x + 1.3, 14, 4.3);
                for (double y : new double[]{2, 12.6}) b.box(Material.FRAME, x - 0.2, y, 2.8, x + 1.5, y + 1.4, 4.5);
            }
            for (double y : new double[]{5.7, 9.7}) {
                b.box(Material.FRAME, 3.8, y, 3.8, 12.2, y + 0.7, 4.3);
            }
            if (ignition) {
                for (int i = 0; i < 4; i++) {
                    double y = 3.4 + i * 2.2;
                    b.box(Material.STEEL, 3.1, y, 2.4, 12.9, y + 0.45, 4.3);
                }
                b.box(Material.YELLOW, 7.2, 6.6, 4.15, 8.8, 9.4, 4.25);
            } else {
                for (int i = 0; i < 4; i++) {
                    b.box(Material.GOLD, 3.7 + i * 2, 14.8, 2.95, 4.6 + i * 2, 15.8, 3.05);
                    b.box(Material.YELLOW, 5.4 + i * 1.4, 0.6, 2.95, 5.95 + i * 1.4, 1.6, 3.05);
                }
            }
        }
        b.turn = 0;
        b.box(Material.FRAME, 5, 15.5, 5, 11, 15.85, 11);
        b.box(glow, 6.2, 15.85, 6.2, 9.8, 15.95, 9.8);
        return b.faces;
    }

    private static final class Builder {
        private final List<Face> faces = new ArrayList<>();
        private final ComponentAtlas atlas;
        private ComponentAtlas.Skin skin;
        private ComponentAtlas.Region wrap;
        private double capRadius;
        private int turn;
        private Vec3d min, max;

        private Builder(String id) { atlas = ComponentAtlas.load("module/" + id, "default"); }

        private ComponentAtlas.Skin body() {
            return new ComponentAtlas.Skin(atlas.regionOr("front", "metal"), atlas.regionOr("back", "metal"),
                    atlas.regionOr("left", "metal"), atlas.regionOr("right", "metal"),
                    atlas.regionOr("top", "metal"), atlas.regionOr("bottom", "metal"), false);
        }

        private ComponentAtlas.Skin materialSkin(Material material) {
            String key = switch (material) {
                case FRAME -> "metal";
                case STEEL -> "steel";
                case CYAN -> "light";
                default -> material.name().toLowerCase(java.util.Locale.ROOT);
            };
            var region = atlas.regionOr(key, material.emissive ? "light" : "steel");
            return new ComponentAtlas.Skin(region, region, region, region, region, region, !material.emissive, true);
        }

        private Vec3d point(double x, double y, double z) { return new Vec3d(x, y, z); }

        private Vec3d turn(Vec3d point) {
            double x = point.x, y = point.y, z = point.z;
            return switch (turn) {
                case 1 -> new Vec3d(16 - z, y, x);
                case 2 -> new Vec3d(16 - x, y, 16 - z);
                case 3 -> new Vec3d(z, y, 16 - x);
                default -> point;
            };
        }

        private void add(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                         ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud) {
            a = turn(a); b = turn(b); c = turn(c); d = turn(d);
            Vec3d normal = b.subtract(a).crossProduct(c.subtract(a));
            normal = normal.multiply(1 / normal.length());
            faces.add(new Face(material, a, b, c, d, normal, ua, ub, uc, ud));
        }

        private void face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
            var selected = skin != null ? skin : materialSkin(material);
            Vec3d normal = b.subtract(a).crossProduct(c.subtract(a));
            add(material, a, b, c, d, selected.project(a, normal, min, max), selected.project(b, normal, min, max),
                    selected.project(c, normal, min, max), selected.project(d, normal, min, max));
        }

        private void planarFace(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d center, double radius) {
            Vec3d normal = b.subtract(a).crossProduct(c.subtract(a));
            var region = (skin != null ? skin : materialSkin(material)).face(normal);
            double r = capRadius > 0 ? capRadius : radius;
            add(material, a, b, c, d, planarUv(region, a, center, r), planarUv(region, b, center, r),
                    planarUv(region, c, center, r), planarUv(region, d, center, r));
        }

        private ComponentAtlas.Uv planarUv(ComponentAtlas.Region region, Vec3d point, Vec3d center, double radius) {
            return region.uv(0.5 + (point.x - center.x) / (2 * radius), 0.5 - (point.y - center.y) / (2 * radius));
        }

        private void box(Material material, double x1, double y1, double z1, double x2, double y2, double z2) {
            min = point(x1, y1, z1); max = point(x2, y2, z2);
            Vec3d a = point(x1, y1, z1), b = point(x1, y2, z1), c = point(x2, y2, z1), d = point(x2, y1, z1);
            Vec3d e = point(x1, y1, z2), f = point(x1, y2, z2), g = point(x2, y2, z2), h = point(x2, y1, z2);
            face(material, a, b, c, d); face(material, h, g, f, e);
            face(material, e, f, b, a); face(material, d, c, g, h);
            face(material, b, f, g, c); face(material, e, a, d, h);
        }

        private void tube(Material material, Vec3d start, Vec3d end, double radius) {
            Vec3d axis = end.subtract(start).normalize();
            Vec3d u = axis.crossProduct(new Vec3d(1, 0, 0)).normalize().multiply(radius);
            Vec3d v = axis.crossProduct(u).normalize().multiply(radius);
            min = new Vec3d(Math.min(start.x, end.x) - radius, Math.min(start.y, end.y) - radius, Math.min(start.z, end.z) - radius);
            max = new Vec3d(Math.max(start.x, end.x) + radius, Math.max(start.y, end.y) + radius, Math.max(start.z, end.z) + radius);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, c = (i + 1) * Math.PI / 4;
                Vec3d p = u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));
                Vec3d q = u.multiply(Math.cos(c)).add(v.multiply(Math.sin(c)));
                face(material, start.add(p), start.add(q), end.add(q), end.add(p));
            }
        }

        private Vec3d[] circle(double x, double y, double z, double radius) {
            Vec3d[] ring = new Vec3d[12];
            for (int i = 0; i < ring.length; i++) {
                double angle = i * Math.PI * 2 / ring.length;
                ring[i] = point(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius, z);
            }
            return ring;
        }

        private void join(Material material, Vec3d[] front, Vec3d[] back, boolean inward) {
            var region = wrap != null ? wrap : materialSkin(material).front();
            for (int i = 0; i < front.length; i++) {
                int n = (i + 1) % front.length;
                var a = region.uv((double) i / front.length, 0);
                var b = region.uv((double) (i + 1) / front.length, 0);
                var c = region.uv((double) (i + 1) / front.length, 1);
                var d = region.uv((double) i / front.length, 1);
                if (inward) add(material, front[n], front[i], back[i], back[n], b, a, d, c);
                else add(material, front[i], front[n], back[n], back[i], a, b, c, d);
            }
        }

        private void cylinder(Material material, double x, double y, double z1, double z2, double r1, double r2) {
            Vec3d[] front = circle(x, y, z1, r1), back = circle(x, y, z2, r2);
            join(material, front, back, false);
            for (int i = 0; i < front.length; i++) {
                int n = (i + 1) % front.length;
                planarFace(material, point(x, y, z1), front[n], front[i], front[i], point(x, y, z1), r1);
                planarFace(material, point(x, y, z2), back[i], back[n], back[n], point(x, y, z2), r2);
            }
        }

        private void ring(Material material, double x, double y, double z1, double z2, double outer, double inner) {
            Vec3d[] a = circle(x, y, z1, outer), b = circle(x, y, z2, outer);
            Vec3d[] c = circle(x, y, z1, inner), d = circle(x, y, z2, inner);
            join(material, a, b, false); join(material, c, d, true);
            for (int i = 0; i < a.length; i++) {
                int n = (i + 1) % a.length;
                planarFace(material, a[i], c[i], c[n], a[n], point(x, y, z1), outer);
                planarFace(material, b[n], d[n], d[i], b[i], point(x, y, z2), outer);
            }
        }

        private void funnel(Material material, double x, double y, double z1, double z2, double r1, double r2) {
            join(material, circle(x, y, z1, r1), circle(x, y, z2, r2), false);
            var front = circle(x, y, z1, r1 - 0.35);
            var back = circle(x, y, z2, r2 - 0.2);
            for (int i = 0; i < front.length; i++) {
                int n = (i + 1) % front.length;
                planarFace(material, front[n], front[i], back[i], back[n], point(x, y, z1), r1);
            }
        }
    }

    private LaserModuleModel() { }
}
