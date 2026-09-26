package net.askcraft.justifylasers.addon.lasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.render.LaserPartRenderer;
import net.askcraft.justifylasers.client.render.LaserRenderFrame;
import net.askcraft.justifylasers.client.render.LaserScorchRenderer;
import net.askcraft.justifylasers.client.render.LaserWorldRenderer;
import net.askcraft.justifylasers.client.render.RefocusingCubeModel;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.network.SettingsPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.IOException;
import java.util.function.Consumer;
import net.askcraft.justifylasers.client.JustifyLasersClient;
import net.askcraft.justifylasers.client.render.RefocusingCubeRenderer;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.client.screen.LaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.LaserReceiverScreen;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class LaserClientPlatform {
    public static void initialize() {
        net.askcraft.justifylasers.registry.ModNutrients.STILL.values().forEach(fluid ->
                net.minecraft.client.render.RenderLayers.setRenderLayer(fluid, net.minecraft.client.render.RenderLayer.getTranslucent()));
        net.askcraft.justifylasers.registry.ModNutrients.FLOWING.values().forEach(fluid ->
                net.minecraft.client.render.RenderLayers.setRenderLayer(fluid, net.minecraft.client.render.RenderLayer.getTranslucent()));

    }
    public static void event(Object input) {
        if (input instanceof net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {

        event.register(net.askcraft.justifylasers.client.ClientSettingsKey.SABER_TOGGLE);
        event.register(net.askcraft.justifylasers.client.ClientSettingsKey.CONFIGURATOR);
            }
        if (input instanceof EntityRenderersEvent.RegisterRenderers event) {

        event.registerEntityRenderer(ModEntities.REFOCUSING_CUBE, RefocusingCubeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_PART, LaserPartRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_COMPONENT, net.askcraft.justifylasers.client.render.LaserComponentBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.render.IndustrialMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PRINTED_MODEL, net.askcraft.justifylasers.client.render.PrintedModelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_TURRET, net.askcraft.justifylasers.client.render.LaserTurretRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_OPTIC, net.askcraft.justifylasers.client.render.LaserOpticRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LIGHT_BRIDGE, net.askcraft.justifylasers.client.render.LightBridgeBlockRenderer::new);
            }
        if (input instanceof RegisterColorHandlersEvent.Item event) {

        event.register((stack, tint) -> 0xFF000000 | ((LaserCrystalItem) stack.getItem()).color().rgb(),
                ModLaserParts.CRYSTALS.values().toArray(Item[]::new));
            }
        if (input instanceof RegisterMenuScreensEvent event) {

        event.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        event.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        event.register(ModScreenHandlers.LASER_MODULE, net.askcraft.justifylasers.client.screen.LaserModuleScreen::new);
        event.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        event.register(ModScreenHandlers.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.screen.IndustrialMachineScreen::new);
        event.register(ModScreenHandlers.MODEL_ENCODER, net.askcraft.justifylasers.client.screen.ModelEncoderScreen::new);
        event.register(ModScreenHandlers.SOLAR_CONCENTRATOR, net.askcraft.justifylasers.client.screen.SolarConcentratorScreen::new);
        event.register(ModScreenHandlers.TABLET, net.askcraft.justifylasers.client.screen.TabletScreen::new);
        event.register(ModScreenHandlers.LASER_TURRET, net.askcraft.justifylasers.client.screen.LaserTurretScreen::new);
            }
        if (input instanceof RegisterClientExtensionsEvent event) {

        net.askcraft.justifylasers.platform.PlatformNutrients.TYPES.forEach((kind, type) ->
                event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                    @Override public net.minecraft.util.Identifier getStillTexture() { return kind.texture(false); }
                    @Override public net.minecraft.util.Identifier getFlowingTexture() { return kind.texture(true); }
                    @Override public int getTintColor() { return kind.tint(); }
                }, type));
        event.registerItem(ClientPlatform.cubeItemExtension(), ModEntities.REFOCUSING_CUBE_ITEM);
        event.registerItem(ClientPlatform.partItemExtension(), ModLaserParts.items().toArray(Item[]::new));
            }
    }
}
