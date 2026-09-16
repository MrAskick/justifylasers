package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public final class ModItemGroups {
    public static ItemGroup JUSTIFY_LASERS;

    public static void initialize() {
        JUSTIFY_LASERS = Platform.register(
                Registries.ITEM_GROUP,
                JustifyLasers.id("justify_lasers"),
                Platform.itemGroupBuilder()
                        .displayName(Text.translatable("itemGroup.justifylasers"))
                        .icon(() -> ModBlocks.LASER_EMITTER_ITEM.getDefaultStack())
                        .entries((displayContext, entries) -> {
                            entries.add(ModBlocks.LASER_EMITTER_ITEM);
                            entries.add(ModBlocks.LASER_RECEIVER_ITEM);
                            entries.add(ModEntities.REFOCUSING_CUBE_ITEM);
                            entries.add(ModBlocks.LASER_MIRROR);
                            entries.add(ModBlocks.BEAM_SPLITTER);
                            entries.add(ModBlocks.BEAM_COMBINER);
                            entries.add(ModBlocks.CONFIGURATOR);
                            entries.add(ModBlocks.LASER_GUN);
                            entries.add(ModBlocks.LASER_SABER);
                            entries.add(ModBlocks.LIGHT_STAFF);
                            entries.add(ModBlocks.LASER_TURRET);
                            entries.add(ModLaserParts.MODULES.get(net.askcraft.justifylasers.energy.LaserModule.TARGET_FILTER));
                            ModLaserParts.CRYSTALS.values().forEach(entries::add);
                            if (LaserConfig.technicalMode()) {
                                ModIndustry.ORES.values().forEach(entries::add);
                                ModIndustry.MACHINES.values().forEach(entries::add);
                                for (var material : new net.minecraft.item.Item[]{ModIndustry.RAW_WOLFRAMITE, ModIndustry.RAW_PHOTONIC_CRYSTAL,
                                        ModIndustry.WOLFRAMITE_INGOT, ModIndustry.PHOTONITE_CRYSTAL, ModIndustry.CRYSTAL_MOUNT}) entries.add(material);
                                ModIndustry.COMPONENTS.values().forEach(entries::add);
                                entries.add(ModIndustry.SOLAR_ABSORBER);
                                entries.add(ModIndustry.SMALL_SOLAR_CONCENTRATOR);
                                entries.add(ModIndustry.ELECTRIC_MOTOR);
                                entries.add(ModIndustry.LASER_ABSORBING_GLASS);
                                entries.add(ModIndustry.BLANK_SCHEMATIC);
                                entries.add(ModIndustry.EXTRATERRESTRIAL_TABLET);
                                ModIndustry.BLUEPRINTS.values().forEach(entries::add);
                                entries.add(ModBlocks.POWERED_LASER_EMITTER_ITEM);
                                entries.add(ModBlocks.ENERGY_RECEIVER);
                                ModLaserParts.MODULES.forEach((module, item) -> {
                                    if (module != net.askcraft.justifylasers.energy.LaserModule.TARGET_FILTER) entries.add(item);
                                });
                                entries.add(ModLaserParts.ADVANCED_RANGE_MODULE);
                                entries.add(ModLaserParts.CONTROL_CIRCUIT);
                            }
                        })
                        .build()
        );
    }

    private ModItemGroups() {
    }
}
