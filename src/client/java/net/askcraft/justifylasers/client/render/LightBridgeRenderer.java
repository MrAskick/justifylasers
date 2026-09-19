package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LightBridgeRenderer {
    private record Field(LightBridgeSpan span, double time) { }
    private static final List<Field> QUEUED = new ArrayList<>();

    static void queue(LaserRenderFrame frame) {
        QUEUED.clear();
        for (var span : LightBridgeNetwork.fields(frame.world())) {
            if (!span.active() || frame.frustum() != null && !frame.frustum().isVisible(span.bounds().expand(.12))) continue;
            if (!span.bounds().expand(192).contains(frame.camera().getPos())) continue;
            QUEUED.add(new Field(span, frame.world().getTime() % 360_000 + frame.tickDelta()));
            if (!IrisCompatibility.isShaderPackInUse()) continue;
            // Only the thin edge rails write opaque material/depth for shader lighting and SSR.
            // The transparent field is composed after the shader pack, using resolved world/hand depth.
            VertexConsumer buffer = frame.consumers().getBuffer(LaserRenderLayers.SHADER_EMISSION);
            for (double edge : new double[]{.022, span.width() - .022}) {
                for (double step = 0; step < span.length(); step += 8) {
                    double end = Math.min(span.length(), step + 8);
                    emission(buffer, frame.matrixStack(), span, edge, step, end, frame.camera().getPos(), span.rgb());
                }
            }
        }
    }

    static boolean queued() { return !QUEUED.isEmpty(); }
    static void clear() { QUEUED.clear(); }

    static void render(VertexConsumer buffer, Vec3d camera) {
        QUEUED.sort(Comparator.comparingDouble((Field f) -> f.span.bounds().getCenter().squaredDistanceTo(camera)).reversed());
        for (var field : QUEUED) {
            var span = field.span;
            double y = camera.subtract(span.point(0, 0, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS / 2)).dotProduct(span.normal()) >= 0
                    ? LightBridgeSpan.HEIGHT + .001 : LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS - .001;
            int color = span.rgb();
            for (double z = 0; z < span.length(); z += .5) {
                double end = Math.min(span.length(), z + .5);
                for (double x = 0; x < span.width(); x += .25) {
                    double right = Math.min(span.width(), x + .25);
                    quad(buffer, span, camera, x, z, right, end, y, color,
                            density(x, z, field.time), density(x, end, field.time),
                            density(right, end, field.time), density(right, z, field.time));
                }
            }
            for (int thread = -1; thread <= span.width() * 3 + 1; thread++) {
                for (double z = 0; z < span.length(); z += .2) {
                    double end = Math.min(span.length(), z + .2);
                    double a = filament(thread, z, field.time);
                    double b = filament(thread, end, field.time);
                    ribbon(buffer, span, camera, a, b, z, end, .016, y, color, 46);
                    ribbon(buffer, span, camera, a, b, z, end, .004, y, pale(color, .22), 58);
                }
            }
            for (double cross = .45; cross < span.length() - .3; cross += 1.13) {
                for (double x = 0; x < span.width(); x += .16) {
                    double X = Math.min(span.width(), x + .16);
                    double za = cross + Math.sin(x * 3.8 + cross * 2.1 + field.time * .009) * .26;
                    double zb = cross + Math.sin(X * 3.8 + cross * 2.1 + field.time * .009) * .26;
                    for (int pass=0;pass<2;pass++) {
                        double half=pass==0?.023:.006;
                        int alpha=pass==0?32:48;
                        vertex(buffer,span.point(x,Math.max(0,za-half),y).subtract(camera),color,alpha);
                        vertex(buffer,span.point(X,Math.max(0,zb-half),y).subtract(camera),color,alpha);
                        vertex(buffer,span.point(X,Math.min(span.length(),zb+half),y).subtract(camera),color,alpha);
                        vertex(buffer,span.point(x,Math.min(span.length(),za+half),y).subtract(camera),color,alpha);
                    }
                }
            }
            for (double edge : new double[]{.022, span.width() - .022}) {
                for (double z = 0; z < span.length(); z += 2) {
                    double end = Math.min(span.length(), z + 2);
                    ribbon(buffer, span, camera, edge, edge, z, end, .075, y, color, 32);
                    ribbon(buffer, span, camera, edge, edge, z, end, .039, y, color, 105);
                    ribbon(buffer, span, camera, edge, edge, z, end, .021, y, pale(color, .50), 195);
                    ribbon(buffer, span, camera, edge, edge, z, end, .009, y, 0xF4FFFF, 255);
                }
            }
            double tip = Math.max(0, span.length() - .035);
            quad(buffer, span, camera, 0, tip, span.width(), span.length(), y, pale(color, .5), 160, 120, 120, 160);
            for (double edge : new double[]{0, span.width()}) {
                var a = span.point(edge, 0, LightBridgeSpan.HEIGHT);
                var b = span.point(edge, span.length(), LightBridgeSpan.HEIGHT);
                vertex(buffer, a.subtract(camera), color, 140);
                vertex(buffer, b.subtract(camera), color, 140);
                vertex(buffer, b.subtract(span.normal().multiply(LightBridgeSpan.THICKNESS)).subtract(camera), color, 80);
                vertex(buffer, a.subtract(span.normal().multiply(LightBridgeSpan.THICKNESS)).subtract(camera), color, 80);
            }
        }
    }

    private static int density(double x, double z, double time) {
        double a = Math.sin(z * 1.85 + Math.sin(x * 5.1 + time * .006) * 1.7 - time * .027);
        double b = Math.sin(x * 8.3 - z * .78 + time * .016);
        return 70 + (int) (14 * a * b);
    }

    private static double filament(int thread, double distance, double time) {
        return thread / 3d + Math.sin(distance * .87 + thread * 1.9 - time * .019) * .23
                + Math.sin(distance * .34 - thread + time * .006) * .12;
    }

    private static void ribbon(VertexConsumer buffer, LightBridgeSpan span, Vec3d camera, double x0, double x1,
                               double z0, double z1, double half, double y, int rgb, int alpha) {
        if (Math.max(x0, x1) + half <= 0 || Math.min(x0, x1) - half >= span.width()) return;
        vertex(buffer, span.point(clamp(x0 - half, span.width()), z0, y).subtract(camera), rgb, alpha);
        vertex(buffer, span.point(clamp(x1 - half, span.width()), z1, y).subtract(camera), rgb, alpha);
        vertex(buffer, span.point(clamp(x1 + half, span.width()), z1, y).subtract(camera), rgb, alpha);
        vertex(buffer, span.point(clamp(x0 + half, span.width()), z0, y).subtract(camera), rgb, alpha);
    }

    private static double clamp(double x, int width) { return Math.max(0, Math.min(width, x)); }

    private static void quad(VertexConsumer buffer, LightBridgeSpan span, Vec3d camera, double x, double z, double X, double Z,
                             double y, int rgb, int a, int b, int c, int d) {
        vertex(buffer, span.point(x, z, y).subtract(camera), rgb, a);
        vertex(buffer, span.point(x, Z, y).subtract(camera), rgb, b);
        vertex(buffer, span.point(X, Z, y).subtract(camera), rgb, c);
        vertex(buffer, span.point(X, z, y).subtract(camera), rgb, d);
    }

    private static void vertex(VertexConsumer buffer, Vec3d p, int rgb, int alpha) {
        RenderVersion.endVertex(buffer.vertex((float) p.x, (float) p.y, (float) p.z)
                .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha));
    }

    private static int pale(int rgb, double mix) {
        return channel(rgb >> 16 & 255, mix) << 16 | channel(rgb >> 8 & 255, mix) << 8 | channel(rgb & 255, mix);
    }
    private static int channel(int value, double mix) { return value + (int) ((255 - value) * mix); }

    private static void emission(VertexConsumer buffer, MatrixStack matrices, LightBridgeSpan span, double edge,
                                 double start, double end, Vec3d camera, int rgb) {
        Vec3d[] p = new Vec3d[8];
        for (int i = 0; i < 8; i++) p[i] = span.point(edge + (i % 4 == 1 || i % 4 == 2 ? .009 : -.009),
                i % 4 >= 2 ? end : start, LightBridgeSpan.HEIGHT - (i >= 4 ? .001 : .015)).subtract(camera);
        int[][] sides = {{4,7,6,5},{0,1,2,3},{0,4,5,1},{1,5,6,2},{2,6,7,3},{3,7,4,0}};
        Vec3d center = p[0].add(p[6]).multiply(.5);
        for (int[] side : sides) {
            Vec3d normal = p[side[1]].subtract(p[side[0]]).crossProduct(p[side[2]].subtract(p[side[0]])).normalize();
            boolean reversed = normal.dotProduct(p[side[0]].add(p[side[2]]).multiply(.5).subtract(center)) < 0;
            if (reversed) normal = normal.negate();
            for (int i = 0; i < 4; i++) {
                Vec3d vertex = p[side[reversed ? 3 - i : i]];
                RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float)vertex.x,(float)vertex.y,(float)vertex.z)
                        .color(rgb>>16&255,rgb>>8&255,rgb&255,255).texture(i<2?0:1,i==0||i==3?0:1)
                        .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                        matrices.peek().getNormalMatrix(),(float)normal.x,(float)normal.y,(float)normal.z));
            }
        }
    }

    private LightBridgeRenderer() { }
}
