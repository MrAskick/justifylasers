package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

final class LightBridgeModel {
    private static final String MATERIAL = "light_bridge";
    static final OpticalComponentMesh[] MODELS = {build(1), build(2), build(3)};
    private static final OpticalComponentMesh[] CORES = {core(1), core(2), core(3)};
    private static final OpticalComponentMesh FEED = feed();
    static final OpticalComponentMesh CORNER = corner(false);
    private static final OpticalComponentMesh CORNER_CORE = corner(true);
    private static final OpticalComponentMesh MIRRORED_CORNER = mirror(CORNER);
    private static final OpticalComponentMesh MIRRORED_CORNER_CORE = mirror(CORNER_CORE);

    private static OpticalComponentMesh mirror(OpticalComponentMesh mesh) {
        java.util.function.UnaryOperator<net.minecraft.util.math.Vec3d> mirror = p -> new net.minecraft.util.math.Vec3d(-p.x, p.y, p.z);
        return new OpticalComponentMesh(MATERIAL, mesh.faces().stream().map(f -> new OpticalComponentMesh.Face(
                mirror.apply(f.d()), mirror.apply(f.c()), mirror.apply(f.b()), mirror.apply(f.a()), mirror.apply(f.normal()),
                f.ud(), f.uc(), f.ub(), f.ua(), f.glowing(), f.alpha())).toList());
    }

    static void renderCorner(boolean mirrored, int rgb, boolean active, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        (mirrored ? MIRRORED_CORNER : CORNER).render(matrices, consumers, light, rgb, active, true);
        if (active) (mirrored ? MIRRORED_CORNER_CORE : CORNER_CORE).render(matrices, consumers, light, 0xFFFFFF, true, true);
    }

    private static OpticalComponentMesh corner(boolean core) {
        var b = new OpticalComponentMesh.Builder(MATERIAL);
        if (core) {
            b.panel("core", true, -5.22, -5.22, 6.6, -4.48, -3.02);
            b.panel("core", true, -5.22, -4.47, -4.48, 6.6, -3.02);
            return b.build();
        }
        b.bevel("metal", -7.96, -7.96, -2.82, 7.96, -1.73, 2.82, .22);
        b.bevel("metal", -7.96, -1.72, -2.82, -1.73, 7.96, 2.82, .22);
        b.bevel("armor", -7.7, -7.75, -3.13, 7.75, -6.95, 2.95, .13);
        b.bevel("armor", -7.75, -6.94, -3.13, -6.95, 7.75, 2.95, .13);
        b.bevel("armor", -1.69, -2.65, -3.1, 6.6, -1.6, -2.65, .1);
        b.bevel("armor", -2.65, -1.59, -3.1, -1.6, 6.6, -2.65, .1);
        b.panel("aperture1", true, -6.5, -6.5, 7, -3.2, -2.96);
        b.panel("aperture1", true, -6.5, -3.19, -3.2, 7, -2.96);
        for (double x : new double[]{-6.5, -.5, 5.5}) {
            b.bevel("armor", x, -6.8, 2.83, x + .8, -2.9, 3.08, .05);
            b.panel("strip", true, x, -7.6, x + .8, -7.18, -3.14);
        }
        for (double y : new double[]{-.5, 5.5}) {
            b.bevel("armor", -6.8, y, 2.83, -2.9, y + .8, 3.08, .05);
            b.panel("strip", true, -7.6, y, -7.18, y + .8, -3.14);
        }
        var port = new OpticalComponentMesh.Builder(MATERIAL);
        b.bevel("metal", -5.5, -5.5, 1.1, 1.85, 1.85, 2.86, .18);
        port.profile("port", true, new double[]{0, .58, .78, 1.16, 1.46, 1.72, 2.01},
                new double[]{-2.87, -2.87, -2.94, -3.02, -3.14, -3.00, -2.87}, 2.04, 255);
        b.add(port.build(), Direction.SOUTH);
        return b.build();
    }

    private static OpticalComponentMesh feed() {
        var b = new OpticalComponentMesh.Builder(MATERIAL);
        b.bevel("metal", -1.28, 3.05, -2.08, 1.28, 5.81, 2.08, .18);
        for (Direction side : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            var face = new OpticalComponentMesh.Builder(MATERIAL);
            double depth = side.getAxis() == Direction.Axis.X ? 1.3 : 2.1;
            face.panel("strip", true, -.7, 4.15, .7, 5.55, -depth);
            b.add(face.build(), side);
        }
        return b.build();
    }

    static void renderFeeds(int width, int rgb, boolean active, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        for (int i = 0; i < width; i++) {
            matrices.push(); matrices.translate(i - (width - 1) * .5, 0, 0);
            FEED.render(matrices, consumers, light, rgb, active, true);
            matrices.pop();
        }
    }

    private static OpticalComponentMesh build(int width) {
        var b = new OpticalComponentMesh.Builder(MATERIAL);
        double half = width * 8;
        b.bevel("metal", -half + .15, -2.85, -2.75, half - .15, 2.85, 2.85, .35);
        b.bevel("metal", -half + 1.6, 1.6, -3.12, half - 1.6, 2.9, -2.6, .12);
        b.bevel("metal", -half + 1.6, -2.9, -3.12, half - 1.6, -1.6, -2.6, .12);
        for (int i = 0; i < width; i++) {
            double x = -half + i * 16;
            b.bevel("armor", x + 2.2, 2.86, -2.55, x + 13.8, 3.05, -.55, .06);
            b.bevel("armor", x + 2.2, 2.86, .55, x + 13.8, 3.05, 2.55, .06);
            b.bevel("armor", x + 3, -3.05, -2.55, x + 13, -2.86, 2.55, .06);
        }
        for (int side : new int[]{-1, 1}) {
            double x = side * (half - .85);
            b.bevel("armor", x - .65, -3.05, -3.15, x + .65, 3.05, 3.05, .3);
            b.panel("strip", true, x - .22, -1.95, x + .22, 1.95, -3.17);
        }
        // Recessed aperture; independent top/bottom bevels keep the island aligned over all widths.
        b.panel("aperture" + width, true, -half + 1.65, -1.6, half - 1.65, 1.6, -2.9);
        for (Direction side : new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.UP, Direction.DOWN}) {
            var port = new OpticalComponentMesh.Builder(MATERIAL);
            double depth = side.getAxis() == Direction.Axis.X ? half - .14 : 2.86;
            port.profile("port", true, new double[]{0, .58, .78, 1.16, 1.46, 1.72, 2.01},
                    new double[]{-depth - .025, -depth - .025, -depth - .11, -depth - .20, -depth - .23, -depth - .14, -depth - .02}, 2.04, 255);
            b.add(port.build(), side);
        }
        return b.build();
    }

    static void render(int width, int rgb, boolean active, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        MODELS[width - 1].render(matrices, consumers, light, rgb, active, true);
        if (active) CORES[width - 1].render(matrices, consumers, light, 0xFFFFFF, true, true);
    }

    private static OpticalComponentMesh core(int width) {
        var b = new OpticalComponentMesh.Builder(MATERIAL);
        double x = width * 8 - 2.4;
        b.panel("core", true, -x, -.4, x, .4, -2.94);
        return b.build();
    }

    private LightBridgeModel() { }
}
