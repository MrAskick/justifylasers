package net.askcraft.justifylasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.render.LaserPartRenderer;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.askcraft.justifylasers.client.render.LaserRenderFrame;
import net.askcraft.justifylasers.client.render.LaserScorchRenderer;
import net.askcraft.justifylasers.client.render.LaserWorldRenderer;
import net.askcraft.justifylasers.client.render.RefocusingCubeModel;
import net.askcraft.justifylasers.client.render.RefocusingCubeRenderer;
import net.askcraft.justifylasers.client.screen.LaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.LaserReceiverScreen;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.network.PolicyPayload;
import net.askcraft.justifylasers.network.SettingsPayload;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.item.Item;

import java.util.function.Consumer;

public final class ClientPlatform {
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(PolicyPayload.ID, (payload, context) ->
                LaserConfig.applyServerMode(payload.packet().technicalMode()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LaserConfig.resetServerMode());
        ColorProviderRegistry.ITEM.register((stack, tint) -> 0xFF000000 | ((LaserCrystalItem) stack.getItem()).color().rgb(),
                ModLaserParts.CRYSTALS.values().toArray(Item[]::new));
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            LaserRenderFrame frame = new LaserRenderFrame(context.world(), context.tickCounter().getTickDelta(false), context.camera(),
                    context.matrixStack(), context.consumers(), context.frustum());
            LaserWorldRenderer.render(frame);
            LaserScorchRenderer.prepare(frame);
        });
        ClientTickEvents.END_CLIENT_TICK.register(LaserScorchRenderer::tick);
        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        EntityRendererRegistry.register(ModEntities.REFOCUSING_CUBE, RefocusingCubeRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_PART, LaserPartRenderer::new);
        ModLaserParts.items().forEach(item -> BuiltinItemRendererRegistry.INSTANCE.register(item, LaserPartRenderer::renderItem));
        BuiltinItemRendererRegistry.INSTANCE.register(ModEntities.REFOCUSING_CUBE_ITEM,
                (stack, mode, matrices, consumers, light, overlay) -> {
                    matrices.push();
                    matrices.translate(0.5D, 0.5D, 0.5D);
                    int color = LaserColor.byIndex(GameVersion.cubeColor(stack)).rgb();
                    RefocusingCubeModel.render(matrices, consumers, light, color, false, false);
                    matrices.pop();
                });
    }

    public static void registerDepthShader(Consumer<ShaderProgram> loaded) {
        CoreShaderRegistrationCallback.EVENT.register(context ->
                context.register(JustifyLasers.id("depth_merge"), VertexFormats.POSITION, loaded));
    }

    public static void sendSettings(LaserSettingsPacket packet) {
        ClientPlayNetworking.send(new SettingsPayload(packet));
    }

    private ClientPlatform() {
    }
}
