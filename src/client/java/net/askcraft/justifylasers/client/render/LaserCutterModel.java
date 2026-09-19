package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

final class LaserCutterModel {
    static final OpticalComponentMesh FRAME = ChamberModel.chassis("laser_cutter", false, false), CASING = casing();
    private static final OpticalComponentMesh RAILS = rails(), CARRIAGE = carriage(), HEAD = head();

    private static OpticalComponentMesh casing() {
        var b = new OpticalComponentMesh.Builder("laser_cutter");
        b.add(ChamberModel.chassis("laser_cutter", false, true), net.minecraft.util.math.Direction.NORTH);
        b.bevel("armor", -3.4, -3.4, -7.42, 3.4, 3.4, -7.16, .07);
        b.panel("dark", false, -2.8, -2.8, 2.8, 2.8, -7.445);
        b.panel("light", true, -.4, -2.35, .4, 2.35, -7.47);
        b.panel("light", true, -2.35, -.4, 2.35, .4, -7.48);
        return b.build();
    }
    private static OpticalComponentMesh rails() {
        var b = new OpticalComponentMesh.Builder("laser_cutter");
        for (double x : new double[]{-8, 8}) {
            b.bevel("armor", x - .7, 5.1, -8.7, x + .7, 7.4, 8.7, .15);
            b.solid("dark", x - .25, 7.42, -8.2, x + .25, 7.8, 8.2);
            for (double z : new double[]{-8.4, 8.4}) b.bevel("joint", x - 1.1, 4.5, z - .55, x + 1.1, 8, z + .55, .12);
        }
        return b.build();
    }
    private static OpticalComponentMesh carriage() {
        var b = new OpticalComponentMesh.Builder("laser_cutter");
        b.bevel("armor", -8.7, 4.1, -1.1, 8.7, 6.5, 1.1, .2);
        b.panel("light", true, -7.4, 4.8, 7.4, 5.2, -1.13);
        return b.build();
    }
    private static OpticalComponentMesh head() {
        var b = new OpticalComponentMesh.Builder("small_solar_concentrator");
        b.bevel("armor", -1.8, 1.1, -1.9, 1.8, 4.05, 1.9, .25);
        b.bevel("metal", -.8, .1, -.8, .8, 1.06, .8, .16);
        var lens = new OpticalComponentMesh.Builder("small_solar_concentrator");
        lens.panel("lens", true, -.54, -.54, .54, .54, .07);
        b.add(lens.build(), net.minecraft.util.math.Direction.DOWN);
        return b.build();
    }
    static Vec3d headPosition(IndustrialMachineBlockEntity machine, float delta) {
        boolean active = machine.status() == IndustrialMachineBlockEntity.Status.WORKING;
        double phase = active ? (machine.getWorld().getTime() + delta) * .075 : 0;
        return new Vec3d(active ? Math.sin(phase) * .35 : 0, 0, active ? Math.sin(phase * .53) * .34 : .34);
    }
    static void render(IndustrialMachineBlockEntity machine, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        boolean active = machine.status() == IndustrialMachineBlockEntity.Status.WORKING;
        int rgb = machine.lightRgb();
        FRAME.render(matrices, consumers, light, rgb, active, true);
        RAILS.render(matrices, consumers, light, rgb, false, false);
        Vec3d head = headPosition(machine, delta);
        matrices.push(); matrices.translate(0, 0, head.z); CARRIAGE.render(matrices, consumers, light, rgb, active, true);
        matrices.translate(head.x, 0, 0); HEAD.render(matrices, consumers, light, rgb, active, true); matrices.pop();
        var workpiece = machine.getStack(0).isEmpty() ? machine.getStack(IndustrialMachineBlockEntity.OUTPUT) : machine.getStack(0);
        if (!workpiece.isEmpty()) {
            matrices.push(); matrices.translate(0, -.34, 0); matrices.scale(.55F, .55F, .55F);
            MinecraftClient.getInstance().getItemRenderer().renderItem(workpiece, ModelTransformationMode.FIXED, light, overlay, matrices, consumers, machine.getWorld(), 0);
            matrices.pop();
        }
        ChamberModel.glass(matrices, consumers, light);
    }
    private LaserCutterModel() { }
}
