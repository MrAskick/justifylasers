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
