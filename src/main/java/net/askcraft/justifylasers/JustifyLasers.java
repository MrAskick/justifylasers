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
import net.askcraft.justifylasers.registry.ModSounds;
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
        net.askcraft.justifylasers.registry.ModNutrients.initialize();
        Platform.onRegister(RegistryKeys.BLOCK, () -> {
            ModBlocks.initialize();
            net.askcraft.justifylasers.registry.ModIndustry.initializeBlocks();
            ModLaserParts.initializeBlocks();
            net.askcraft.justifylasers.registry.ModNutrients.blocks();
        });
        Platform.onRegister(RegistryKeys.ITEM, () -> {
            ModBlocks.initializeItems();
            ModEntities.initializeItems();
            ModLaserParts.initialize();
            net.askcraft.justifylasers.registry.ModIndustry.initializeItems();
            net.askcraft.justifylasers.registry.ModNutrients.items();
        });
        Platform.onRegister(RegistryKeys.ENTITY_TYPE, ModEntities::initialize);
        Platform.onRegister(RegistryKeys.BLOCK_ENTITY_TYPE, ModBlockEntities::initialize);
        Platform.onRegister(RegistryKeys.SCREEN_HANDLER, ModScreenHandlers::initialize);
        Platform.onRegister(RegistryKeys.SOUND_EVENT, ModSounds::initialize);
        Platform.onRegister(RegistryKeys.ITEM_GROUP, ModItemGroups::initialize);
        Platform.registerSettingsReceiver();
        Platform.registerEnergy();
        net.askcraft.justifylasers.industry.IndustryRecipe.initialize();
        net.askcraft.justifylasers.industry.AmplifierUpgradeRecipe.initialize();
        Platform.registerExplorationLoot();
        LaserBeamNetwork.initialize();
        Platform.onEndWorldTick(net.askcraft.justifylasers.bridge.LightBridgeNetwork::tick);
        Platform.onEndWorldTick(net.askcraft.justifylasers.laser.SaberCombat::tick);
        Platform.onEndWorldTick(net.askcraft.justifylasers.item.LaserGunItem::tick);

        LOGGER.info("JustifyLasers initialized");
    }
}
