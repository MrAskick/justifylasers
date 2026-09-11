package net.askcraft.justifylasers;

import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModItemGroups;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JustifyLasers {
    public static final String MOD_ID = "justifylasers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return GameVersion.id(MOD_ID, path);
    }

    public static void initialize() {
        LaserConfig.initialize();
        Platform.onRegister(RegistryKeys.BLOCK, () -> {
            ModBlocks.initialize();
            ModLaserParts.initializeBlocks();
        });
        Platform.onRegister(RegistryKeys.ITEM, () -> {
            ModBlocks.initializeItems();
            ModEntities.initializeItems();
            ModLaserParts.initialize();
        });
        Platform.onRegister(RegistryKeys.ENTITY_TYPE, ModEntities::initialize);
        Platform.onRegister(RegistryKeys.BLOCK_ENTITY_TYPE, ModBlockEntities::initialize);
        Platform.onRegister(RegistryKeys.SCREEN_HANDLER, ModScreenHandlers::initialize);
        Platform.onRegister(RegistryKeys.ITEM_GROUP, ModItemGroups::initialize);
        Platform.registerSettingsReceiver();
        Platform.registerEnergy();
        LaserBeamNetwork.initialize();

        LOGGER.info("JustifyLasers initialized");
    }
}
