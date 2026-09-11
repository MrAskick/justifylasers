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
                            if (LaserConfig.technicalMode()) {
                                entries.add(ModBlocks.POWERED_LASER_EMITTER_ITEM);
                                ModLaserParts.CRYSTALS.values().forEach(entries::add);
                                ModLaserParts.MODULES.values().forEach(entries::add);
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
