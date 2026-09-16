package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.laser.OpticPortMode;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

final class BeamCombinerModel {
    // Shares the machined socket family; a faceted central chamber distinguishes the combiner.
    static final OpticalComponentMesh CORE = core();

    static void render(LaserOpticBlockEntity optic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        boolean active = optic.getCachedState().get(LaserOpticBlock.LIT);
        CORE.render(matrices, consumers, light, optic.rgb(), active, optic.emitsShaderLight());
        for (Direction side : Direction.values()) {
            var mode = optic.portMode(side);
            BeamSplitterModel.PORTS.get(side).get(mode).render(matrices, consumers, light, optic.rgb(),
                    active && mode != OpticPortMode.DISABLED, optic.emitsShaderLight());
        }
    }

    private static OpticalComponentMesh core() {
        var mesh = new OpticalComponentMesh.Builder("beam_splitter");
        mesh.bevel("steel", -2.84, -2.84, -2.84, 2.84, 2.84, 2.84, .55);
        for (Direction side : Direction.values()) {
            var plate = new OpticalComponentMesh.Builder("beam_splitter");
            plate.panel("corner", true, -1.7, -1.7, 1.7, 1.7, -2.85);
            mesh.add(plate.build(), side);
        }
        return mesh.build();
    }
    private BeamCombinerModel() { }
}
