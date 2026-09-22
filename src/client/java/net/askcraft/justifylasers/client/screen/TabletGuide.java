package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.energy.AmplifierTier;
import net.askcraft.justifylasers.industry.CrystalGrowth;
import net.askcraft.justifylasers.item.AssemblyBlueprintItem;
import net.askcraft.justifylasers.item.GrowthSeedItem;
import net.askcraft.justifylasers.item.LaserAmplifierItem;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LegacyCrystalSeedItem;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.askcraft.justifylasers.registry.ModNutrients;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class TabletGuide {
    public static final int VISIBLE_LINES = 14;
    private static final Set<String> RETIRED = Set.of("laser_chassis", "optical_assembly", "heat_resistant_alloy", "electric_smelter");
    public record Entry(String id, ItemStack icon, Text title, Text description) { }
    private static List<Entry> cached;
    private static String language;
    public static void clear() { cached = null; }

    public static List<Entry> entries() {
        String current = net.minecraft.client.MinecraftClient.getInstance().options.language;
        if (cached != null && current.equals(language)) return cached;
        language = current;
        var result = new ArrayList<Entry>();
        result.add(topic("first_steps", ModIndustry.EXTRATERRESTRIAL_TABLET.getDefaultStack()));
        result.add(topic("power", ModBlocks.POWERED_LASER_EMITTER_ITEM.getDefaultStack()));
        result.add(topic("multiblocks", ModIndustry.SMALL_SOLAR_CONCENTRATOR.asItem().getDefaultStack()));
        result.add(topic("cultivation", ModNutrients.GROWN.get(CrystalGrowth.DIAMOND).getDefaultStack()));
        result.add(topic("automation", ModBlocks.ENERGY_RECEIVER.asItem().getDefaultStack()));
        var items = new ArrayList<Entry>();
        for (var item : Registries.ITEM) {
            var id = Registries.ITEM.getId(item);
            if (!id.getNamespace().equals("justifylasers") || RETIRED.contains(id.getPath())
                    || item instanceof LegacyCrystalSeedItem || item instanceof GrowthSeedItem || item instanceof LaserAmplifierItem
                    || item instanceof AssemblyBlueprintItem blueprint && !blueprint.recipe().isEmpty()) continue;
            var stack = item == ModIndustry.ASSEMBLY_BLUEPRINT ? AssemblyBlueprintItem.stack("powered_laser_emitter") : item.getDefaultStack();
            String key = descriptionKey(stack);
            var title = item == ModIndustry.ASSEMBLY_BLUEPRINT ? Text.translatable("item.justifylasers.assembly_blueprint") : stack.getName();
            items.add(new Entry(id.getPath(), stack, title, Text.translatable(key)));
        }
        for (var crystal : CrystalGrowth.values()) {
            var seed = crystal.seed(0);
            items.add(new Entry("growth_seed/" + crystal.id(), seed, seed.getName(),
                    Text.translatable("guide.justifylasers.growth_seed", crystal.natural().getName())));
        }
        for (int tier : AmplifierTier.AVAILABLE) {
            var stack = LaserAmplifierItem.stack(tier);
            items.add(new Entry("amplifier_module/" + tier, stack, stack.getName(),
                    Text.translatable("guide.justifylasers.amplifier_module").append("\n\n")
                            .append(Text.translatable("tooltip.justifylasers.amplifier.flux", net.askcraft.justifylasers.laser.LuminousFlux.format(AmplifierTier.lumens(tier))))
                            .append("\n").append(Text.translatable("tooltip.justifylasers.amplifier.energy", AmplifierTier.energy(tier)))));
        }
        items.sort(Comparator.comparing(entry -> entry.title().getString(), String.CASE_INSENSITIVE_ORDER));
        result.addAll(items);
        return cached = List.copyOf(result);
    }
    public static String descriptionKey(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        if (stack.getItem() instanceof LaserCrystalItem) id = "crystal";
        else if (id.endsWith("_ore")) id = id.contains("wolframite") ? "wolframite_ore" : "photonic_crystal_ore";
        else if (id.startsWith("grown_")) id = "grown_crystal";
        else if (id.endsWith("_nutrient_bucket")) id = "nutrient_bucket";
        return "guide.justifylasers." + id;
    }
    private static Entry topic(String id, ItemStack icon) {
        return new Entry("topic/" + id, icon, Text.translatable("gui.justifylasers.tablet.help." + id),
                Text.translatable("guide.justifylasers." + id));
    }
    public static List<Text> lines(Text text, TextRenderer font, int width) {
        var lines = new ArrayList<Text>();
        for (String paragraph : text.getString().split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                if (line.length() > 0 && font.getWidth(line + " " + word) > width) {
                    lines.add(Text.literal(line.toString())); line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            lines.add(Text.literal(line.toString()));
        }
        return List.copyOf(lines);
    }
    private TabletGuide() { }
}
