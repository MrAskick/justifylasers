package net.askcraft.justifylasers.addon.lasers.platform;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.render.LaserPartRenderer;
import net.askcraft.justifylasers.client.render.LaserRenderFrame;
import net.askcraft.justifylasers.client.render.LaserScorchRenderer;
import net.askcraft.justifylasers.client.render.LaserWorldRenderer;
import net.askcraft.justifylasers.client.render.RefocusingCubeModel;
import net.askcraft.justifylasers.client.screen.LaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.PoweredLaserEmitterScreen;
import net.askcraft.justifylasers.client.screen.LaserReceiverScreen;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.registry.ModScreenHandlers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.io.IOException;
import java.util.function.Consumer;
import net.askcraft.justifylasers.client.JustifyLasersClient;
import net.askcraft.justifylasers.client.render.RefocusingCubeRenderer;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class LaserClientPlatform {
    public static void initialize() {
        net.askcraft.justifylasers.registry.ModNutrients.STILL.values().forEach(fluid ->
                net.minecraft.client.render.RenderLayers.setRenderLayer(fluid, net.minecraft.client.render.RenderLayer.getTranslucent()));
        net.askcraft.justifylasers.registry.ModNutrients.FLOWING.values().forEach(fluid ->
                net.minecraft.client.render.RenderLayers.setRenderLayer(fluid, net.minecraft.client.render.RenderLayer.getTranslucent()));


        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_MODULE, net.askcraft.justifylasers.client.screen.LaserModuleScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        HandledScreens.register(ModScreenHandlers.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.screen.IndustrialMachineScreen::new);
        HandledScreens.register(ModScreenHandlers.MODEL_ENCODER, net.askcraft.justifylasers.client.screen.ModelEncoderScreen::new);
        HandledScreens.register(ModScreenHandlers.SOLAR_CONCENTRATOR, net.askcraft.justifylasers.client.screen.SolarConcentratorScreen::new);
        HandledScreens.register(ModScreenHandlers.TABLET, net.askcraft.justifylasers.client.screen.TabletScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_TURRET, net.askcraft.justifylasers.client.screen.LaserTurretScreen::new);

    }
    public static void event(Object input) {
        if (input instanceof net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {

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

        event.register((stack, tint) -> ((LaserCrystalItem) stack.getItem()).color().rgb(),
                ModLaserParts.CRYSTALS.values().toArray(Item[]::new));
            }
    }
}
