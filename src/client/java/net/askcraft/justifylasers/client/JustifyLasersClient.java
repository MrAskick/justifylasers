package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.render.LaserDepthMerger;
import net.askcraft.justifylasers.client.render.LaserEmitterBlockEntityRenderer;
import net.askcraft.justifylasers.client.render.LaserRenderLayers;
import net.askcraft.justifylasers.client.screen.LaserEmitterScreen;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class JustifyLasersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LaserDepthMerger.initialize();
        IrisCompatibility.initialize();
        LaserRenderLayers.initialize();
        BlockEntityRendererFactories.register(
                ModBlockEntities.LASER_EMITTER,
                LaserEmitterBlockEntityRenderer::new
        );
        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
    }
}
