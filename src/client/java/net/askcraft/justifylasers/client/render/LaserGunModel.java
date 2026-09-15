package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.function.UnaryOperator;

public final class LaserGunModel {
    enum Material { FRAME, STEEL, RUBBER, COLOR, CORE, GLASS }
    record Face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal,
                ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud) { }
    private static final List<Face> GUN = gun();
    private static final Map<LaserColor, List<Face>> STANDS = variants(false);
    private static final Map<LaserColor, List<Face>> CRADLES = variants(true);
    private static final ItemModelBounds GUN_BOUNDS = ItemModelBounds.of(GUN.stream()
            .flatMap(face -> java.util.stream.Stream.of(face.a(), face.b(), face.c(), face.d())).toList());

    public static void fitGui(MatrixStack matrices) { GUN_BOUNDS.fitGui(matrices); }
    static List<Face> mesh() { return GUN; }
    static List<Face> standMesh(LaserColor color) { return STANDS.get(color); }
    static List<Face> cradleMesh(LaserColor color) { return CRADLES.get(color); }

    private static Map<LaserColor, List<Face>> variants(boolean cradle) {
        Map<LaserColor, List<Face>> result = new EnumMap<>(LaserColor.class);
        for (LaserColor color : LaserColor.values()) result.put(color, cradle ? cradle(color) : stand(color));
        return Map.copyOf(result);
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int color) {
        draw(GUN, "gun", matrices, consumers, light, color, true);
    }

    public static void renderFirstPerson(MatrixStack matrices, VertexConsumerProvider consumers, int light, int color) {
        // The scope compositor supplies the lens; translucent hand glass would refract/bleach its source frame in Iris.
        draw(GUN, "gun", matrices, consumers, light, color, false);
    }

    public static void renderStand(MatrixStack matrices, VertexConsumerProvider consumers, int light, int color, float yaw) {
        draw(STANDS.get(LaserColor.nearest(color)), "turret", matrices, consumers, light, color, true);
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        draw(CRADLES.get(LaserColor.nearest(color)), "turret", matrices, consumers, light, color, true);
        matrices.pop();
    }

    private static void draw(List<Face> mesh, String kind, MatrixStack matrices, VertexConsumerProvider consumers, int light, int color, boolean glass) {
        for (Material material : Material.values()) {
            if (!glass && material == Material.GLASS) continue;
            boolean glowing = material == Material.COLOR || material == Material.CORE;
            RenderLayer layer = material == Material.GLASS ? LaserRenderLayers.CUBE_LENS
                    : material == Material.CORE && IrisCompatibility.isShaderPackInUse() ? LaserRenderLayers.SHADER_EMISSION
                    : RenderLayer.getEntitySolid(material == Material.CORE ? JustifyLasers.id("textures/effect/beam.png") : ComponentAtlas.texture(kind, color));
            VertexConsumer buffer = consumers.getBuffer(layer);
            int rgb = material == Material.GLASS ? 0xBDDEFF : 0xFFFFFF;
            int illumination = glowing ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            for (Face face : mesh) {
                if (face.material() != material) continue;
                int alpha = material == Material.GLASS ? 25 : 255;
                vertex(buffer, matrices, face.a(), face.normal(), face.ua(), rgb, alpha, illumination);
                vertex(buffer, matrices, face.b(), face.normal(), face.ub(), rgb, alpha, illumination);
                vertex(buffer, matrices, face.c(), face.normal(), face.uc(), rgb, alpha, illumination);
                vertex(buffer, matrices, face.d(), face.normal(), face.ud(), rgb, alpha, illumination);
            }
        }
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d point, Vec3d normal, ComponentAtlas.Uv uv, int color, int alpha, int light) {
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) point.x, (float) point.y, (float) point.z)
                        .color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha)
                        .texture(uv.u(), uv.v())
                        .overlay(OverlayTexture.DEFAULT_UV).light(light), matrices.peek().getNormalMatrix(),
                (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private static List<Face> gun() {
        Builder b = new Builder(ComponentAtlas.load("gun", "red"));
        // Receiver, stepped cheek rest and shoulder stock.
        b.skin = b.atlas.trim("metal").top(b.atlas.region("receiver_top")).bottom(b.atlas.region("receiver_bottom"));
        b.box(Material.FRAME, -3.8, -0.8, 0, 3.8, 8.8, 15, 0.65);
        b.skin = b.atlas.trim("metal").sides(b.atlas.region("stock_left"), b.atlas.region("stock_right"))
                .back(b.atlas.region("stock_back"));
        b.box(Material.FRAME, -4.5, -1.5, 14.3, 4.5, 10, 21.8, 0.9);
        b.skin = b.atlas.trim("rubber").back(b.atlas.region("stock_back"));
        b.box(Material.RUBBER, -4.7, -1.7, 21.2, 4.7, 9.5, 23.4, 0.7);
        b.skin = null;
        b.box(Material.STEEL, -4.7, 8.7, 14.5, 4.7, 10.2, 22.4, 0.4);
        b.box(Material.STEEL, -4.7, -1.8, 14.5, 4.7, -0.3, 22.4, 0.4);
        b.box(Material.FRAME, -3, 10, 15, 3, 10.8, 20.2, 0.22);
        b.box(Material.COLOR, -1.7, 10.81, 17.3, 1.7, 10.95, 18, 0.04);
        for (int sign : new int[]{-1, 1}) {
            double x = sign * 4.4;
            b.box(Material.STEEL, x - 0.65, -0.9, 16, x + 0.65, 9.4, 18.1, 0.28);
            b.box(Material.COLOR, x + sign * 0.68 - 0.09, 1, 16.65, x + sign * 0.68 + 0.09, 7.8, 17.3, 0.04);
        }
        // Pistol grip and its red energy spine are angled together, not intersecting boxes.
        b.transform = point -> point.subtract(0, 1, 8).rotateX(-0.30F).add(0, 1, 8);
        b.skin = b.atlas.trim("rubber").sides(b.atlas.region("grip_left"), b.atlas.region("grip_right"))
                .back(b.atlas.region("grip_end"));
        b.box(Material.RUBBER, -2, -9, 7, 2, 1.1, 12, 0.55);
        b.skin = null;
        b.box(Material.STEEL, -2.45, -9.4, 6.5, 2.45, -7.8, 12.5, 0.35);
        b.box(Material.FRAME, -2.1, -7.8, 11.9, 2.1, 0.5, 12.8, 0.18);
        b.box(Material.COLOR, -0.9, -7, 12.81, 0.9, -0.5, 13, 0.04);
        for (int i = 0; i < 5; i++) b.box(Material.FRAME, -2.05, -6.8 + i * 1.4, 6.8, 2.05, -6.35 + i * 1.4, 7.15, 0.09);
        b.transform = UnaryOperator.identity();
        b.box(Material.STEEL, -1.5, -3.7, 3.2, 1.5, -3.1, 8.4, 0.16);
        b.box(Material.STEEL, -1.5, -3.5, 3.0, 1.5, 0.2, 3.7, 0.15);
        b.box(Material.RUBBER, -0.5, -2.1, 5.8, 0.5, 0.1, 6.5, 0.12);
        // Layered side armor with ventilation recesses.
        for (int sign : new int[]{-1, 1}) {
            for (double z : new double[]{0, 6.8, 12.7}) {
                b.box(Material.STEEL, sign * 4.15 - 0.55, -0.9, z, sign * 4.15 + 0.55, 8.8, z + 1.7, 0.3);
            }
            b.skin = b.atlas.trim("metal").sides(b.atlas.region("vent"), b.atlas.region("vent"));
            b.box(Material.FRAME, sign * 4.15 - 0.5, 0.2, 2, sign * 4.15 + 0.5, 7.6, 6.4, 0.4);
            b.skin = null;
            b.box(Material.COLOR, sign * 4.72 - 0.05, 5.9, 8.4, sign * 4.72 + 0.05, 6.5, 11.9, 0.03);
        }
        // Open accelerator cage: the internal conduit remains visible from both sides.
        for (double x : new double[]{-3.5, 3.5}) {
            b.box(Material.FRAME, x - 0.7, -0.5, -22, x + 0.7, 1, 0.3, 0.3);
            b.box(Material.FRAME, x - 0.7, 8, -22, x + 0.7, 9.5, 0.3, 0.3);
            b.box(Material.STEEL, x - 0.45, 9.2, -20, x + 0.45, 10, -1, 0.2);
            b.box(Material.COLOR, x - 0.36, 8.8, -19, x + 0.36, 9.06, -2, 0.04);
        }
        for (double z : new double[]{-1.5, -20.8}) {
            b.ring(Material.FRAME, 0, 4.5, z - 1.3, z + 1.3, 5.2, 4.05, 8);
            b.ring(Material.STEEL, 0, 4.5, z - 1.45, z - 0.8, 5.0, 4.3, 8);
            b.ring(Material.COLOR, 0, 4.5, z - 0.76, z - 0.58, 4.1, 3.88, 16);
            for (int sign : new int[]{-1, 1}) {
                b.box(Material.STEEL, sign * 4.4 - 0.6, 0.8, z - 1.6, sign * 4.4 + 0.6, 8.2, z + 0.1, 0.25);
                b.box(Material.COLOR, sign * 4.4 - 0.22, 2.1, z - 1.72, sign * 4.4 + 0.22, 6.9, z - 1.62, 0.02);
            }
        }
        b.skin = b.atlas.trim("steel").front(b.atlas.region("muzzle_ring"));
        b.ring(Material.STEEL, 0, 4.5, -1.9, -1.4, 3.8, 2.6, 16);
        b.skin = null;
        b.ring(Material.COLOR, 0, 4.5, -2.1, -1.91, 2.6, 0.68, 16);
        for (int side : new int[]{-1, 1}) {
            b.box(Material.COLOR, side * 0.8 - 0.12, 4.1, -22, side * 0.8 + 0.12, 4.9, -1.2, 0.04);
            b.box(Material.COLOR, -0.4, 4.5 + side * 0.8 - 0.12, -22, 0.4, 4.5 + side * 0.8 + 0.12, -1.2, 0.04);
        }
        b.box(Material.CORE, -0.42, 4.08, -22.1, 0.42, 4.92, -1.25, 0.10);
        // Four armored muzzle claws and white-hot inserts.
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
            b.box(Material.FRAME, x * 3.1 - 1.1, 4.5 + y * 3.1 - 1.1, -25.4, x * 3.1 + 1.1, 4.5 + y * 3.1 + 1.1, -21.5, 0.5);
            b.box(Material.STEEL, x * 3.1 - 1.05, 4.5 + y * 3.1 - 1.05, -26, x * 3.1 + 1.05, 4.5 + y * 3.1 + 1.05, -24.8, 0.4);
            b.box(Material.COLOR, x * 3.1 - 0.52, 4.5 + y * 3.1 - 0.52, -26.02, x * 3.1 + 0.52, 4.5 + y * 3.1 + 0.52, -25.96, 0.03);
            b.box(Material.CORE, x * 3.1 - 0.21, 4.5 + y * 3.1 - 0.21, -26.04, x * 3.1 + 0.21, 4.5 + y * 3.1 + 0.21, -26.025, 0.004);
        }
        // Continuous upper rail and recessed reflex sight.
        b.skin = b.atlas.trim("metal").sides(b.atlas.region("rail"), b.atlas.region("rail"));
        b.box(Material.FRAME, -1.7, 9, -18, 1.7, 11.1, 13.5, 0.3);
        b.skin = null;
        for (int i = 0; i < 15; i++) b.box(Material.STEEL, -1.6, 11.1, -14 + i * 1.7, 1.6, 11.65, -13.3 + i * 1.7, 0.12);
        for (int sign : new int[]{-1, 1}) for (double z : new double[]{-13, -4, 5})
            b.box(Material.COLOR, sign * 1.73 - 0.04, 9.7, z, sign * 1.73 + 0.04, 10.3, z + 5, 0.02);
        b.box(Material.FRAME, -1.4, 11.5, 8.4, 1.4, 14.0, 11.4, 0.25);
        b.box(Material.STEEL, -2.5, 13.4, 7.5, 2.5, 14.2, 12.4, 0.2);
        b.box(Material.STEEL, -2.5, 17.2, 7.5, 2.5, 18, 12.4, 0.2);
        for (int sign : new int[]{-1, 1}) b.box(Material.STEEL, sign * 2.1 - 0.4, 14.1, 7.5, sign * 2.1 + 0.4, 17.3, 12.4, 0.18);
        b.box(Material.GLASS, -1.66, 14.22, 10.0, 1.66, 17.18, 10.02, 0.003);
        b.box(Material.COLOR, -0.035, 15.66, 12.42, 0.035, 15.74, 12.46, 0.004);
        return List.copyOf(b.faces);
    }

    private static List<Face> stand(LaserColor color) {
        Builder b = new Builder(ComponentAtlas.load("turret", color.asString()));
        b.box(Material.RUBBER, -7.8, 0, -7.8, 7.8, 0.65, 7.8, 0.5);
        b.skin = new ComponentAtlas.Skin(b.atlas.region("base_front"), b.atlas.region("base_back"),
                b.atlas.region("base_left"), b.atlas.region("base_right"), b.atlas.region("base_top"), b.atlas.region("base_bottom"), false);
        b.box(Material.FRAME, -7.6, 0.6, -7.6, 7.6, 3.1, 7.6, 0.55);
        b.skin = null;
        b.box(Material.FRAME, -6.6, 3.0, -6.6, 6.6, 4.15, 6.6, 0.6);
        b.box(Material.STEEL, -5.8, 4, -5.8, 5.8, 4.7, 5.8, 0.65);
        b.box(Material.FRAME, -5.4, 4.65, -5.4, 5.4, 5.3, 5.4, 0.75);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            b.box(Material.STEEL, x * 6.5 - 1.3, 0.6, z * 6.5 - 1.3, x * 6.5 + 1.3, 3.2, z * 6.5 + 1.3, 0.45);
            b.box(Material.RUBBER, x * 6.5 - 0.8, 0.7, z * 6.5 - 0.8, x * 6.5 + 0.8, 2.75, z * 6.5 + 0.8, 0.25);
            b.box(Material.STEEL, x * 5.5 - 0.65, 3.25, z * 5.5 - 0.65, x * 5.5 + 0.65, 4.1, z * 5.5 + 0.65, 0.22);
        }
        for (int turn = 0; turn < 4; turn++) {
            final float angle = turn * (float) Math.PI / 2;
            b.transform = point -> point.rotateY(angle);
            b.box(Material.COLOR, -4.5, 1.65, -7.64, 4.5, 2.25, -7.58, 0.01);
        }
        return List.copyOf(b.faces);
    }

    private static List<Face> cradle(LaserColor color) {
        Builder b = new Builder(ComponentAtlas.load("turret", color.asString()));
        b.skin = b.atlas.trim("metal").sides(b.atlas.region("ring_side"), b.atlas.region("ring_side"))
                .front(b.atlas.region("ring_side")).back(b.atlas.region("ring_side"));
        b.box(Material.FRAME, -4.9, 5.2, -4.9, 4.9, 7.3, 4.9, 1.7);
        b.skin = null;
        b.box(Material.STEEL, -4.7, 7.25, -4.7, 4.7, 7.65, 4.7, 1.5);
        for (int sign : new int[]{-1, 1}) {
            b.transform = point -> point.subtract(0, 11.9, 0).rotateX(-0.32F).add(0, 11.9, 0);
            b.skin = b.atlas.trim("metal").sides(b.atlas.region("arm_front"), b.atlas.region("arm_side"))
                    .back(b.atlas.region("arm_side"));
            b.box(Material.FRAME, sign * 3.65 - 0.95, 7.35, -2.65, sign * 3.65 + 0.95, 16.4, 2.65, 0.7);
            b.skin = null;
            b.box(Material.STEEL, sign * 3.65 - 1.0, 13.9, -2.8, sign * 3.65 + 1, 16.45, -1.65, 0.4);
            b.box(Material.COLOR, sign * 3.65 - 0.27, 8.7, -2.68, sign * 3.65 + 0.27, 13.5, -2.62, 0.01);
            b.transform = UnaryOperator.identity();
        }
        b.skin = b.atlas.trim("metal").top(b.atlas.region("bracket"));
        b.box(Material.FRAME, -4.7, 16.2, -4.7, 4.7, 17.55, 4.7, 0.45);
        b.skin = null;
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            b.box(Material.FRAME, x * 4.1 - 0.7, 17.3, z * 3.6 - 0.7, x * 4.1 + 0.7, 19.65, z * 3.6 + 0.7, 0.25);
            b.box(Material.STEEL, x * 4.1 - 0.74, 19.6, z * 3.6 - 0.74, x * 4.1 + 0.74, 20.1, z * 3.6 + 0.74, 0.2);
            b.box(Material.COLOR, x * 4.82 - 0.02, 17.8, z * 3.6 - 0.3, x * 4.82 + 0.02, 19.1, z * 3.6 + 0.3, 0.004);
        }
        for (int turn = 0; turn < 4; turn++) {
            final float angle = turn * (float) Math.PI / 2;
            b.transform = point -> point.rotateY(angle);
            b.box(Material.COLOR, -2.9, 6.05, -4.94, 2.9, 6.55, -4.88, 0.01);
            b.box(Material.COLOR, -2.8, 16.65, -4.73, 2.8, 17.15, -4.67, 0.01);
        }
        return List.copyOf(b.faces);
    }

    private static final class Builder {
        private final List<Face> faces = new ArrayList<>();
        private final ComponentAtlas atlas;
        private UnaryOperator<Vec3d> transform = UnaryOperator.identity();
        private ComponentAtlas.Skin skin;
        private Vec3d min = new Vec3d(-5, 0, -5), max = new Vec3d(5, 10, 5);

        private Builder(ComponentAtlas atlas) { this.atlas = atlas; }

        private ComponentAtlas.Skin materialSkin(Material material) {
            if (material == Material.COLOR || material == Material.CORE) return atlas.strip("light");
            return atlas.trim(switch (material) {
                case STEEL -> "steel";
                case RUBBER -> "rubber";
                case COLOR, CORE -> "light";
                default -> "metal";
            });
        }

        private void box(Material material, double x0, double y0, double z0, double x1, double y1, double z1, double bevel) {
            min = new Vec3d(x0, y0, z0); max = new Vec3d(x1, y1, z1);
            double cut = Math.min(bevel, Math.min(x1 - x0, Math.min(y1 - y0, z1 - z0)) * 0.4);
            Vec3d[] a = octagon(x0 + cut, z0 + cut, x1 - cut, z1 - cut, y0, cut * 0.5);
            Vec3d[] b = octagon(x0, z0, x1, z1, y0 + cut, cut);
            Vec3d[] c = octagon(x0, z0, x1, z1, y1 - cut, cut);
            Vec3d[] d = octagon(x0 + cut, z0 + cut, x1 - cut, z1 - cut, y1, cut * 0.5);
            join(material, a, b); join(material, b, c); join(material, c, d);
            cap(material, a, false); cap(material, d, true);
        }

        private Vec3d[] octagon(double x0, double z0, double x1, double z1, double y, double cut) {
            return new Vec3d[]{new Vec3d(x0 + cut, y, z0), new Vec3d(x1 - cut, y, z0), new Vec3d(x1, y, z0 + cut), new Vec3d(x1, y, z1 - cut),
                    new Vec3d(x1 - cut, y, z1), new Vec3d(x0 + cut, y, z1), new Vec3d(x0, y, z1 - cut), new Vec3d(x0, y, z0 + cut)};
        }
        private void join(Material material, Vec3d[] a, Vec3d[] b) {
            for (int i = 0; i < a.length; i++) { int next = (i + 1) % a.length; face(material, a[i], b[i], b[next], a[next]); }
        }
        private void cap(Material material, Vec3d[] ring, boolean top) {
            Vec3d center = ring[0].add(ring[4]).multiply(0.5);
            for (int i = 0; i < 8; i++) { int next = (i + 1) % 8; face(material, center, ring[top ? next : i], ring[top ? i : next], center); }
        }
        private void ring(Material material, double x, double y, double front, double back, double outer, double inner, int sides) {
            min = new Vec3d(x - outer, y - outer, front); max = new Vec3d(x + outer, y + outer, back);
            for (int i = 0; i < sides; i++) {
                double a = (i + 0.5) * Math.PI * 2 / sides, b = (i + 1.5) * Math.PI * 2 / sides;
                Vec3d ofa = radial(x, y, front, outer, a), ofb = radial(x, y, front, outer, b);
                Vec3d oba = radial(x, y, back, outer, a), obb = radial(x, y, back, outer, b);
                Vec3d ifa = radial(x, y, front, inner, a), ifb = radial(x, y, front, inner, b);
                Vec3d iba = radial(x, y, back, inner, a), ibb = radial(x, y, back, inner, b);
                face(material, ofb, ofa, ifa, ifb); face(material, oba, obb, ibb, iba);
                var wrap = materialSkin(material).front();
                add(material, ofa, ofb, obb, oba, wrap.uv((double) i / sides, 0), wrap.uv((double) (i + 1) / sides, 0),
                        wrap.uv((double) (i + 1) / sides, 1), wrap.uv((double) i / sides, 1));
                add(material, ifb, ifa, iba, ibb, wrap.uv((double) (i + 1) / sides, 0), wrap.uv((double) i / sides, 0),
                        wrap.uv((double) i / sides, 1), wrap.uv((double) (i + 1) / sides, 1));
            }
        }
        private Vec3d radial(double x, double y, double z, double radius, double angle) { return new Vec3d(x + radius * Math.cos(angle), y + radius * Math.sin(angle), z); }
        private void face(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
            Vec3d localNormal = b.subtract(a).crossProduct(c.subtract(a));
            boolean bevel = (Math.abs(localNormal.x) > 1e-9 ? 1 : 0) + (Math.abs(localNormal.y) > 1e-9 ? 1 : 0)
                    + (Math.abs(localNormal.z) > 1e-9 ? 1 : 0) > 1;
            var selected = skin != null && !bevel ? skin : materialSkin(material);
            add(material, a, b, c, d, selected.project(a, localNormal, min, max), selected.project(b, localNormal, min, max),
                    selected.project(c, localNormal, min, max), selected.project(d, localNormal, min, max));
        }
        private void add(Material material, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                         ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud) {
            a = transform.apply(a); b = transform.apply(b); c = transform.apply(c); d = transform.apply(d);
            Vec3d normal = b.subtract(a).crossProduct(c.subtract(a));
            // Vec3d.normalize() rounds the small muzzle-insert faces down to zero.
            normal = normal.multiply(1.0 / normal.length());
            faces.add(new Face(material, a, b, c, d, normal, ua, ub, uc, ud));
        }
    }

    private LaserGunModel() { }
}
