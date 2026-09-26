package net.askcraft.justifylasers.addon.lasers;

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


import net.askcraft.justifylasers.addon.lasers.registry.*;
import net.askcraft.justifylasers.api.JustifyModule;

public final class LasersModule implements JustifyModule {
    public String id() { return "justifylasers"; }
    public void initialize() {
        LaserConfig.initialize();
        LaserNutrients.initialize();
        Platform.onRegister(RegistryKeys.BLOCK, () -> {
            LaserBlocks.initialize();
            LaserIndustry.initializeBlocks();
            LaserLaserParts.initializeBlocks();
            LaserNutrients.blocks();
        });
        Platform.onRegister(RegistryKeys.ITEM, () -> {
            LaserBlocks.initializeItems();
            LaserEntities.initializeItems();
            LaserLaserParts.initialize();
            LaserIndustry.initializeItems();
            LaserNutrients.items();
        });
        Platform.onRegister(RegistryKeys.ENTITY_TYPE, LaserEntities::initialize);
        Platform.onRegister(RegistryKeys.BLOCK_ENTITY_TYPE, LaserBlockEntities::initialize);
        Platform.onRegister(RegistryKeys.SCREEN_HANDLER, LaserScreenHandlers::initialize);
        Platform.onRegister(RegistryKeys.SOUND_EVENT, LaserSounds::initialize);
        Platform.onRegister(RegistryKeys.ITEM_GROUP, LaserItemGroups::initialize);
        Platform.registerEnergy();
        net.askcraft.justifylasers.industry.IndustryRecipe.initialize();
        net.askcraft.justifylasers.industry.AmplifierUpgradeRecipe.initialize();
        net.askcraft.justifylasers.industry.BlueprintClearingRecipe.initialize();
        net.askcraft.justifylasers.industry.GrowthSeedRecipe.initialize();
        Platform.registerExplorationLoot();
        LaserBeamNetwork.initialize();
        Platform.onEndWorldTick(net.askcraft.justifylasers.bridge.LightBridgeNetwork::tick);
        Platform.onEndWorldTick(net.askcraft.justifylasers.laser.SaberCombat::tick);
        Platform.onEndWorldTick(net.askcraft.justifylasers.item.LaserGunItem::tick);

    }
}
