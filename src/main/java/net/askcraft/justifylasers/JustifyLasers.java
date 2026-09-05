package net.askcraft.justifylasers;

import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModItemGroups;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JustifyLasers implements ModInitializer {
    public static final String MOD_ID = "justifylasers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItemGroups.initialize();
        ModBlockEntities.initialize();
        ModScreenHandlers.initialize();

        LOGGER.info("JustifyLasers initialized");
    }
}
