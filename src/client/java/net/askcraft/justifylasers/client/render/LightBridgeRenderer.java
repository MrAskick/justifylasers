package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LightBridgeRenderer {
    private record Field(LightBridgeSpan span, double time) { }
    private record EmissionKey(int width, double length, boolean reflected) { }
    private static final List<Field> QUEUED = new ArrayList<>();

    static void queue(LaserRenderFrame frame) {
        QUEUED.clear();
        for (var span : LightBridgeNetwork.fields(frame.world())) {
            if (!span.active() || frame.frustum() != null && !frame.frustum().isVisible(span.bounds().expand(.12))) continue;
            if (!span.bounds().expand(192).contains(frame.camera().getPos())) continue;
            QUEUED.add(new Field(span, frame.world().getTime() % 360_000 + frame.tickDelta()));
            if (!IrisCompatibility.isShaderPackInUse()) continue;
            if (net.askcraft.justifylasers.client.ClientSettings.get().gpuEffects && emissionGpu(span, frame)) continue;
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

    static void renderGpu(Vec3d camera, org.joml.Matrix4f view, org.joml.Matrix4f projection) {
        QUEUED.sort(Comparator.comparingDouble((Field f) -> f.span.bounds().getCenter().squaredDistanceTo(camera)).reversed());
        for (var field : QUEUED) {
            if (LightBridgeGpuRenderer.draw(field.span, field.time, camera, view, projection)) continue;
            var buffer = RenderVersion.beginQuads(net.minecraft.client.render.VertexFormats.POSITION_COLOR);
            render(field, buffer, camera);
            net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }

    static void render(BufferBuilder buffer, Vec3d camera) {
        QUEUED.sort(Comparator.comparingDouble((Field f) -> f.span.bounds().getCenter().squaredDistanceTo(camera)).reversed());
        for (var field : QUEUED) render(field, buffer, camera);
    }

    private static void render(Field field, BufferBuilder buffer, Vec3d camera) {
            var span = field.span;
            var transform = LightBridgeTransform.of(span, camera);
            double y = camera.subtract(span.point(0, 0, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS / 2)).dotProduct(span.normal()) >= 0
                    ? LightBridgeSpan.HEIGHT + .001 : LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS - .001;
            int color = span.rgb();
            for (double z = 0; z < span.length(); z += .5) {
                double end = Math.min(span.length(), z + .5);
                for (double x = 0; x < span.width(); x += .25) {
                    double right = Math.min(span.width(), x + .25);
                    quad(buffer, transform, x, z, right, end, y, color,
                            density(x, z, field.time), density(x, end, field.time),
                            density(right, end, field.time), density(right, z, field.time));
                }
            }
            for (int thread = -1; thread <= span.width() * 3 + 1; thread++) {
                for (double z = 0; z < span.length(); z += .2) {
                    double end = Math.min(span.length(), z + .2);
                    double a = filament(thread, z, field.time);
                    double b = filament(thread, end, field.time);
                    ribbon(buffer, span.width(), transform, a, b, z, end, .016, y, color, 46);
                    ribbon(buffer, span.width(), transform, a, b, z, end, .004, y, pale(color, .22), 58);
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
                        transform.vertex(buffer,x,Math.max(0,za-half),y,color,alpha);
                        transform.vertex(buffer,X,Math.max(0,zb-half),y,color,alpha);
                        transform.vertex(buffer,X,Math.min(span.length(),zb+half),y,color,alpha);
                        transform.vertex(buffer,x,Math.min(span.length(),za+half),y,color,alpha);
                    }
                }
            }
            for (double edge : new double[]{.022, span.width() - .022}) {
                for (double z = 0; z < span.length(); z += 2) {
                    double end = Math.min(span.length(), z + 2);
                    ribbon(buffer, span.width(), transform, edge, edge, z, end, .075, y, color, 32);
                    ribbon(buffer, span.width(), transform, edge, edge, z, end, .039, y, color, 105);
                    ribbon(buffer, span.width(), transform, edge, edge, z, end, .021, y, pale(color, .50), 195);
                    ribbon(buffer, span.width(), transform, edge, edge, z, end, .009, y, 0xF4FFFF, 255);
                }
            }
            double tip = Math.max(0, span.length() - .035);
            quad(buffer, transform, 0, tip, span.width(), span.length(), y, pale(color, .5), 160, 120, 120, 160);
            for (double edge : new double[]{0, span.width()}) {
                transform.vertex(buffer, edge, 0, LightBridgeSpan.HEIGHT, color, 140);
                transform.vertex(buffer, edge, span.length(), LightBridgeSpan.HEIGHT, color, 140);
                transform.vertex(buffer, edge, span.length(), LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS, color, 80);
                transform.vertex(buffer, edge, 0, LightBridgeSpan.HEIGHT - LightBridgeSpan.THICKNESS, color, 80);
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

    private static void ribbon(BufferBuilder buffer, int width, LightBridgeTransform transform, double x0, double x1,
                               double z0, double z1, double half, double y, int rgb, int alpha) {
        if (Math.max(x0, x1) + half <= 0 || Math.min(x0, x1) - half >= width) return;
        transform.vertex(buffer, clamp(x0 - half, width), z0, y, rgb, alpha);
        transform.vertex(buffer, clamp(x1 - half, width), z1, y, rgb, alpha);
        transform.vertex(buffer, clamp(x1 + half, width), z1, y, rgb, alpha);
        transform.vertex(buffer, clamp(x0 + half, width), z0, y, rgb, alpha);
    }

    private static double clamp(double x, int width) { return Math.max(0, Math.min(width, x)); }

    private static void quad(BufferBuilder buffer, LightBridgeTransform transform, double x, double z, double X, double Z,
                             double y, int rgb, int a, int b, int c, int d) {
        transform.vertex(buffer, x, z, y, rgb, a);
        transform.vertex(buffer, x, Z, y, rgb, b);
        transform.vertex(buffer, X, Z, y, rgb, c);
        transform.vertex(buffer, X, z, y, rgb, d);
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
        emissionFaces(buffer, matrices, p, rgb, false);
    }

    private static boolean emissionGpu(LightBridgeSpan span, LaserRenderFrame frame) {
        var transform = LightBridgeTransform.of(span, frame.camera().getPos()).matrix();
        var key = new EmissionKey(span.width(), span.length(), transform.determinant() < 0);
        var matrices = frame.matrixStack();
        matrices.push();
        try {
            matrices.peek().getPositionMatrix().mul(transform);
            matrices.peek().getNormalMatrix().mul(new org.joml.Matrix3f(transform).invert().transpose());
            return GpuModelRenderer.draw(key, 48 * (int)Math.ceil(span.length()/8), buffer -> {
                var local = new MatrixStack();
                for (double edge : new double[]{.022, key.width - .022}) for (double start=0; start<key.length; start+=8) {
                    double end = Math.min(key.length, start+8);
                    Vec3d[] p = new Vec3d[8];
                    for(int i=0;i<8;i++) p[i] = new Vec3d(edge+(i%4==1||i%4==2?.009:-.009),
                            LightBridgeSpan.HEIGHT-(i>=4?.001:.015), i%4>=2?end:start);
                    emissionFaces(buffer, local, p, 0xFFFFFF, key.reflected);
                }
            }, matrices, LaserRenderLayers.SHADER_EMISSION, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV, span.rgb(), false);
        } finally { matrices.pop(); }
    }

    private static void emissionFaces(VertexConsumer buffer, MatrixStack matrices, Vec3d[] p, int rgb, boolean reflected) {
        int[][] sides = {{4,7,6,5},{0,1,2,3},{0,4,5,1},{1,5,6,2},{2,6,7,3},{3,7,4,0}};
        Vec3d center = p[0].add(p[6]).multiply(.5);
        for (int[] side : sides) {
            Vec3d normal = p[side[1]].subtract(p[side[0]]).crossProduct(p[side[2]].subtract(p[side[0]])).normalize();
            boolean reversed = normal.dotProduct(p[side[0]].add(p[side[2]]).multiply(.5).subtract(center)) < 0;
            if (reversed) normal = normal.negate();
            for (int i = 0; i < 4; i++) {
                Vec3d vertex = p[side[reversed != reflected ? 3 - i : i]];
                RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float)vertex.x,(float)vertex.y,(float)vertex.z)
                        .color(rgb>>16&255,rgb>>8&255,rgb&255,255).texture(i<2?0:1,i==0||i==3?0:1)
                        .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                        matrices.peek().getNormalMatrix(),(float)normal.x,(float)normal.y,(float)normal.z));
            }
        }
    }

    private LightBridgeRenderer() { }
}
