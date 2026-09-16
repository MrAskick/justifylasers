package net.askcraft.justifylasers.forge;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.JustifyLasersClient;
import net.askcraft.justifylasers.client.render.RefocusingCubeRenderer;
import net.askcraft.justifylasers.client.render.LaserPartRenderer;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.askcraft.justifylasers.item.LaserCrystalItem;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.registry.ModEntities;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = JustifyLasers.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class JustifyLasersForgeClient {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(JustifyLasersClient::initialize);
    }

    @SubscribeEvent
    public static void keys(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
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
    public static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> ((LaserCrystalItem) stack.getItem()).color().rgb(),
                ModLaserParts.CRYSTALS.values().toArray(Item[]::new));
    }

    private JustifyLasersForgeClient() {
    }
}
