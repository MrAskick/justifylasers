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
import net.minecraft.client.render.VertexFormat;
import net.minecraft.item.Item;

import java.util.function.Consumer;

public final class ClientPlatform {
    public static void initialize() {
        net.askcraft.justifylasers.network.SaberStatePacket.receiver = net.askcraft.justifylasers.client.SaberFencingClient::receive;
        ClientPlayNetworking.registerGlobalReceiver(net.askcraft.justifylasers.network.SaberStatePayload.ID, (payload, context) -> payload.packet().deliver());
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> net.askcraft.justifylasers.client.SaberTooltips.append(stack, lines));
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(net.askcraft.justifylasers.client.ClientSettingsKey.OPEN);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(net.askcraft.justifylasers.client.ClientSettingsKey.SABER_TOGGLE);
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
        ClientTickEvents.END_CLIENT_TICK.register(net.askcraft.justifylasers.client.JustifyLasersClient::tick);
        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        HandledScreens.register(ModScreenHandlers.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.screen.IndustrialMachineScreen::new);
        HandledScreens.register(ModScreenHandlers.SOLAR_CONCENTRATOR, net.askcraft.justifylasers.client.screen.SolarConcentratorScreen::new);
        HandledScreens.register(ModScreenHandlers.TABLET, net.askcraft.justifylasers.client.screen.TabletScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_TURRET, net.askcraft.justifylasers.client.screen.LaserTurretScreen::new);
        EntityRendererRegistry.register(ModEntities.REFOCUSING_CUBE, RefocusingCubeRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_PART, LaserPartRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_COMPONENT, net.askcraft.justifylasers.client.render.LaserComponentBlockRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(net.askcraft.justifylasers.registry.ModIndustry.LASER_ABSORBING_GLASS,
                net.minecraft.client.render.RenderLayer.getTranslucent());
        BlockEntityRendererFactories.register(ModBlockEntities.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.render.IndustrialMachineRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_TURRET, net.askcraft.justifylasers.client.render.LaserTurretRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_OPTIC, net.askcraft.justifylasers.client.render.LaserOpticRenderer::new);
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

    public static void registerShader(String name, VertexFormat format, Consumer<ShaderProgram> loaded) {
        CoreShaderRegistrationCallback.EVENT.register(context ->
                context.register(JustifyLasers.id(name), format, loaded));
    }

    public static void sendSettings(LaserSettingsPacket packet) {
        ClientPlayNetworking.send(new SettingsPayload(packet));
    }

    private ClientPlatform() {
    }

    public static void sendSaberToggle(net.askcraft.justifylasers.network.SaberTogglePacket packet) {
        ClientPlayNetworking.send(new net.askcraft.justifylasers.network.SaberTogglePayload(packet));
    }

    public static void sendGunControl(net.askcraft.justifylasers.network.LaserGunControlPacket packet) {
        ClientPlayNetworking.send(new net.askcraft.justifylasers.network.GunControlPayload(packet));
    }
}
