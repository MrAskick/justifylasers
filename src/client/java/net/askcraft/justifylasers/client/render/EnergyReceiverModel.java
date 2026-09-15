package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.OpticPortMode;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Six configurable sockets on a shared chassis; only the neutral light mask is tinted. */
public final class EnergyReceiverModel {
    enum Panel { INPUT, OUTPUT, DISABLED, TOP, SIDE, BOTTOM }
    record Face(Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal,
                ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud, boolean glowing) { }

    private static final ComponentAtlas ATLAS = ComponentAtlas.load("energy_receiver", "base");
    private static final Identifier BASE = ComponentAtlas.texture("energy_receiver", "base");
    private static final Identifier GLOW = ComponentAtlas.texture("energy_receiver", "glow");
    private static final Identifier INDICATOR = ComponentAtlas.texture("energy_receiver", "indicator");
    private static final List<Face> CHASSIS = chassis();
    private static final Map<Direction, Map<Panel, List<Face>>> PORTS = ports();

    public static void render(LaserOpticBlockEntity receiver, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        boolean active = receiver.getCachedState().get(LaserOpticBlock.LIT);
        draw(CHASSIS, matrices, consumers.getBuffer(RenderLayer.getEntitySolid(BASE)), 0xFFFFFF, light, false);
        for (Direction side : Direction.values()) {
            draw(PORTS.get(side).get(panel(side, receiver.facing(), receiver.portMode(side))),
                    matrices, consumers.getBuffer(RenderLayer.getEntitySolid(BASE)), 0xFFFFFF, light, false);
        }
        if (!active) return;
        boolean emission = receiver.emitsShaderLight();
        RenderLayer layer = RenderLayer.getEntityCutout(emission && IrisCompatibility.isShaderPackInUse() ? GLOW : INDICATOR);
        int illumination = emission ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
        VertexConsumer overlay = consumers.getBuffer(layer);
        draw(CHASSIS, matrices, overlay, receiver.rgb(), illumination, true);
        for (Direction side : Direction.values()) {
            if (receiver.portMode(side) == OpticPortMode.DISABLED) continue;
            draw(PORTS.get(side).get(panel(side, receiver.facing(), receiver.portMode(side))),
                    matrices, overlay, receiver.rgb(), illumination, true);
        }
    }

    static Panel panel(Direction side, Direction facing, OpticPortMode mode) {
        if (mode == OpticPortMode.INPUT) return Panel.INPUT;
        if (mode == OpticPortMode.DISABLED) return Panel.DISABLED;
        if (side == Direction.UP) return Panel.TOP;
        if (side == Direction.DOWN) return Panel.BOTTOM;
        if (facing.getAxis().isHorizontal() && side == facing.rotateYCounterclockwise()) return Panel.SIDE;
        return Panel.OUTPUT;
    }

    static List<Face> chassisMesh() { return CHASSIS; }
    static Map<Direction, Map<Panel, List<Face>>> portMeshes() { return PORTS; }

