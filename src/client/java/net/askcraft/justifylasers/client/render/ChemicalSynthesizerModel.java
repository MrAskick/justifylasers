package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.industry.ProcessFluid;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

final class ChemicalSynthesizerModel {
    static final OpticalComponentMesh BODY = body(), GAUGE = gauge();

    private static OpticalComponentMesh body() {
        var b = new OpticalComponentMesh.Builder("fuel_generator");
        b.bevel("metal", -7.4, -7.9, -7.3, 7.4, -1, 7.3, .38);
        b.bevel("armor", -2.2, -1, -5.9, 2.2, 6.35, 6.8, .22);
        b.bevel("armor", -7.55, 6.4, -6.9, 7.55, 7.85, 6.9, .25);
        for (int sign : new int[]{-1, 1}) {
            double x = sign * 4.75;
            b.bevel("metal", x - 2.15, -.95, -5.8, x + 2.15, 6.3, 5.8, .45);
            for (double y : new double[]{-.85, 5.1}) b.bevel("armor", x - 2.4, y, -6.1, x + 2.4, y + 1.12, 6.1, .22);
            b.panel("screen", false, x - 1.52, .42, x + 1.52, 4.98, -5.825);
            for (double dx : new double[]{-1.8, 1.8}) b.bevel("metal", x + dx - .15, .34, -5.96, x + dx + .15, 5.04, -5.84, .035);
            b.bevel("armor", x - 2.45, -7.93, -7.5, x + 2.45, -6.6, 7.5, .17);
        }
        b.bevel("metal", -5.7, -6.35, -7.6, 5.7, -1.65, -7.29, .08);
        b.panel("screen", false, -5.45, -6.1, 5.45, -1.9, -7.625);
        b.panel("light", true, -.55, .3, .55, 5.35, -5.925);
        for (Direction side : new Direction[]{Direction.WEST, Direction.EAST, Direction.SOUTH}) {
            var panel = new OpticalComponentMesh.Builder("fuel_generator");
            panel.panel("vent", false, -4.5, -5.8, 4.5, -2, -7.425);
            for (double x : new double[]{-5.7, 5.7}) panel.bevel("armor", x - .5, -6.3, -7.58, x + .5, -.8, -7.2, .09);
            panel.panel("light", true, -2.5, -6.6, 2.5, -6.25, -7.45);
            b.add(panel.build(), side);
        }
        for (double x : new double[]{-4.75, 4.75}) SolarDishModel.upPanel(b, "light", true, x, 0, 7.88, 1.35);
        return b.build();
    }
    private static OpticalComponentMesh gauge() {
        var b = new OpticalComponentMesh.Builder("fuel_generator");
        b.panel("light", true, -1.36, 0, 1.36, 4.4, -5.85);
        return b.build();
    }
    static void render(IndustrialMachineBlockEntity machine, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        boolean working = machine != null && machine.status() == IndustrialMachineBlockEntity.Status.WORKING;
        BODY.render(matrices, consumers, light, 0x78EFDA, working, true);
        if (IrisCompatibility.isRenderingShadowPass() || !net.askcraft.justifylasers.client.ClientSettings.get().machineDisplays) return;
        for (int tank = 0; tank < 2; tank++) {
            int amount = machine == null ? 0 : machine.fluidAmount(tank);
            if (amount == 0) continue;
            matrices.push(); matrices.translate((tank == 0 ? -4.75 : 4.75) / 16, .46 / 16, 0);
            matrices.scale(1, amount / (float) machine.tankCapacity(), 1);
            GAUGE.render(matrices, consumers, light, machine.fluid(tank).rgb(), true, false);
            matrices.pop();
        }
        matrices.push(); matrices.translate(.327, -.135, -.478); matrices.scale(-.005F, -.005F, .005F);
        String state = machine == null ? "idle" : machine.status().name().toLowerCase(java.util.Locale.ROOT);
        line(matrices, consumers, Text.translatable("gui.justifylasers.industry.status." + state), 0, working ? 0x78EFDA : 0x8DADB7);
        line(matrices, consumers, Text.literal((machine == null ? 0 : machine.energy().stored()) + " " + Platform.ENERGY_UNIT), 12, 0xC6EBED);
        line(matrices, consumers, Text.translatable("gui.justifylasers.industry.synth_tanks",
                machine == null ? 0 : machine.fluidAmount(0), machine == null ? 0 : machine.fluidAmount(1)), 24, 0x92D9F1);
        Text product = machine == null || machine.fluidAmount(1) == 0 ? Text.literal("—")
                : Text.translatable("block.justifylasers." + machine.fluid(1).fluidId());
        line(matrices, consumers, product, 36, machine == null ? ProcessFluid.WATER.rgb() : machine.fluid(1).rgb());
        matrices.pop();
    }
    private static void line(MatrixStack matrices, VertexConsumerProvider consumers, Text text, int y, int color) {
        var font = MinecraftClient.getInstance().textRenderer;
        matrices.push(); matrices.translate(0, y, 0);
        float scale = Math.min(1, 130F / Math.max(1, font.getWidth(text))); matrices.scale(scale, scale, 1);
        font.draw(text, 0, 0, color, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }
    private ChemicalSynthesizerModel() { }
}
