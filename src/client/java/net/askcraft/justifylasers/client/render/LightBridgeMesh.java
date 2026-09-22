package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.bridge.LightBridgeSpan;

/** Immutable animation descriptors. Position.y stores a ribbon offset, except on the side walls. */
final class LightBridgeMesh {
    static final int DENSITY = 0, FILAMENT = 1, CROSS = 2, FLAT = 3, WALL = 4;
    static final int BASE = 0, PALE = 1, EDGE = 2, WHITE = 3;

    @FunctionalInterface
    interface Writer {
        void vertex(double x, double y, double z, int kind, int palette, int alpha);
    }

    static void emit(int width, double length, Writer out) {
        if (width < 1 || width > LightBridgeSpan.MAX_WIDTH || !Double.isFinite(length) || length <= 0
                || length > LightBridgeSpan.MAX_LENGTH + 1) throw new IllegalArgumentException("Invalid bridge mesh size");
        for (double z = 0; z < length; z += .5) {
            double end = Math.min(length, z + .5);
            for (double x = 0; x < width; x += .25) {
                double right = Math.min(width, x + .25);
                quad(out, x, z, right, end, DENSITY, BASE, 255, 255, 255, 255);
            }
        }
        for (int thread = -1; thread <= width * 3 + 1; thread++) {
            for (double z = 0; z < length; z += .2) {
                double end = Math.min(length, z + .2);
                ribbon(out, thread, z, end, .016, FILAMENT, BASE, 46);
                ribbon(out, thread, z, end, .004, FILAMENT, PALE, 58);
            }
        }
        for (double cross = .45; cross < length - .3; cross += 1.13) {
            for (double x = 0; x < width; x += .16) {
                double end = Math.min(width, x + .16);
                for (int pass = 0; pass < 2; pass++) {
                    double half = pass == 0 ? .023 : .006;
                    int alpha = pass == 0 ? 32 : 48;
                    out.vertex(x, -half, cross, CROSS, BASE, alpha);
                    out.vertex(end, -half, cross, CROSS, BASE, alpha);
                    out.vertex(end, half, cross, CROSS, BASE, alpha);
                    out.vertex(x, half, cross, CROSS, BASE, alpha);
                }
            }
        }
        for (double edge : new double[]{.022, width - .022}) {
            for (double z = 0; z < length; z += 2) {
                double end = Math.min(length, z + 2);
                flatRibbon(out, width, edge, z, end, .075, BASE, 32);
                flatRibbon(out, width, edge, z, end, .039, BASE, 105);
                flatRibbon(out, width, edge, z, end, .021, EDGE, 195);
                flatRibbon(out, width, edge, z, end, .009, WHITE, 255);
            }
        }
        quad(out, 0, Math.max(0, length - .035), width, length, FLAT, EDGE, 160, 120, 120, 160);
        for (double edge : new double[]{0, width}) {
            out.vertex(edge, LightBridgeSpan.HEIGHT, 0, WALL, BASE, 140);
            out.vertex(edge, LightBridgeSpan.HEIGHT, length, WALL, BASE, 140);
            out.vertex(edge, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS, length, WALL, BASE, 80);
            out.vertex(edge, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS, 0, WALL, BASE, 80);
        }
    }

    private static void quad(Writer out, double x, double z, double right, double end, int kind, int palette,
                             int a, int b, int c, int d) {
        out.vertex(x, 0, z, kind, palette, a);
        out.vertex(x, 0, end, kind, palette, b);
        out.vertex(right, 0, end, kind, palette, c);
        out.vertex(right, 0, z, kind, palette, d);
    }

    private static void ribbon(Writer out, double thread, double start, double end, double half, int kind, int palette, int alpha) {
        out.vertex(thread, -half, start, kind, palette, alpha);
        out.vertex(thread, -half, end, kind, palette, alpha);
        out.vertex(thread, half, end, kind, palette, alpha);
        out.vertex(thread, half, start, kind, palette, alpha);
    }

    private static void flatRibbon(Writer out, int width, double edge, double start, double end, double half, int palette, int alpha) {
        quad(out, Math.max(0, edge - half), start, Math.min(width, edge + half), end, FLAT, palette, alpha, alpha, alpha, alpha);
    }

    private LightBridgeMesh() { }
}