    private static void draw(List<Face> mesh, MatrixStack matrices, VertexConsumer buffer, int rgb, int light, boolean glow) {
        for (Face face : mesh) {
            if (glow && !face.glowing()) continue;
            Vec3d offset = glow ? face.normal().multiply(0.00035) : Vec3d.ZERO;
            vertex(buffer, matrices, face.a().add(offset), face.normal(), face.ua(), rgb, light);
            vertex(buffer, matrices, face.b().add(offset), face.normal(), face.ub(), rgb, light);
            vertex(buffer, matrices, face.c().add(offset), face.normal(), face.uc(), rgb, light);
            vertex(buffer, matrices, face.d().add(offset), face.normal(), face.ud(), rgb, light);
        }
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d point, Vec3d normal, ComponentAtlas.Uv uv, int rgb, int light) {
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) point.x, (float) point.y, (float) point.z)
                        .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).texture(uv.u(), uv.v())
                        .overlay(OverlayTexture.DEFAULT_UV).light(light), matrices.peek().getNormalMatrix(),
                (float) normal.x, (float) normal.y, (float) normal.z));
    }

    private static List<Face> chassis() {
        Builder body = new Builder();
        body.box("metal", false, -6.7, -6.7, -6.7, 6.7, 6.7, 6.7, false);
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            Vec3d min = new Vec3d(x * 6.7 - 1.3, y * 6.7 - 1.3, z * 6.7 - 1.3);
            Vec3d max = min.add(2.6, 2.6, 2.6);
            body.cap(min, max);
        }
        Builder face = new Builder();
        for (int sign : new int[]{-1, 1}) {
            face.box("edge", true, -4.9, sign * 6.15 - 0.52, -7.35, 4.9, sign * 6.15 + 0.52, -6.65, false);
            face.box("edge", true, sign * 6.15 - 0.52, -4.9, -7.35, sign * 6.15 + 0.52, 4.9, -6.65, true);
            face.box("frame", false, -3.75, sign * 4.4 - 0.31, -7.55, 3.75, sign * 4.4 + 0.31, -6.65, false);
            face.box("frame", false, sign * 4.4 - 0.31, -3.75, -7.55, sign * 4.4 + 0.31, 3.75, -6.65, true);
        }
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1})
            face.box("stud", true, x * 4.4 - 0.6, y * 4.4 - 0.6, -7.8, x * 4.4 + 0.6, y * 4.4 + 0.6, -6.65, false);
        for (Direction side : Direction.values()) body.faces.addAll(orient(face.faces, side));
        return List.copyOf(body.faces);
    }

    private static Map<Direction, Map<Panel, List<Face>>> ports() {
        Map<Panel, List<Face>> originals = new EnumMap<>(Panel.class);
        for (Panel panel : Panel.values()) {
            Builder b = new Builder();
            if (panel == Panel.INPUT) b.lens();
            else {
                String part = panel.name().toLowerCase(java.util.Locale.ROOT);
                b.box(part, panel != Panel.DISABLED, -3.3, -3.3, -7.3, 3.3, 3.3, -6.7, false);
                if (panel == Panel.TOP) {
                    // Raised bus bars sample subregions of the same face, not three repeated copies of it.
                    for (double x : new double[]{-1.65, 0, 1.65}) b.ridge(part, x - 0.37, -2.55, x + 0.37, 2.55, -7.65, 3.3);
                } else if (panel == Panel.SIDE) {
                    for (double y : new double[]{-1.65, 0, 1.65}) b.ridge(part, -2.55, y - 0.37, 2.55, y + 0.37, -7.65, 3.3);
                }
            }
            originals.put(panel, List.copyOf(b.faces));
        }
        Map<Direction, Map<Panel, List<Face>>> result = new EnumMap<>(Direction.class);
        for (Direction side : Direction.values()) {
            Map<Panel, List<Face>> panels = new EnumMap<>(Panel.class);
            originals.forEach((panel, mesh) -> panels.put(panel, orient(mesh, side)));
            result.put(side, Map.copyOf(panels));
        }
        return Map.copyOf(result);
    }

    private static List<Face> orient(List<Face> mesh, Direction side) {
        Vec3d normal = Vec3d.of(side.getVector());
        Vec3d right = side.getAxis() == Direction.Axis.Y ? new Vec3d(1, 0, 0) : normal.crossProduct(new Vec3d(0, 1, 0));
        Vec3d up = right.crossProduct(normal);
        java.util.function.UnaryOperator<Vec3d> turn = p -> right.multiply(p.x).add(up.multiply(p.y)).subtract(normal.multiply(p.z));
        return mesh.stream().map(f -> new Face(turn.apply(f.a()), turn.apply(f.b()), turn.apply(f.c()), turn.apply(f.d()), turn.apply(f.normal()),
                f.ua(), f.ub(), f.uc(), f.ud(), f.glowing())).toList();
    }

    private static final class Builder {
        private final List<Face> faces = new ArrayList<>();

        void box(String part, boolean glow, double x1, double y1, double z1, double x2, double y2, double z2, boolean vertical) {
            Vec3d min = new Vec3d(x1, y1, z1), max = new Vec3d(x2, y2, z2);
            boxFaces(min, max, (normal, corners) -> {
                boolean front = normal.z < 0;
                var region = ATLAS.region(front ? part : "metal");
                add(corners, normal, region, min, max, glow && front, vertical && front, !front);
            });
        }

        void cap(Vec3d min, Vec3d max) {
            boxFaces(min, max, (normal, corners) -> {
                var region = ATLAS.region(normal.y != 0 ? "corner_top" : normal.x != 0 ? "corner_side" : "corner_front");
                add(corners, normal, region, min, max, normal.y == 0, false, false);
            });
        }

        void ridge(String part, double x1, double y1, double x2, double y2, double z, double extent) {
            box("metal", false, x1, y1, z + 0.001, x2, y2, -7.3, false);
            Vec3d[] points = {new Vec3d(x1,y1,z), new Vec3d(x1,y2,z), new Vec3d(x2,y2,z), new Vec3d(x2,y1,z)};
            add(points, new Vec3d(0,0,-1), ATLAS.region(part), new Vec3d(-extent,-extent,z), new Vec3d(extent,extent,z), true, false, false);
        }

        void lens() {
            double[] radii = {0, 2.5, 2.7, 3.12, 3.35};
            double[] depths = {-7.26, -7.26, -7.78, -7.78, -7.22};
            for (int band = 0; band < radii.length - 1; band++) for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32, b = Math.PI * 2 * (i + 1) / 32;
                Vec3d[] p = {circle(radii[band], depths[band], a), circle(radii[band], depths[band], b),
                        circle(radii[band+1], depths[band+1], b), circle(radii[band+1], depths[band+1], a)};
                Vec3d normal = p[2].subtract(p[1]).crossProduct(p[3].subtract(p[1])).normalize();
                var region = ATLAS.region("input");
                var uvs = new ComponentAtlas.Uv[4];
                for (int j = 0; j < 4; j++) uvs[j] = region.uv((p[j].x + 3.35) / 6.7, (3.35 - p[j].y) / 6.7);
                face(p, normal, uvs, true);
            }
        }

        private static Vec3d circle(double radius, double z, double angle) { return new Vec3d(Math.cos(angle)*radius, Math.sin(angle)*radius, z); }

        private void add(Vec3d[] corners, Vec3d normal, ComponentAtlas.Region region, Vec3d min, Vec3d max,
                         boolean glow, boolean vertical, boolean trim) {
            var uvs = new ComponentAtlas.Uv[4];
            for (int i = 0; i < 4; i++) uvs[i] = region.project(corners[i], normal, min, max, trim, vertical);
            face(corners, normal, uvs, glow);
        }

        private void face(Vec3d[] p, Vec3d normal, ComponentAtlas.Uv[] uv, boolean glow) {
            faces.add(new Face(p[0].multiply(1/16d), p[1].multiply(1/16d), p[2].multiply(1/16d), p[3].multiply(1/16d),
                    normal, uv[0], uv[1], uv[2], uv[3], glow));
        }

        private static void boxFaces(Vec3d min, Vec3d max, java.util.function.BiConsumer<Vec3d, Vec3d[]> emit) {
            double x=min.x,y=min.y,z=min.z,X=max.x,Y=max.y,Z=max.z;
            emit.accept(new Vec3d(0,0,-1), new Vec3d[]{new Vec3d(x,y,z),new Vec3d(x,Y,z),new Vec3d(X,Y,z),new Vec3d(X,y,z)});
            emit.accept(new Vec3d(0,0,1), new Vec3d[]{new Vec3d(X,y,Z),new Vec3d(X,Y,Z),new Vec3d(x,Y,Z),new Vec3d(x,y,Z)});
            emit.accept(new Vec3d(-1,0,0), new Vec3d[]{new Vec3d(x,y,Z),new Vec3d(x,Y,Z),new Vec3d(x,Y,z),new Vec3d(x,y,z)});
            emit.accept(new Vec3d(1,0,0), new Vec3d[]{new Vec3d(X,y,z),new Vec3d(X,Y,z),new Vec3d(X,Y,Z),new Vec3d(X,y,Z)});
            emit.accept(new Vec3d(0,1,0), new Vec3d[]{new Vec3d(x,Y,z),new Vec3d(x,Y,Z),new Vec3d(X,Y,Z),new Vec3d(X,Y,z)});
            emit.accept(new Vec3d(0,-1,0), new Vec3d[]{new Vec3d(x,y,Z),new Vec3d(x,y,z),new Vec3d(X,y,z),new Vec3d(X,y,Z)});
        }
    }

    private EnergyReceiverModel() { }
}
