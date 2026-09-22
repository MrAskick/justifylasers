package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.item.GrowthSeedItem;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** A natural pointed cluster, without the mount used by working laser crystals. */
public final class GrowthSeedModel {
    record Face(List<Vec3d> points, Vec3d normal) { }
    private static final List<List<Face>> STAGES = java.util.stream.IntStream.range(0, 4).mapToObj(GrowthSeedModel::mesh).toList();
    private static final ItemModelBounds BOUNDS = ItemModelBounds.of(STAGES.get(0).stream().flatMap(face -> face.points.stream()).toList());

    public static void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                              VertexConsumerProvider consumers, int light, int overlay) {
        CrystalGrowth crystal = GrowthSeedItem.type(stack);
        int stage = crystal == null ? 0 : Math.max(0, crystal.stage(stack));
        var texture = JustifyLasers.id("textures/component/grown_" + (crystal == null ? "diamond" : crystal.id()) + "_crystal/base.png");
        var buffer = consumers.getBuffer(mode == ModelTransformationMode.GUI ? LaserCrystalModel.EmissiveLayers.get(texture) : RenderLayer.getEntitySolid(texture));
        matrices.push();
        if (mode == ModelTransformationMode.GUI) BOUNDS.fitGui(matrices);
        else matrices.scale(1 / 16F, 1 / 16F, 1 / 16F);
        for (Face face : STAGES.get(stage)) for (int i = 0; i < 4; i++) {
            Vec3d point = face.points.get(i);
            float u = (i < 2 ? 4.5F : 27.5F) / 128F;
            float v = (i == 0 || i == 3 ? 43.5F : 4.5F) / 128F;
            RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) point.x, (float) point.y, (float) point.z)
                    .color(255,255,255,255).texture(u,v).overlay(overlay).light(LightmapTextureManager.MAX_LIGHT_COORDINATE),
                    matrices.peek().getNormalMatrix(), (float)face.normal.x, (float)face.normal.y, (float)face.normal.z));
        }
        matrices.pop();
    }

    static List<Face> mesh(int stage) {
        var faces = new ArrayList<Face>();
        double[][] crystals = {{8,8,2.35,14,0,0}, {5,8,1.6,9,-2.5,-.4}, {10,8,1.6,8,2.4,.6},
                {7,5,1.35,7,-.6,-2}, {9.5,10,1.35,6,1,1.8}, {5.6,10,1,4,-1.2,1.4}, {10,5.5,1,4,1,-1}};
        for (int index = 0; index < crystals.length - stage; index++) {
            double[] c = crystals[index];
            double height = c[3] * (1 - stage * .13), shoulder = height - c[2] * 1.15;
            Vec3d tip = new Vec3d(c[0] + c[4], height, c[1] + c[5]);
            for (int side = 0; side < 8; side++) {
                double a = side * Math.PI / 4, b = (side + 1) * Math.PI / 4;
                Vec3d lowerA = new Vec3d(c[0] + Math.cos(a) * c[2] * .72, 0, c[1] + Math.sin(a) * c[2] * .72);
                Vec3d lowerB = new Vec3d(c[0] + Math.cos(b) * c[2] * .72, 0, c[1] + Math.sin(b) * c[2] * .72);
                Vec3d upperA = new Vec3d(c[0] + c[4] * shoulder / height + Math.cos(a) * c[2], shoulder, c[1] + c[5] * shoulder / height + Math.sin(a) * c[2]);
                Vec3d upperB = new Vec3d(c[0] + c[4] * shoulder / height + Math.cos(b) * c[2], shoulder, c[1] + c[5] * shoulder / height + Math.sin(b) * c[2]);
                face(faces, lowerA, upperA, upperB, lowerB);
                face(faces, upperA, tip, upperB, upperB);
                face(faces, lowerB, new Vec3d(c[0],0,c[1]), lowerA, lowerA);
            }
        }
        return List.copyOf(faces);
    }

    private static void face(List<Face> faces, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        faces.add(new Face(List.of(a,b,c,d), b.subtract(a).crossProduct(c.subtract(a)).normalize()));
    }
    private GrowthSeedModel() { }
}
