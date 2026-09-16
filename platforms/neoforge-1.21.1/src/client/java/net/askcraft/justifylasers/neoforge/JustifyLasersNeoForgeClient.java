package net.askcraft.justifylasers.neoforge;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.JustifyLasersClient;
import net.askcraft.justifylasers.client.render.RefocusingCubeRenderer;
import net.askcraft.justifylasers.client.render.LaserPartRenderer;
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
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.io.IOException;

@EventBusSubscriber(modid = JustifyLasers.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JustifyLasersNeoForgeClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(JustifyLasersClient::initialize);
    }

    @SubscribeEvent
    public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(net.askcraft.justifylasers.client.ClientSettingsKey.OPEN);
        event.register(net.askcraft.justifylasers.client.ClientSettingsKey.SABER_TOGGLE);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.REFOCUSING_CUBE, RefocusingCubeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_PART, LaserPartRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_COMPONENT, net.askcraft.justifylasers.client.render.LaserComponentBlockRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.render.IndustrialMachineRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_TURRET, net.askcraft.justifylasers.client.render.LaserTurretRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LASER_OPTIC, net.askcraft.justifylasers.client.render.LaserOpticRenderer::new);
    }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) throws IOException {
        ClientPlatform.loadShaders(event);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        event.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        event.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        event.register(ModScreenHandlers.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.screen.IndustrialMachineScreen::new);
        event.register(ModScreenHandlers.SOLAR_CONCENTRATOR, net.askcraft.justifylasers.client.screen.SolarConcentratorScreen::new);
        event.register(ModScreenHandlers.TABLET, net.askcraft.justifylasers.client.screen.TabletScreen::new);
        event.register(ModScreenHandlers.LASER_TURRET, net.askcraft.justifylasers.client.screen.LaserTurretScreen::new);
    }

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(ClientPlatform.cubeItemExtension(), ModEntities.REFOCUSING_CUBE_ITEM);
        event.registerItem(ClientPlatform.partItemExtension(), ModLaserParts.items().toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> 0xFF000000 | ((LaserCrystalItem) stack.getItem()).color().rgb(),
                ModLaserParts.CRYSTALS.values().toArray(Item[]::new));
    }

    private JustifyLasersNeoForgeClient() {
    }
}
