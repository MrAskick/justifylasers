package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

final class ChamberModel {
    private static final OpticalComponentMesh GROWER = chassis(true, false), ASSEMBLER = chassis(false, false);
    private static final OpticalComponentMesh GROWER_CASING = chassis(true, true), ASSEMBLER_CASING = chassis(false, true);
    private static final OpticalComponentMesh ARM = arm(), JAW = jaw();

    static void casing(MachineKind kind, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        (kind == MachineKind.CRYSTAL_GROWER ? GROWER_CASING : ASSEMBLER_CASING).render(matrices, consumers, light, 0xFFFFFF, false, false);
    }

    static void render(IndustrialMachineBlockEntity machine, float delta, MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int overlay) {
        boolean grower = machine.kind() == MachineKind.CRYSTAL_GROWER;
        boolean active = machine.status() == IndustrialMachineBlockEntity.Status.WORKING || machine.calibration() > 0;
        float progress = machine.completion(delta), time = machine.getWorld().getTime() + delta;
        int rgb = grower ? 0x78CBFF : 0xACECF9;
        (grower ? GROWER : ASSEMBLER).render(matrices, consumers, light, rgb, active, true);
        if (grower) {
            if (progress > 0 || !machine.getStack(IndustrialMachineBlockEntity.OUTPUT).isEmpty()) {
                float size = machine.getStack(IndustrialMachineBlockEntity.OUTPUT).isEmpty() ? .20F + .78F * progress : .98F;
                matrices.push();
                matrices.translate(0, -.49, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * .65F));
                matrices.scale(size, size, size);
                matrices.translate(-.5, -.20, -.5);
                LaserCrystalModel.renderBare(ModelTransformationMode.NONE, matrices, consumers, light, overlay);
                matrices.pop();
            }
            if (machine.water() > 0) water(matrices, consumers, light, machine.water() / (float)machine.tankCapacity());
            glass(matrices, consumers, light);
        } else {
            for (int side : new int[]{-1, 1}) {
                matrices.push();
                matrices.translate(side * .69, .36, .05);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-side * (active ? 38 + (float)Math.sin(time * .16 + side) * 16 : 25)));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(active ? (float)Math.sin(time * .11 + side) * 24 : 0));
                ARM.render(matrices, consumers, light, rgb, active, true);
                matrices.translate(0, -.35, 0);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-side * (active ? 30 + (float)Math.sin(time * .22 + side) * 22 : 30)));
                ARM.render(matrices, consumers, light, rgb, active, true);
                matrices.translate(0, -.36, 0);
                for (int claw : new int[]{-1, 1}) {
                    matrices.push(); matrices.translate(claw * (active ? .026 + .014 * (1 + Math.sin(time * .3 + side)) : .047), 0, 0);
                    JAW.render(matrices, consumers, light, rgb, false, false); matrices.pop();
                }
                matrices.pop();
            }
            var recipe = machine.recipe();
            var result = machine.getStack(IndustrialMachineBlockEntity.OUTPUT);
            if (result.isEmpty() && recipe != null && progress > .12F) result = recipe.output(machine);
            if (!result.isEmpty()) {
                matrices.push();
                matrices.translate(0, -.25, 0);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(active ? time * .5F : 0));
                float size = machine.calibration() > 0 || progress == 0 ? .76F : .25F + progress * .51F;
                matrices.scale(size, size, size);
                MinecraftClient.getInstance().getItemRenderer().renderItem(result, ModelTransformationMode.FIXED,
                        light, overlay, matrices, consumers, machine.getWorld(), 0);
                matrices.pop();
            }
        }
    }

    static OpticalComponentMesh chassis(boolean grower, boolean casing) {
        String atlas = (grower ? "crystal_chamber" : "assembly_chamber") + (casing ? "_casing" : "");
        var body = new OpticalComponentMesh.Builder(atlas);
        double extent = casing ? 8 : 16, foot = casing ? -5 : -10, roof = casing ? 5 : 12;
        body.bevel("dark", -extent + .1, -extent + .06, -extent + .1, extent - .1, foot - .06, extent - .1, .5);
        body.bevel("armor", -extent + .1, roof + .06, -extent + .1, extent - .1, extent - .06, extent - .1, .45);
        if (casing) body.solid("dark", -6.7, foot + .10, -6.7, 6.7, roof - .10, 6.7);
        else {
            body.solid("dark", -12, foot + .02, -12, 12, foot + .5, 12);
            var floor = new OpticalComponentMesh.Builder(atlas);
            floor.trimmedPanel("grill", false, -11.8, -11.8, 11.8, 11.8, -foot - .52);
            body.add(floor.build(), Direction.UP);
            body.bevel("dark", -5, foot + .54, -5, 5, foot + 1.8, 5, .4);
            body.bevel("armor", -4.2, foot + 1.82, -4.2, 4.2, foot + 2.4, 4.2, .25);
        }
        // Each corner is owned once. Face-local panels never recreate its supporting solid.
        for (int sx : new int[]{-1,1}) for (int sz : new int[]{-1,1}) {
            double cx = sx * (extent - 1.9), cz = sz * (extent - 1.9);
            body.bevel("armor", cx - 1.65, foot + .02, cz - 1.65, cx + 1.65, roof - .02, cz + 1.65, .25);
            for (double cy : new double[]{-extent, roof})
                body.bevel(grower && !casing ? "armor" : "dark", cx - 1.85, cy, cz - 1.85,
                        cx + 1.85, cy + (casing ? 3 : 4), cz + 1.85, .25);
            if (!casing) {
                double ix = sx * 9.5, iz = sz * 9.5;
                body.solid("dark", ix - .65, foot + .55, iz - .65, ix + .65, roof - .1, iz + .65);
                for (double cy : new double[]{-6.5,6.5})
                    body.bevel("armor", ix - 1.4, cy - 1.4, iz - 1.4, ix + 1.4, cy + 1.4, iz + 1.4, .18);
            }
        }
        if (grower && !casing) body.bevel("dark", -3.3, 8.7, -3.3, 3.3, roof - .04, 3.3, .2);
        for (Direction side : Direction.Type.HORIZONTAL) {
            var face = new OpticalComponentMesh.Builder(atlas);
            for (int sign : new int[]{-1,1}) {
                double cx = sign * (extent - 1.9);
                face.trimmedPanel("column", true, cx - .8, foot + .8, cx + .8, roof - .8, -extent + .23);
                for (double cy : new double[]{-extent, roof})
                    face.trimmedPanel("joint", true, cx - 1.05, cy + .65, cx + 1.05, cy + (casing ? 2.35 : 3.25), -extent + .03);
            }
            face.trimmedPanel("beam", true, -extent + 4, -extent + .9, extent - 4, foot - .65, -extent + .07);
            face.trimmedPanel("beam", true, -extent + 4, roof + .5, extent - 4, extent - .5, -extent + .07);
            if (casing) {
                face.bevel("armor", -4.6, -4.5, -extent + .72, 4.6, 4.5, -extent + 1.12, .10);
                face.trimmedPanel("core", true, -2.5, -2.5, 2.5, 2.5, -extent + .69);
            } else {
                for (int sign : new int[]{-1,1}) {
                    double cx = sign * 9.5;
                    face.trimmedPanel("light", true, cx - .22, foot + 1.3, cx + .22, roof - 1, -10.18);
                    for (double cy : new double[]{-6.5,6.5})
                        face.trimmedPanel("joint", true, cx - .6, cy - .6, cx + .6, cy + .6, -10.92);
                }
                if (grower) face.trimmedPanel("light", true, -2.1, 9.1, 2.1, 9.9, -3.33);
            }
            body.add(face.build(), side);
        }
        var top = new OpticalComponentMesh.Builder(atlas);
        top.trimmedPanel("core", true, -3.4, -3.4, 3.4, 3.4, -extent + .03);
        body.add(top.build(), Direction.UP);
        return body.build();
    }

    private static OpticalComponentMesh arm() {
        var arm = new OpticalComponentMesh.Builder("assembly_chamber");
        arm.bevel("dark", -1.3, -6, -1.3, 1.3, .8, 1.3, .3);
        arm.solid("armor", -1.35, -4.5, -.85, 1.35, -.7, .85);
        arm.trimmedPanel("light", true, -.37, -4, .37, -1.2, -1.32);
        arm.bevel("joint", -1.7, -1, -1.5, 1.7, 1.4, 1.5, .25);
        return arm.build();
    }

    private static OpticalComponentMesh jaw() {
        var jaw = new OpticalComponentMesh.Builder("assembly_chamber");
        jaw.bevel("armor", -.28, -1.8, -.65, .28, .1, .65, .08);
        return jaw.build();
    }

    private static void water(MatrixStack matrices, VertexConsumerProvider consumers, int light, float amount) {
        Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(GameVersion.id("minecraft", "block/water_still"));
        double low = -.586, high = low + 1.24 * amount, side = .737;
        var buffer = consumers.getBuffer(LaserCrystalModel.EmissiveLayers.lens(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        // Vanilla water already carries alpha 180/255 (~70%); do not attenuate it a second time.
        quad(matrices, buffer, sprite, light, 0x649CEE, 255, new Vec3d(-side,high,-side),new Vec3d(-side,high,side),new Vec3d(side,high,side),new Vec3d(side,high,-side));
        walls(matrices, buffer, sprite, light, 0x649CEE, 255, side, low, high);
    }

    private static void glass(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        Sprite sprite = MinecraftClient.getInstance().getBlockRenderManager().getModel(net.minecraft.block.Blocks.GLASS.getDefaultState()).getParticleSprite();
        var buffer = consumers.getBuffer(LaserCrystalModel.EmissiveLayers.lens(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
        walls(matrices, buffer, sprite, light, 0xD8EEFF, 42, .75, -.60, .735);
    }

    private static void walls(MatrixStack matrices, VertexConsumer buffer, Sprite sprite, int light, int rgb, int alpha, double s, double low, double high) {
        quad(matrices,buffer,sprite,light,rgb,alpha,new Vec3d(-s,low,-s),new Vec3d(-s,high,-s),new Vec3d(s,high,-s),new Vec3d(s,low,-s));
        quad(matrices,buffer,sprite,light,rgb,alpha,new Vec3d(s,low,s),new Vec3d(s,high,s),new Vec3d(-s,high,s),new Vec3d(-s,low,s));
        quad(matrices,buffer,sprite,light,rgb,alpha,new Vec3d(s,low,-s),new Vec3d(s,high,-s),new Vec3d(s,high,s),new Vec3d(s,low,s));
        quad(matrices,buffer,sprite,light,rgb,alpha,new Vec3d(-s,low,s),new Vec3d(-s,high,s),new Vec3d(-s,high,-s),new Vec3d(-s,low,-s));
    }

    private static void quad(MatrixStack matrices, VertexConsumer buffer, Sprite sprite, int light, int rgb, int alpha, Vec3d... vertices) {
        Vec3d normal = vertices[1].subtract(vertices[0]).crossProduct(vertices[2].subtract(vertices[0])).normalize();
        for (int i = 0; i < 4; i++) {
            Vec3d p = vertices[i];
            RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float)p.x, (float)p.y, (float)p.z)
                    .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha)
                    .texture(i < 2 ? sprite.getMinU() : sprite.getMaxU(), i == 0 || i == 3 ? sprite.getMaxV() : sprite.getMinV())
                    .overlay(OverlayTexture.DEFAULT_UV).light(light), matrices.peek().getNormalMatrix(), (float)normal.x, (float)normal.y, (float)normal.z));
        }
    }

    private ChamberModel() { }
}
