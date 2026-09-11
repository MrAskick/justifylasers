package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LaserModuleModel {
    enum Material {
        FRAME("frame", false), STEEL("steel", false), GOLD("gold", false),
        CYAN("cyan_light", true), RED("red_light", true), ORANGE("orange", true),
        WHITE("white_light", true), BLUE("blue", true), YELLOW("yellow", true),
        ENERGY("cyan", true), HEAT("red", true);

        final Identifier texture;
        final boolean emissive;

        Material(String texture, boolean emissive) {
            this.texture = JustifyLasers.id("textures/item/crystal/" + texture + ".png");
            this.emissive = emissive;
        }
    }

    record Face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal,
                Vec3d uvCenter, double uvRadius) { }

    private static final Map<String, List<Face>> MESHES = buildMeshes();

    public static void render(String id, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, int overlay) {
        List<Face> mesh = MESHES.get(id);
        if (mesh == null) return;
        matrices.push();
        try {
            matrices.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
            for (Material material : Material.values()) {
                VertexConsumer buffer = consumers.getBuffer(material.emissive && mode == ModelTransformationMode.GUI
                        ? LaserCrystalModel.EmissiveLayers.get(material.texture) : RenderLayer.getEntitySolid(material.texture));
                int illumination = material.emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
                for (Face face : mesh) {
                    if (face.material != material) continue;
                    vertex(buffer, matrices, face, face.a, 0, 1, illumination, overlay);
                    vertex(buffer, matrices, face, face.b, 0, 0, illumination, overlay);
                    vertex(buffer, matrices, face, face.c, 1, 0, illumination, overlay);
                    vertex(buffer, matrices, face, face.d, 1, 1, illumination, overlay);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    static Map<String, List<Face>> meshes() {
        return MESHES;
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Face face, Vec3d point,
                               float u, float v, int light, int overlay) {
        if (face.uvRadius > 0) {
            // A lens cap shares one planar UV field across all triangles, avoiding radial texture seams.
            u = (float) (0.5 + (point.x - face.uvCenter.x) / (2 * face.uvRadius));
            v = (float) (0.5 - (point.y - face.uvCenter.y) / (2 * face.uvRadius));
        }
        Vec3d normal = face.normal;
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(),
                        (float) point.x, (float) point.y, (float) point.z)
                .color(255, 255, 255, 255).texture(u, v).overlay(overlay).light(light),
                matrices.peek().getNormalMatrix(), (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private static Map<String, List<Face>> buildMeshes() {
        Map<String, List<Face>> result = new LinkedHashMap<>();
        result.put("block_destruction_module", drill());
        result.put("entity_damage_module", damage());
        result.put("range_module", range(false));
        result.put("advanced_range_module", range(true));
        result.put("thickness_module", lens());
        result.put("control_circuit", circuit());
        result.put("silk_touch_module", silk());
        result.put("block_drops_module", collector());
        result.put("scorch_marks_module", thermal(false));
        result.put("ignition_module", thermal(true));
        result.replaceAll((id, mesh) -> List.copyOf(mesh));
        return Map.copyOf(result);
    }

    private static List<Face> drill() {
        Builder b = new Builder();
        b.box(Material.FRAME, 2.5, 0.5, 7.8, 13.5, 10.7, 15.7);
        b.cylinder(Material.FRAME, 8, 6, 6.4, 9, 5.7, 5.7);
        b.cylinder(Material.ORANGE, 8, 6, 7.4, 7.8, 5.8, 5.8);
        b.cylinder(Material.STEEL, 8, 6, 6.6, 7.25, 5.6, 5.6);
        for (int i = 0; i < 5; i++) {
            double z = i * 1.3;
            double radius = 0.35 + i * 1.0;
            b.cylinder(Material.STEEL, 8, 6, z, z + 0.95, radius, radius + 0.8);
            b.cylinder(Material.FRAME, 8, 6, z + 0.95, z + 1.3, radius + 0.75, radius + 0.75);
        }
        for (double z : new double[]{8.5, 14.6}) {
            b.box(Material.STEEL, 2, 0.4, z, 3, 11.2, z + 0.8);
            b.box(Material.STEEL, 13, 0.4, z, 14, 11.2, z + 0.8);
            b.box(Material.STEEL, 2, 10.4, z, 14, 11.4, z + 0.8);
            for (double x : new double[]{2, 12}) {
                b.box(Material.FRAME, x, 0, z - 0.2, x + 2, 2, z + 1.1);
                b.box(Material.FRAME, x, 10, z - 0.2, x + 2, 12, z + 1.1);
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

    private static List<Face> damage() {
        Builder b = new Builder();
        b.box(Material.FRAME, 3, 0.5, 4, 13, 13.4, 12.5);
        b.ring(Material.STEEL, 8, 7, 2.7, 3.7, 5.25, 4.4);
        b.ring(Material.HEAT, 8, 7, 2.65, 3.6, 4.4, 3.65);
        b.ring(Material.FRAME, 8, 7, 2.1, 3.8, 3.7, 2.85);
        b.cylinder(Material.RED, 8, 7, 2, 3.9, 2.8, 2.8);
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

    private static List<Face> range(boolean advanced) {
        Builder b = new Builder();
        if (advanced) {
            b.box(Material.FRAME, 6.9, 0.2, 1.2, 9.1, 11.3, 14.8);
            for (double y : new double[]{3.3, 8.5}) {
                b.cylinder(Material.ENERGY, 8, y, 1, 14.5, 2.2, 2.2);
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
                b.box(Material.FRAME, x, 3.2, 6.6, x + 0.35, 9.4, 9.6);
                for (int i = 0; i < 3; i++) b.box(Material.GOLD, x - 0.05, 4, 7 + i * 0.75, x + 0.4, 5.5 + i, 7.4 + i * 0.75);
            }
        } else {
            b.box(Material.FRAME, 3.4, 0.18, 1.12, 12.6, 7.6, 14.5);
            for (double z : new double[]{1, 13.7}) {
                b.box(Material.STEEL, 3, 0, z, 4, 8, z + 1);
                b.box(Material.STEEL, 12, 0, z, 13, 8, z + 1);
                b.box(Material.STEEL, 4, 7, z, 12, 8, z + 1);
                b.box(Material.STEEL, 4, 0, z, 12, 1, z + 1);
            }
            b.box(Material.CYAN, 6, 2, 0.96, 10, 6, 0.99);
            b.box(Material.FRAME, 4.8, 7.6, 3, 11.2, 8, 11);
            for (int i = 0; i < 5; i++) b.box(Material.CYAN, 5.6, 8, 3.4 + i * 1.4, 6.8 + i * 0.7, 8.15, 4.25 + i * 1.4);
            for (double x : new double[]{3.32, 12.63}) for (int i = 0; i < 3; i++) {
                for (int step = 0; step < 3; step++) {
                    double z = 4.2 + i * 2.4 + step * 0.35;
                    b.box(Material.CYAN, x, 2 + step * 0.45, z, x + 0.05, 2.5 + step * 0.45, z + 0.65);
                    b.box(Material.CYAN, x, 4.25 - step * 0.45, z, x + 0.05, 4.75 - step * 0.45, z + 0.65);
                }
            }
            b.box(Material.STEEL, 6.6, 7.6, 11, 9.4, 8.6, 13.8);
            b.box(Material.FRAME, 7.45, 8.6, 11.8, 8.55, 12.8, 12.9);
            b.box(Material.CYAN, 7, 11.4, 11.35, 9, 12.3, 13.35);
            b.box(Material.STEEL, 7, 12.3, 11.35, 9, 14, 13.35);
            b.box(Material.YELLOW, 10, 8, 12.3, 11, 8.1, 13.1);
        }
        return b.faces;
    }

    private static List<Face> lens() {
        Builder b = new Builder();
        b.cylinder(Material.FRAME, 8, 7, 2.4, 14.5, 6, 6);
        for (double z : new double[]{2, 10.5, 14}) b.ring(Material.STEEL, 8, 7, z, z + 0.8, 6.65, 5.6);
        b.ring(Material.BLUE, 8, 7, 1.7, 2.5, 5.6, 4.65);
        b.cylinder(Material.CYAN, 8, 7, 1.6, 2.6, 4.65, 4.65);
        b.cylinder(Material.BLUE, 8, 7, 1.55, 1.6, 3.9, 3.9);
        b.cylinder(Material.CYAN, 8, 7, 1.5, 1.55, 2.8, 2.8);
        b.cylinder(Material.WHITE, 8, 7, 1.45, 1.5, 1.35, 1.35);
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

    private static List<Face> circuit() {
        Builder b = new Builder();
        b.box(Material.FRAME, 1, 0, 1, 15, 1.15, 15);
        b.box(Material.FRAME, 5.2, 1.15, 5.2, 10.8, 2.6, 10.8);
        b.box(Material.CYAN, 6.7, 2.6, 6.7, 9.3, 2.7, 9.3);
        b.box(Material.WHITE, 7.45, 2.7, 7.45, 8.55, 2.76, 8.55);
        for (int side = 0; side < 4; side++) {
            b.turn = side;
            b.box(Material.STEEL, 2.5, 1.15, 1, 13.5, 1.65, 1.6);
            b.box(Material.CYAN, 4, 0.4, 0.97, 12, 0.8, 1);
            for (int i = 0; i < 4; i++) {
                double x = 5.5 + i * 1.35;
                b.box(Material.STEEL, x, 1.25, 4.65, x + 0.55, 2, 5.3);
                b.box(Material.CYAN, x + 0.1, 1.16, 2.7, x + 0.35, 1.25, 4.65);
                b.box(Material.CYAN, x - 0.25, 1.16, 2.4, x + 0.75, 1.4, 3.1);
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
        Builder b = new Builder();
        for (double y : new double[]{0, 13.3}) {
            b.box(Material.FRAME, 2, y, 2, 14, y + 2.5, 14);
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
        b.box(Material.FRAME, 5.2, 5.2, 5.2, 10.8, 10.8, 10.8);
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

    private static List<Face> collector() {
        Builder b = new Builder();
        b.cylinder(Material.FRAME, 8, 7, 5.8, 14.8, 3.8, 3.8);
        for (double z : new double[]{7.8, 13.5}) b.ring(Material.STEEL, 8, 7, z, z + 0.7, 4.2, 3.3);
        b.ring(Material.CYAN, 8, 7, 12.6, 13.1, 4, 3.5);
        // The intake is a hollow tapered funnel, not a luminous flat disc.
        b.funnel(Material.FRAME, 8, 7, 1.6, 6.5, 6.4, 2.5);
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
        return b.faces;
    }

    private static List<Face> thermal(boolean ignition) {
        Builder b = new Builder();
        Material glow = ignition ? Material.HEAT : Material.ORANGE;
        b.box(glow, 4.3, 2, 4.3, 11.7, 14, 11.7);
        for (double y : new double[]{0, 13.5}) {
            b.box(Material.FRAME, 3, y, 3, 13, y + 2.5, 13);
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
        private int turn;

        private Vec3d point(double x, double y, double z) {
            return switch (turn) {
                case 1 -> new Vec3d(16 - z, y, x);
                case 2 -> new Vec3d(16 - x, y, 16 - z);
                case 3 -> new Vec3d(z, y, 16 - x);
                default -> new Vec3d(x, y, z);
            };
        }

        private void face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
            faces.add(new Face(material, a, b, c, d, b.subtract(a).crossProduct(c.subtract(a)).normalize(), Vec3d.ZERO, 0));
        }

        private void cap(Material material, Vec3d center, Vec3d a, Vec3d b, double radius) {
            planarFace(material, center, a, b, b, center, radius);
        }

        private void planarFace(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d center, double radius) {
            faces.add(new Face(material, a, b, c, d, b.subtract(a).crossProduct(c.subtract(a)).normalize(), center, radius));
        }

        private void box(Material material, double x1, double y1, double z1, double x2, double y2, double z2) {
            Vec3d a = point(x1, y1, z1), b = point(x1, y2, z1), c = point(x2, y2, z1), d = point(x2, y1, z1);
            Vec3d e = point(x1, y1, z2), f = point(x1, y2, z2), g = point(x2, y2, z2), h = point(x2, y1, z2);
            face(material, a, b, c, d);
            face(material, h, g, f, e);
            face(material, e, f, b, a);
            face(material, d, c, g, h);
            face(material, b, f, g, c);
            face(material, e, a, d, h);
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
            for (int i = 0; i < front.length; i++) {
                int n = (i + 1) % front.length;
                if (inward) face(material, front[n], front[i], back[i], back[n]);
                else face(material, front[i], front[n], back[n], back[i]);
            }
        }

        private void cylinder(Material material, double x, double y, double z1, double z2, double r1, double r2) {
            Vec3d[] front = circle(x, y, z1, r1), back = circle(x, y, z2, r2);
            join(material, front, back, false);
            for (int i = 0; i < front.length; i++) {
                int n = (i + 1) % front.length;
                cap(material, point(x, y, z1), front[n], front[i], r1);
                cap(material, point(x, y, z2), back[i], back[n], r2);
            }
        }

        private void ring(Material material, double x, double y, double z1, double z2, double outer, double inner) {
            Vec3d[] a = circle(x, y, z1, outer), b = circle(x, y, z2, outer);
            Vec3d[] c = circle(x, y, z1, inner), d = circle(x, y, z2, inner);
            join(material, a, b, false);
            join(material, c, d, true);
            for (int i = 0; i < a.length; i++) {
                int n = (i + 1) % a.length;
                planarFace(material, a[i], c[i], c[n], a[n], point(x, y, z1), outer);
                planarFace(material, b[n], d[n], d[i], b[i], point(x, y, z2), outer);
            }
        }

        private void funnel(Material material, double x, double y, double z1, double z2, double r1, double r2) {
            join(material, circle(x, y, z1, r1), circle(x, y, z2, r2), false);
            join(material, circle(x, y, z1, r1 - 0.35), circle(x, y, z2, r2 - 0.2), true);
        }
    }

    private LaserModuleModel() { }
}
