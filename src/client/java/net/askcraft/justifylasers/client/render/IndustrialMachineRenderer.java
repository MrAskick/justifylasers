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
    private static final OpticalComponentMesh GENERATOR = legacyChassis(true), SMELTER = legacyChassis(false);

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
            else legacy(machine.kind(), machine.status() == IndustrialMachineBlockEntity.Status.WORKING, matrices, consumers, light);
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
        else legacy(kind, false, matrices, consumers, light);
        matrices.pop();
    }

    private static void legacy(MachineKind kind, boolean running, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        (kind == MachineKind.FUEL_GENERATOR ? GENERATOR : SMELTER).render(matrices, consumers, light, 0xFF9B35, running, true);
    }

    private static OpticalComponentMesh legacyChassis(boolean generator) {
        var b = new OpticalComponentMesh.Builder("laser_saber");
        b.bevel("metal", -8, -8, -8, 8, -4.9, 8, .7);
        b.bevel("metal", -7.7, 6.1, -7.7, 7.7, 8, 7.7, .4);
        for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1})
            b.bevel("steel", x * 6.3 - .65, -4.85, z * 6.3 - .65, x * 6.3 + .65, 6.1, z * 6.3 + .65, .2);
        b.box("control", false, -3.2, -7.4, -8.03, 3.2, -5.6, -7.9);
        b.box("light", true, 4.4, -7.05, -8.045, 5.8, -6.5, -7.88);
        b.box("vent", false, -5.8, -3.8, 6.4, 5.8, 4.8, 7.0);
        b.solid("grip", -5.8, -4.6, -5.7, 5.8, 5.5, 5.6);
        b.bevel("metal", -5.4, -3.8, -6.4, 5.4, 4.5, -5.6, .4);
        b.box("light", true, -3.9, -2.5, -6.45, 3.9, 2.9, -6.40);
        int bars = generator ? 5 : 3;
        for (int i = 0; i < bars; i++) b.solid("grip", -4.2, -2.4 + i * 5.2 / bars, -6.51, 4.2, -2.0 + i * 5.2 / bars, -6.46);
        for (int i = 0; i < 5; i++) b.solid("steel", -5, 8.01, -5 + i * 2.2, 5, 8.30, -4.4 + i * 2.2);
        return b.build();
    }

    @Override public boolean rendersOutsideBoundingBox(IndustrialMachineBlockEntity machine) { return machine.kind().multiblock(); }
    // NeoForge queries the renderer; legacy Forge queries the block entity instead.
    public net.minecraft.util.math.Box getRenderBoundingBox(IndustrialMachineBlockEntity machine) { return machine.getRenderBoundingBox(); }
    @Override public int getRenderDistance() { return 128; }
}
