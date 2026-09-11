package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class TechGui {
    enum State {
        NORMAL, HOVERED, PRESSED, SELECTED, DISABLED;

        static State of(boolean active, boolean pressed, boolean hovered, boolean focused, boolean selected) {
            if (!active) return DISABLED;
            if (pressed) return PRESSED;
            if (hovered || focused) return HOVERED;
            return selected ? SELECTED : NORMAL;
        }

        int accent() {
            return switch (this) {
                case NORMAL -> 0xFF28DEEF;
                case HOVERED -> 0xFFB7FAFF;
                case PRESSED -> 0xFFEDFEFF;
                case SELECTED -> 0xFF68EFF8;
                case DISABLED -> 0xFF476B7D;
            };
        }
    }

    static void button(DrawContext context, int x, int y, int width, int height, State state) {
        boolean disabled = state == State.DISABLED;
        boolean bright = state == State.HOVERED || state == State.SELECTED;
        float cut = height >= 24 ? 4.5F : 3;
        int accent = state.accent();
        int top = disabled ? 0xB4172B38 : state == State.PRESSED ? 0xDB03283D : bright ? 0xD70D5E7C : 0xCD084862;
        int bottom = disabled ? 0xB410202B : state == State.PRESSED ? 0xDB0A425C : 0xCE032034;
        gradient(context, contour(x + 1, y + 1, width - 2, height - 2, cut), top, bottom, y + 1, y + height - 1);
        outline(context, contour(x + 0.8F, y + 0.8F, width - 1.6F, height - 1.6F, cut), 1.5F, 0xEA021523);
        outline(context, contour(x + 1.9F, y + 1.9F, width - 3.8F, height - 3.8F, cut - 0.7F),
                bright ? 1.6F : 1.15F, accent);
        outline(context, contour(x + 3.1F, y + 3.1F, width - 6.2F, height - 6.2F, Math.max(1, cut - 1.6F)),
                0.6F, disabled ? 0x70516B78 : 0xB008799C);
        line(context, x + cut + 2, y + 1.45F, x + width - cut - 2, y + 1.45F, 0.65F,
                disabled ? 0xFF496775 : state == State.PRESSED ? 0xFF127795 : 0xFF7DEFFF);
        line(context, x + cut + 2, y + height - 2.8F, x + width - cut - 2, y + height - 2.8F, 0.6F,
                disabled ? 0x80516B78 : 0xFF0B5879);
        if (height >= 24) {
            for (int side = 0; side < 2; side++) for (int end = 0; end < 2; end++) {
                float left = side == 0 ? x : x + width;
                float topEdge = end == 0 ? y : y + height;
                int dx = side == 0 ? 1 : -1, dy = end == 0 ? 1 : -1;
                line(context, left + dx * 4, topEdge + dy * 7.5F, left + dx * 7.5F, topEdge + dy * 4, 1.35F, accent);
            }
            line(context, x + 5, y + 9, x + 5, y + height - 9, 0.55F, disabled ? 0x20476B7D : 0x3810C6EF);
            line(context, x + width - 5, y + 9, x + width - 5, y + height - 9, 0.55F, disabled ? 0x20476B7D : 0x3810C6EF);
        }
        context.draw();
    }

    static void indicator(DrawContext context, int x, int y, boolean on, boolean active) {
        context.fill(x - 1, y - 1, x + 5, y + 3, 0xD5031726);
        context.fill(x, y, x + 4, y + 2, !active ? 0xFF3E5866 : on ? 0xFF34E6EF : 0xFF52616A);
        if (on && active) context.fill(x + 1, y, x + 3, y + 1, 0xFFCBFFFF);
    }

    static void slider(DrawContext context, int x, int y, int width, int height, double value, State state) {
        button(context, x, y, width, height, state);
        int left = x + 5, right = x + width - 5, bottom = y + height - 4;
        int thumb = left + (int) Math.round((right - left) * Math.max(0, Math.min(1, value)));
        context.fill(left, bottom, right, bottom + 1, 0xED01141F);
        context.fill(left, bottom, thumb, bottom + 1, state.accent());
        context.fill(thumb - 2, bottom - 1, thumb + 2, bottom + 2, state == State.DISABLED ? 0xFF547785 : 0xFF42C9E1);
        context.fill(thumb - 1, bottom - 1, thumb + 1, bottom + 1, state == State.DISABLED ? 0xFF67828C : 0xFFD3FBFF);
    }

    enum Icon {
        MODULES(g -> {
            g.path(2, 5.5F, 5.5F, 18.5F, 5.5F, 18.5F, 18.5F, 5.5F, 18.5F, 5.5F, 5.5F);
            for (int p = 8; p <= 16; p += 4) {
                g.line(p, 1, p, 4.5F, 1.5F); g.line(p, 19.5F, p, 23, 1.5F);
                g.line(1, p, 4.5F, p, 1.5F); g.line(19.5F, p, 23, p, 1.5F);
            }
            g.tone = Tone.HIGHLIGHT;
            g.line(5.5F, 5.5F, 11, 5.5F, 1.1F);
        }),
        BACK(g -> {
            g.line(3, 12, 21, 12, 2.5F);
            g.path(2.5F, 10, 5, 3, 12, 10, 19);
        }),
        LIGHT(g -> {
            g.circle(12, 9, 5.4F);
            g.polygon(7, 10, 17, 10, 14.5F, 17, 9.5F, 17);
            g.line(9.5F, 19, 14.5F, 19, 1.5F);
            g.line(10.5F, 21.5F, 13.5F, 21.5F, 1.5F);
            g.line(12, 0.5F, 12, 2, 1.4F);
            g.line(1, 9, 3.5F, 9, 1.4F); g.line(20.5F, 9, 23, 9, 1.4F);
            g.line(3.5F, 2.5F, 5.5F, 4.5F, 1.4F); g.line(18.5F, 4.5F, 20.5F, 2.5F, 1.4F);
            g.tone = Tone.HIGHLIGHT;
            g.arc(12, 9, 3.8F, 200, 245, 0.8F);
        }),
        GLOW(g -> {
            g.star(12, 10, 9, 3);
            g.star(3.5F, 19.5F, 3, 1);
            g.star(21, 19, 2.5F, 0.9F);
            g.tone = Tone.HIGHLIGHT;
            g.star(12, 10, 4, 1.2F);
        }),
        SHIELD(g -> {
            g.path(1.7F, 12, 1.5F, 16, 3.5F, 21, 4.5F, 20.5F, 11, 18, 16.5F,
                    12, 22, 6, 16.5F, 3.5F, 11, 3, 4.5F, 8, 3.5F, 12, 1.5F);
            g.arc(12, 10, 2.4F, 180, 360, 1.6F);
            g.rect(8.7F, 10, 15.3F, 16);
            g.tone = Tone.INK;
            g.circle(12, 12, 0.85F); g.rect(11.5F, 12, 12.5F, 14.5F);
        }),
        REDSTONE(g -> {
            g.rect(10, 1, 14, 5); g.rect(10, 19, 14, 23);
            g.line(12, 6.5F, 12, 17.5F, 1.8F);
            for (float radius : new float[]{7.5F, 10.5F}) {
                g.arc(12, 12, radius, 145, 215, 1.4F);
                g.arc(12, 12, radius, -35, 35, 1.4F);
            }
        }),
        SETTINGS(g -> {
            g.arc(12, 12, 6.8F, 0, 360, 3.2F);
            for (int tooth = 0; tooth < 8; tooth++) {
                double angle = tooth * Math.PI / 4;
                g.line(12 + (float) Math.cos(angle) * 7, 12 + (float) Math.sin(angle) * 7,
                        12 + (float) Math.cos(angle) * 10, 12 + (float) Math.sin(angle) * 10, 3.2F);
            }
            g.arc(12, 12, 2.5F, 0, 360, 1.2F);
        }),
        POWER(g -> {
            g.arc(12, 13, 8, -50, 230, 2);
            g.line(12, 1.5F, 12, 12, 2);
        }),
        RESET(g -> {
            g.arc(12, 12, 8, -140, 145, 2);
            g.polygon(2.5F, 3, 9, 7.5F, 2.5F, 10);
        });

        private final List<Quad> mesh;

        Icon(Consumer<Glyph> drawing) {
            Glyph glyph = new Glyph();
            drawing.accept(glyph);
            mesh = List.copyOf(glyph.quads);
        }

        List<Quad> mesh() { return mesh; }

        void draw(DrawContext context, float x, float y, float size, State state) {
            var matrices = context.getMatrices();
            matrices.push();
            matrices.translate(x, y, 0);
            matrices.scale(size / 24, size / 24, 1);
            for (Quad quad : mesh) {
                int color = switch (quad.tone) {
                    case ACCENT -> state.accent();
                    case HIGHLIGHT -> state == State.DISABLED ? 0xFF597987 : 0xFFC3FDFF;
                    case INK -> state == State.DISABLED ? 0xFF192D3A : 0xFF07415B;
                };
                drawQuad(context, quad.a, quad.b, quad.c, quad.d, color, color, color, color);
            }
            context.draw();
            matrices.pop();
        }
    }

    enum Tone { ACCENT, HIGHLIGHT, INK }
    record Point(float x, float y) { }
    record Quad(Point a, Point b, Point c, Point d, Tone tone) { }

    private static final class Glyph {
        private final List<Quad> quads = new ArrayList<>();
        private Tone tone = Tone.ACCENT;

        private void rect(float x1, float y1, float x2, float y2) {
            quads.add(new Quad(new Point(x1, y1), new Point(x1, y2), new Point(x2, y2), new Point(x2, y1), tone));
        }

        private void line(float x1, float y1, float x2, float y2, float thickness) {
            quads.add(stroke(x1, y1, x2, y2, thickness, tone));
        }

        private void path(float thickness, float... points) {
            for (int i = 0; i < points.length - 2; i += 2) line(points[i], points[i + 1], points[i + 2], points[i + 3], thickness);
        }

        private void polygon(float... points) {
            Point a = new Point(points[0], points[1]);
            for (int i = 2; i < points.length - 2; i += 2) {
                Point b = new Point(points[i], points[i + 1]), c = new Point(points[i + 2], points[i + 3]);
                quads.add(new Quad(a, c, b, b, tone));
            }
        }

        private void circle(float x, float y, float radius) {
            Point center = new Point(x, y);
            for (int i = 0; i < 32; i++) {
                Point a = radial(x, y, radius, i * 360.0F / 32), b = radial(x, y, radius, (i + 1) * 360.0F / 32);
                quads.add(new Quad(center, b, a, a, tone));
            }
        }

        private void arc(float x, float y, float radius, float start, float end, float thickness) {
            int segments = (int) Math.ceil((end - start) / 12);
            for (int i = 0; i < segments; i++) {
                Point a = radial(x, y, radius, start + (end - start) * i / segments);
                Point b = radial(x, y, radius, start + (end - start) * (i + 1) / segments);
                line(a.x, a.y, b.x, b.y, thickness);
            }
        }

        private void star(float x, float y, float radius, float inset) {
            Point center = new Point(x, y);
            for (int i = 0; i < 8; i++) {
                Point a = radial(x, y, (i & 1) == 0 ? radius : inset, i * 45);
                Point b = radial(x, y, (i & 1) == 0 ? inset : radius, (i + 1) * 45);
                quads.add(new Quad(center, b, a, a, tone));
            }
        }
    }

    static Point[] contour(float x, float y, float width, float height, float cut) {
        cut = Math.min(cut, Math.min(width, height) / 2);
        return new Point[]{new Point(x + cut, y), new Point(x + width - cut, y), new Point(x + width, y + cut),
                new Point(x + width, y + height - cut), new Point(x + width - cut, y + height),
                new Point(x + cut, y + height), new Point(x, y + height - cut), new Point(x, y + cut)};
    }

    private static Point radial(float x, float y, float radius, float angle) {
        double radians = Math.toRadians(angle);
        return new Point(x + (float) Math.cos(radians) * radius, y + (float) Math.sin(radians) * radius);
    }

    private static Quad stroke(float x1, float y1, float x2, float y2, float thickness, Tone tone) {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        float dx = (y1 - y2) * thickness / (2 * length), dy = (x2 - x1) * thickness / (2 * length);
        return new Quad(new Point(x1 + dx, y1 + dy), new Point(x2 + dx, y2 + dy),
                new Point(x2 - dx, y2 - dy), new Point(x1 - dx, y1 - dy), tone);
    }

    private static void line(DrawContext context, float x1, float y1, float x2, float y2, float width, int color) {
        Quad q = stroke(x1, y1, x2, y2, width, Tone.ACCENT);
        drawQuad(context, q.a, q.b, q.c, q.d, color, color, color, color);
    }

    private static void outline(DrawContext context, Point[] points, float width, int color) {
        for (int i = 0; i < points.length; i++) {
            Point a = points[i], b = points[(i + 1) % points.length];
            line(context, a.x, a.y, b.x, b.y, width, color);
        }
    }

    private static void gradient(DrawContext context, Point[] points, int top, int bottom, float y1, float y2) {
        Point a = points[0];
        for (int i = 1; i < points.length - 1; i++) {
            Point b = points[i], c = points[i + 1];
            // Match DrawContext.fill winding: the GUI projection inverts the Y axis.
            drawQuad(context, a, c, b, b, blend(top, bottom, (a.y - y1) / (y2 - y1)),
                    blend(top, bottom, (c.y - y1) / (y2 - y1)), blend(top, bottom, (b.y - y1) / (y2 - y1)),
                    blend(top, bottom, (b.y - y1) / (y2 - y1)));
        }
    }

    private static int blend(int a, int b, float t) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) result |= Math.round((a >>> shift & 255) * (1 - t) + (b >>> shift & 255) * t) << shift;
        return result;
    }

    private static void drawQuad(DrawContext context, Point a, Point b, Point c, Point d, int ca, int cb, int cc, int cd) {
        VertexConsumer buffer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        RenderVersion.endVertex(buffer.vertex(matrix, a.x, a.y, 0).color(ca));
        RenderVersion.endVertex(buffer.vertex(matrix, b.x, b.y, 0).color(cb));
        RenderVersion.endVertex(buffer.vertex(matrix, c.x, c.y, 0).color(cc));
        RenderVersion.endVertex(buffer.vertex(matrix, d.x, d.y, 0).color(cd));
    }

    private TechGui() { }
}
