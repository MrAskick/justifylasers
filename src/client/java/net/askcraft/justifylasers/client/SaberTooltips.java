package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class SaberTooltips {
    public static void append(ItemStack stack, List<Text> lines) {
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LaserAmplifierItem) {
            int tier = net.askcraft.justifylasers.item.LaserAmplifierItem.tier(stack);
            lines.add(Text.translatable("tooltip.justifylasers.amplifier.flux", net.askcraft.justifylasers.laser.LuminousFlux.format(net.askcraft.justifylasers.energy.AmplifierTier.lumens(tier))).formatted(Formatting.AQUA));
            lines.add(Text.translatable("tooltip.justifylasers.amplifier.energy", net.askcraft.justifylasers.energy.AmplifierTier.energy(tier, net.askcraft.justifylasers.config.LaserConfig.get().lumensPerEnergyUnit)).formatted(Formatting.GRAY));
            lines.add(Text.translatable("tooltip.justifylasers.amplifier.upgrade").formatted(Formatting.GRAY));
        }
        if (net.askcraft.justifylasers.industry.CrystalSeed.synthetic(stack))
            lines.add(Text.translatable("tooltip.justifylasers.synthetic_crystal").formatted(Formatting.AQUA));
        for (var crystal : net.askcraft.justifylasers.industry.CrystalGrowth.values()) {
            int stage = crystal.stage(stack);
            if (stage > 0) lines.add(Text.translatable("tooltip.justifylasers.seed_stage." + stage).formatted(Formatting.GRAY));
        }
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.LaserConfiguratorItem tool) {
            lines.add(Text.translatable("item.justifylasers.configurator.mode." + tool.mode(stack))
                    .styled(style -> style.withColor(tool.color(tool.mode(stack)))));
            lines.add(Text.translatable("message.justifylasers.configurator_charge", tool.readEnergy(stack), tool.CAPACITY).formatted(Formatting.GRAY));
            lines.add(Text.translatable("tooltip.justifylasers.configurator.radial", ClientSettingsKey.CONFIGURATOR.getBoundKeyLocalizedText()).formatted(Formatting.AQUA));
            lines.add(Text.translatable("tooltip.justifylasers.configurator.charge").formatted(Formatting.GRAY));
            if (tool.mode(stack) == 3) lines.add(Text.translatable("tooltip.justifylasers.mirror_aim").formatted(Formatting.GRAY));
        }
        if (stack.getItem() instanceof net.minecraft.item.BlockItem blockItem && blockItem.getBlock() instanceof net.askcraft.justifylasers.block.LightBridgeBlock)
            lines.add(Text.translatable("tooltip.justifylasers.bridge_placement").formatted(Formatting.AQUA));
        if (stack.isOf(net.askcraft.justifylasers.registry.ModBlocks.CORNER_LIGHT_BRIDGE.asItem())) {
            lines.add(Text.translatable("tooltip.justifylasers.corner_light_bridge").formatted(Formatting.AQUA));
            lines.add(Text.translatable("tooltip.justifylasers.light_bridge.inputs").formatted(Formatting.GRAY));
        }
        if (stack.isOf(net.askcraft.justifylasers.registry.ModBlocks.LIGHT_BRIDGE.asItem()))
            for (String suffix : new String[]{"", ".inputs", ".width", ".range"})
                lines.add(Text.translatable("tooltip.justifylasers.light_bridge" + suffix).formatted(Formatting.GRAY));
        if (net.askcraft.justifylasers.laser.WeaponHands.weapon(stack))
            lines.add(Text.translatable("tooltip.justifylasers.dual_weapons").formatted(Formatting.AQUA));
        if (stack.getItem() instanceof net.askcraft.justifylasers.item.AssemblyBlueprintItem blueprint) {
            lines.add(Text.translatable("gui.justifylasers.industry.blueprint_reusable").formatted(Formatting.AQUA));
            lines.add(Text.translatable(blueprint.recipe().equals("extraterrestrial_tablet")
                    ? "tooltip.justifylasers.tablet_schematic" : "tooltip.justifylasers.blueprint_tablet").formatted(Formatting.GRAY));
        }
        if (stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(net.askcraft.justifylasers.industry.MachineKind.CRYSTAL_GROWER).asItem())
                || stack.isOf(net.askcraft.justifylasers.registry.ModIndustry.MACHINES.get(net.askcraft.justifylasers.industry.MachineKind.ASSEMBLY_CHAMBER).asItem()))
            lines.add(Text.translatable("tooltip.justifylasers.chamber_structure").formatted(Formatting.GRAY));
        if (!(stack.getItem() instanceof LaserSaberItem)) return;
        LaserColor color = LaserColor.byIndex(GameVersion.cubeColor(stack));
        lines.add(Text.translatable("message.justifylasers.saber_color", Text.translatable(color.translationKey()))
                .styled(style -> style.withColor(color.rgb())));
        lines.add(Text.translatable("tooltip.justifylasers.saber_use", ClientSettingsKey.SABER_TOGGLE.getBoundKeyLocalizedText()).formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.justifylasers.saber_attack").formatted(Formatting.GRAY));
    }

    private SaberTooltips() { }
}
