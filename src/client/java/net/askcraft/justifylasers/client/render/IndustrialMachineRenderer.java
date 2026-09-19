package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.IndustrialMachineBlock;
import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.laser.LaserBeamTrace;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class IndustrialMachineRenderer implements BlockEntityRenderer<IndustrialMachineBlockEntity> {
    private static final OpticalComponentMesh GROWER_PORTS = growerPorts();
    private static final OpticalComponentMesh PROJECTOR = projector();

    public IndustrialMachineRenderer(BlockEntityRendererFactory.Context context) { }

    @Override public void render(IndustrialMachineBlockEntity machine, float delta, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        // Formation is server-owned. A temporarily missing neighboring chunk must not collapse the client model.
        boolean assembled = machine.kind().multiblock() && machine.origin() != null;
        if (assembled && !machine.isController()) return;
        matrices.push();
        try {
            double center = assembled ? 1 : .5;
            matrices.translate(center, center, center);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - machine.getCachedState().get(IndustrialMachineBlock.FACING).asRotation()));
            if (assembled && machine.kind() == MachineKind.LASER_CUTTER) LaserCutterModel.render(machine, delta, matrices, consumers, light, overlay);
            else if (assembled) ChamberModel.render(machine, delta, matrices, consumers, light, overlay);
            else if (machine.kind() == MachineKind.LASER_CUTTER) LaserCutterModel.CASING.render(matrices, consumers, light, 0x91DFFF, false, false);
            else if (machine.kind().multiblock()) ChamberModel.casing(machine.kind(), matrices, consumers, light);
            else if (machine.kind() == MachineKind.CHEMICAL_SYNTHESIZER) ChemicalSynthesizerModel.render(machine, matrices, consumers, light);
            else FuelGeneratorModel.render(machine,matrices,consumers,light);
            if (assembled && machine.kind().opticalInput()) GROWER_PORTS.render(matrices, consumers, light, machine.lightRgb(), machine.lightFlux() > 0, true);
            if (assembled && machine.kind() == MachineKind.CRYSTAL_GROWER) {
                float time = machine.getWorld().getTime() + delta;
                for (int i = 0; i < 4; i++) {
                    Vec3d start = projectorPosition(i), axis = focus(time, i).subtract(start).normalize();
                    matrices.push();
                    matrices.translate(start.x, start.y, start.z);
                    matrices.multiply(new org.joml.Quaternionf().rotationTo(0, 0, -1, (float) axis.x, (float) axis.y, (float) axis.z));
                    PROJECTOR.render(matrices, consumers, light, machine.lightRgb(), machine.lightFlux() > 0, true);
                    matrices.pop();
                }
            }
        } finally { matrices.pop(); }
        if (assembled && !IrisCompatibility.isRenderingShadowPass()) effects(machine, delta, matrices, consumers);
    }

    private static void effects(IndustrialMachineBlockEntity machine, float delta, MatrixStack matrices, VertexConsumerProvider consumers) {
        Vec3d origin = Vec3d.of(machine.getPos()), center = origin.add(1, 1, 1);
        float time = machine.getWorld().getTime() + delta;
        if (machine.kind() == MachineKind.LASER_CUTTER && machine.status() == IndustrialMachineBlockEntity.Status.WORKING) {
            float yaw = (float)Math.toRadians(180 - machine.getCachedState().get(IndustrialMachineBlock.FACING).asRotation());
            Vec3d head = LaserCutterModel.headPosition(machine, delta).rotateY(yaw);
            Vec3d start = center.add(head), end = start.add(0, -.43, 0);
            LaserBeamRenderer.render(new LaserBeamTrace(start, end, Direction.DOWN, null), origin, machine.getPos(), 116,
                    time, machine.lightRgb(), .17, true, matrices, consumers);
        }
        if (machine.kind() == MachineKind.CRYSTAL_GROWER && machine.lightFlux() > 0) {
            for (int i = 0; i < 4; i++) {
                double pulse = .5 + .5 * Math.sin(time * .13 + i * 1.9);
                // Use the same frame as the four mounted focusing heads, including block rotation.
                float yaw = (float) Math.toRadians(180 - machine.getCachedState().get(IndustrialMachineBlock.FACING).asRotation());
                Vec3d start = center.add(projectorPosition(i).rotateY(yaw));
                Vec3d end = center.add(focus(time, i).rotateY(yaw));
                var axis = end.subtract(start);
                LaserBeamRenderer.render(new LaserBeamTrace(start, end, Direction.getFacing(axis.x, axis.y, axis.z), null),
                        origin, machine.getPos(), 96 + i, time, machine.lightRgb(), .14 + pulse * .10, true, matrices, consumers);
                // A travelling highlight reads as optical focusing, not an electrical discharge.
                double travel = (time * .027 + i * .25) % 1;
                Vec3d glint = start.lerp(end, travel);
                LaserBeamRenderer.render(new LaserBeamTrace(glint, glint.add(axis.normalize().multiply(.05)), Direction.UP, null),
                        origin, machine.getPos(), 104 + i, time, machine.lightRgb(), .29, true, matrices, consumers);
            }
        }
        if (machine.calibration() > 0 && machine.getStack(IndustrialMachineBlockEntity.OUTPUT).isOf(ModBlocks.POWERED_LASER_EMITTER_ITEM)) {
            Direction direction = machine.getCachedState().get(IndustrialMachineBlock.FACING);
            Vec3d outward = Vec3d.of(direction.getVector());
            Vec3d start = center.add(0, -.25, 0).add(outward.multiply(.15));
            var hit = LaserBeamTrace.traceFrom(machine.getWorld(), center.add(0, -.25, 0).add(outward.multiply(1.01)), direction, 1.4);
            LaserBeamRenderer.render(new LaserBeamTrace(start, hit.end(), direction, hit.hitBlock(), hit.hitSide()), origin,
                    machine.getPos(), 96, time, LaserColor.byIndex(machine.color()).rgb(), .22, true, matrices, consumers);
        }
    }

    public static void renderItem(MachineKind kind, MatrixStack matrices, VertexConsumerProvider consumers, int light, int overlay) {
        matrices.push(); matrices.translate(.5, .5, .5);
        if (kind == MachineKind.LASER_CUTTER) LaserCutterModel.CASING.render(matrices, consumers, light, 0x91DFFF, false, false);
        else if (kind.multiblock()) ChamberModel.casing(kind, matrices, consumers, light);
        else if (kind == MachineKind.CHEMICAL_SYNTHESIZER) ChemicalSynthesizerModel.render(null, matrices, consumers, light);
        else FuelGeneratorModel.render(null,matrices,consumers,light);
        matrices.pop();
    }

    static Vec3d projectorPosition(int index) {
        double angle = index * Math.PI / 2;
        return new Vec3d(Math.cos(angle) * .55, .56, Math.sin(angle) * .55);
    }

    static Vec3d focus(float time, int index) {
        double phase = time * .052 + index * Math.PI / 2;
        return new Vec3d(Math.cos(phase) * .035, -.28 + .045 * Math.sin(phase * .7), Math.sin(phase) * .035);
    }

    private static OpticalComponentMesh projector() {
        var b = new OpticalComponentMesh.Builder("small_solar_concentrator");
        b.bevel("armor", -1.9, -1.9, .65, 1.9, 1.9, 3.9, .4);
        b.profile("metal", false, new double[]{1.8, 1.8, 1.3, .85}, new double[]{1, .3, .02, -.02}, 1.8, 255);
        b.profile("lens", true, new double[]{.85, .72, 0}, new double[]{-.021, -.05, -.06}, .85, 255);
        return b.build();
    }

    private static OpticalComponentMesh growerPorts() {
        var b = new OpticalComponentMesh.Builder("small_solar_concentrator");
        for (Direction side : Direction.Type.HORIZONTAL) {
            var f = new OpticalComponentMesh.Builder("small_solar_concentrator");
            for (double x : new double[]{-8,8}) {
                f.bevel("armor",x-4.2,-11.9,-16.05,x+4.2,-4.1,-15.8,.45);
                f.panel("metal",false,x-3.5,-11.3,x+3.5,-4.7,-16.08);
                f.panel("lens",true,x-2.65,-10.6,x+2.65,-5.4,-16.11);
            }
            b.add(f.build(),side);
        }
        return b.build();
    }


    @Override public boolean rendersOutsideBoundingBox(IndustrialMachineBlockEntity machine) { return machine.kind().multiblock(); }
    // NeoForge queries the renderer; legacy Forge queries the block entity instead.
    public net.minecraft.util.math.Box getRenderBoundingBox(IndustrialMachineBlockEntity machine) { return machine.getRenderBoundingBox(); }
    @Override public int getRenderDistance() { return 128; }
}
