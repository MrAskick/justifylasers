package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.item.LaserModuleItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.item.Item;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.registry.Registries;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModLaserParts {
    public static final Map<String, LaserPartBlock> DECORATIONS = new LinkedHashMap<>();
    public static final Map<LaserColor, LaserCrystalItem> CRYSTALS = new EnumMap<>(LaserColor.class);
    public static final Map<LaserModule, LaserModuleItem> MODULES = new EnumMap<>(LaserModule.class);
    public static LaserModuleItem ADVANCED_RANGE_MODULE;
    public static Item CONTROL_CIRCUIT;

    public static void initializeBlocks() {
        for (LaserColor color : LaserColor.values()) registerDecoration(color.asString() + "_crystal");
        for (LaserModule module : LaserModule.values()) registerDecoration(module.id());
        registerDecoration("advanced_range_module");
        registerDecoration("control_circuit");
    }

    private static void registerDecoration(String id) {
        DECORATIONS.put(id, Platform.register(Registries.BLOCK, JustifyLasers.id(id),
                new LaserPartBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
                        .strength(0.6F).sounds(BlockSoundGroup.METAL).nonOpaque(), id)));
    }

    public static List<Item> items() {
        return DECORATIONS.values().stream().map(block -> block.asItem()).toList();
    }

    public static void initialize() {
        for (LaserColor color : LaserColor.values()) {
            CRYSTALS.put(color, Platform.register(Registries.ITEM, JustifyLasers.id(color.asString() + "_crystal"),
                    Platform.crystalItem(new Item.Settings().maxCount(1), color)));
        }
        for (LaserModule module : LaserModule.values()) {
            MODULES.put(module, Platform.register(Registries.ITEM, JustifyLasers.id(module.id()),
                    Platform.moduleItem(new Item.Settings().maxCount(module.maxCount()), module, module == LaserModule.RANGE ? 1 : 0)));
        }
        ADVANCED_RANGE_MODULE = Platform.register(Registries.ITEM, JustifyLasers.id("advanced_range_module"),
                Platform.moduleItem(new Item.Settings(), LaserModule.RANGE, 8));
        CONTROL_CIRCUIT = Platform.register(Registries.ITEM, JustifyLasers.id("control_circuit"), Platform.partItem(new Item.Settings(), "control_circuit"));
    }

    private ModLaserParts() {
    }
}
