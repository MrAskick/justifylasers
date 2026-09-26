package net.askcraft.justifylasers.addon.lasers.platform;
import net.askcraft.justifylasers.platform.GameVersion;

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

public final class LaserClientPlatform {
    public static void initialize() {
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(net.askcraft.justifylasers.client.ClientSettingsKey.CONFIGURATOR);
        net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(net.askcraft.justifylasers.client.ClientSettingsKey.SABER_TOGGLE);
        ColorProviderRegistry.ITEM.register((stack, tint) -> 0xFF000000 | ((LaserCrystalItem) stack.getItem()).color().rgb(),
                ModLaserParts.CRYSTALS.values().toArray(Item[]::new));
        net.askcraft.justifylasers.registry.ModNutrients.STILL.forEach((kind, fluid) -> {
            var flowing = net.askcraft.justifylasers.registry.ModNutrients.FLOWING.get(kind);
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.register(fluid, flowing,
                    new net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler(
                            kind.texture(false), kind.texture(true), kind.tint()));
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putFluids(net.minecraft.client.render.RenderLayer.getTranslucent(), fluid, flowing);
        });


        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_MODULE, net.askcraft.justifylasers.client.screen.LaserModuleScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        HandledScreens.register(ModScreenHandlers.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.screen.IndustrialMachineScreen::new);
        HandledScreens.register(ModScreenHandlers.MODEL_ENCODER, net.askcraft.justifylasers.client.screen.ModelEncoderScreen::new);
        HandledScreens.register(ModScreenHandlers.SOLAR_CONCENTRATOR, net.askcraft.justifylasers.client.screen.SolarConcentratorScreen::new);
        HandledScreens.register(ModScreenHandlers.TABLET, net.askcraft.justifylasers.client.screen.TabletScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_TURRET, net.askcraft.justifylasers.client.screen.LaserTurretScreen::new);
        EntityRendererRegistry.register(ModEntities.REFOCUSING_CUBE, RefocusingCubeRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_PART, LaserPartRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_COMPONENT, net.askcraft.justifylasers.client.render.LaserComponentBlockRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(net.askcraft.justifylasers.registry.ModIndustry.LASER_ABSORBING_GLASS,
                net.minecraft.client.render.RenderLayer.getTranslucent());
        BlockEntityRendererFactories.register(ModBlockEntities.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.render.IndustrialMachineRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.PRINTED_MODEL, net.askcraft.justifylasers.client.render.PrintedModelRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_TURRET, net.askcraft.justifylasers.client.render.LaserTurretRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LASER_OPTIC, net.askcraft.justifylasers.client.render.LaserOpticRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.LIGHT_BRIDGE, net.askcraft.justifylasers.client.render.LightBridgeBlockRenderer::new);
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
    public static void event(Object input) {

    }
}
