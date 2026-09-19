package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class GrownCrystalModel {
    record Face(List<Vec3d> points, Vec3d normal) { }
    static final List<Face> FACES = mesh();
    private static final ItemModelBounds BOUNDS = ItemModelBounds.of(FACES.stream().flatMap(face -> face.points.stream()).toList());

    static void render(String id, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int overlay) {
        var texture = JustifyLasers.id("textures/component/" + id + "/base.png");
        var buffer = consumers.getBuffer(LaserCrystalModel.EmissiveLayers.glass(texture));
        matrices.push();
        if (mode == ModelTransformationMode.GUI) BOUNDS.fitGui(matrices);
        else matrices.scale(1 / 16F, 1 / 16F, 1 / 16F);
        for (Face face : FACES) for (int i = 0; i < 4; i++) {
            var p = face.points.get(i);
            // Same facet island and cluster geometry as raw photonite; no unrelated parts of the atlas.
            float u = (i < 2 ? 4.5F : 27.5F) / 128F, v = (i == 0 || i == 3 ? 43.5F : 4.5F) / 128F;
            RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float)p.x, (float)p.y, (float)p.z)
                    .color(255, 255, 255, 166).texture(u, v).overlay(overlay).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                    matrices.peek().getNormalMatrix(), (float)face.normal.x, (float)face.normal.y, (float)face.normal.z));
        }
        matrices.pop();
    }

    private static List<Face> mesh() {
        var faces = new ArrayList<Face>();
        int[][] corners = {{0,2,3,1},{5,7,6,4},{4,6,2,0},{1,3,7,5},{2,6,7,3},{4,0,1,5}};
        for (double[] box : new double[][]{{3,4,5,9,10,11,22.5},{8,3,4,13,8,9,-22.5},{6,8,6,11,13,10,-22.5}}) {
            var points = new ArrayList<Vec3d>();
            double angle = Math.toRadians(box[6]);
            for (int i = 0; i < 8; i++) {
                double x = box[(i & 1) != 0 ? 3 : 0] - 8, y = box[(i & 2) != 0 ? 4 : 1] - 8;
                points.add(new Vec3d(8 + x * Math.cos(angle) - y * Math.sin(angle), 8 + x * Math.sin(angle) + y * Math.cos(angle), box[(i & 4) != 0 ? 5 : 2]));
            }
            for (int[] indices : corners) {
                var quad = java.util.Arrays.stream(indices).mapToObj(points::get).toList();
                faces.add(new Face(quad, quad.get(1).subtract(quad.get(0)).crossProduct(quad.get(2).subtract(quad.get(0))).normalize()));
            }
        }
        double floor = faces.stream().flatMap(face -> face.points.stream()).mapToDouble(p -> p.y).min().orElseThrow();
        return faces.stream().map(face -> new Face(face.points.stream().map(p -> p.add(0, -floor, 0)).toList(), face.normal)).toList();
    }
    private GrownCrystalModel() { }
}
