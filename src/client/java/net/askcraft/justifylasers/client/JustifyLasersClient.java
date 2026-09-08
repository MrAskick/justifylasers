package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.render.LaserDepthMerger;
import net.askcraft.justifylasers.client.render.LaserRenderLayers;
import net.askcraft.justifylasers.client.render.LaserScorchRenderer;
import net.askcraft.justifylasers.client.render.LaserWorldRenderer;
import net.askcraft.justifylasers.client.render.RefocusingCubeModel;
import net.askcraft.justifylasers.client.render.RefocusingCubeRenderer;
import net.askcraft.justifylasers.client.screen.LaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.LaserReceiverScreen;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class JustifyLasersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LaserDepthMerger.initialize();
        IrisCompatibility.initialize();
        LaserRenderLayers.initialize();
        WorldRenderEvents.AFTER_ENTITIES.register(LaserWorldRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(LaserScorchRenderer::prepare);
        ClientTickEvents.END_CLIENT_TICK.register(LaserScorchRenderer::tick);
        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        EntityRendererRegistry.register(ModEntities.REFOCUSING_CUBE, RefocusingCubeRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(ModEntities.REFOCUSING_CUBE_ITEM,
                (stack, mode, matrices, consumers, light, overlay) -> {
                    matrices.push();
                    matrices.translate(0.5D, 0.5D, 0.5D);
                    int color = stack.hasNbt() ? LaserColor.byIndex(stack.getNbt().getInt("Color")).rgb() : LaserColor.RED.rgb();
                    RefocusingCubeModel.render(matrices, consumers, light, color, false, false);
                    matrices.pop();
                });
    }
}
