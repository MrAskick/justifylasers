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
            if (assembled) ChamberModel.render(machine, delta, matrices, consumers, light, overlay);
            else if (machine.kind().multiblock()) ChamberModel.casing(machine.kind(), matrices, consumers, light);
            else FuelGeneratorModel.render(machine,matrices,consumers,light);
            if (assembled && machine.kind() == MachineKind.CRYSTAL_GROWER)
                GROWER_PORTS.render(matrices,consumers,light,0xFFF2CC,machine.lightFlux()>0,true);
        } finally { matrices.pop(); }
        if (assembled && !IrisCompatibility.isRenderingShadowPass()) effects(machine, delta, matrices, consumers);
    }

    private static void effects(IndustrialMachineBlockEntity machine, float delta, MatrixStack matrices, VertexConsumerProvider consumers) {
        Vec3d origin = Vec3d.of(machine.getPos()), center = origin.add(1, 1, 1);
        float time = machine.getWorld().getTime() + delta;
        if (machine.kind() == MachineKind.CRYSTAL_GROWER && machine.status() == IndustrialMachineBlockEntity.Status.WORKING
                && machine.getWorld().getTime() % 11 < 4) {
            // Deterministic, short arcs inside the chamber. They never enter server beam tracing.
            for (int side = 0; side < 2; side++) {
                double angle = (Math.floor(time / 3) * 1.91 + side * Math.PI);
                Vec3d from = center.add(Math.cos(angle) * .58, .30, Math.sin(angle) * .58);
                Vec3d to = center.add(0, -.13 + machine.completion(delta) * .2, 0);
                Vec3d previous = from;
                for (int segment = 1; segment <= 4; segment++) {
                    Vec3d next = from.lerp(to, segment / 4d);
                    if (segment < 4) next = next.add(Math.sin(angle + segment * 7) * .045, Math.cos(angle * 2 + segment) * .06, Math.cos(angle + segment * 9) * .045);
                    LaserBeamRenderer.render(new LaserBeamTrace(previous, next, Direction.DOWN, null), origin, machine.getPos(),
                            100 + side * 4 + segment, time, side == 0 ? 0x8552FF : 0x457CFF, .13, true, matrices, consumers);
                    previous = next;
                }
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
        if (kind.multiblock()) ChamberModel.casing(kind, matrices, consumers, light);
        else FuelGeneratorModel.render(null,matrices,consumers,light);
        matrices.pop();
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
