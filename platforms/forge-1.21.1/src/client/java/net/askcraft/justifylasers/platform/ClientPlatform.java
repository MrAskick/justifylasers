package net.askcraft.justifylasers.platform;

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

public final class ClientPlatform {
    private record ShaderRegistration(String name, VertexFormat format, Consumer<ShaderProgram> loaded) { }
    private static final java.util.List<ShaderRegistration> SHADERS = new java.util.ArrayList<>();

    public static void initialize() {
        net.askcraft.justifylasers.registry.ModNutrients.STILL.values().forEach(fluid ->
                net.minecraft.client.render.RenderLayers.setRenderLayer(fluid, net.minecraft.client.render.RenderLayer.getTranslucent()));
        net.askcraft.justifylasers.registry.ModNutrients.FLOWING.values().forEach(fluid ->
                net.minecraft.client.render.RenderLayers.setRenderLayer(fluid, net.minecraft.client.render.RenderLayer.getTranslucent()));
        net.askcraft.justifylasers.network.SaberStatePacket.receiver = net.askcraft.justifylasers.client.SaberFencingClient::receive;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.ItemTooltipEvent event) -> net.askcraft.justifylasers.client.SaberTooltips.append(event.getItemStack(), event.getToolTip()));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LaserConfig.resetServerMode());
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            // Forge 52 supplies the projection matrix as getPoseStack() at AFTER_ENTITIES.
            // Entity buffers already use the world view transform, so start in local space.
            MatrixStack matrices = new MatrixStack();
            LaserRenderFrame frame = new LaserRenderFrame(client.world, event.getPartialTick(), event.getCamera(),
                    matrices, client.getBufferBuilders().getEntityVertexConsumers(), event.getFrustum());
            LaserWorldRenderer.render(frame);
            LaserScorchRenderer.prepare(frame);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) net.askcraft.justifylasers.client.JustifyLasersClient.tick(MinecraftClient.getInstance());
        });
        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_MODULE, net.askcraft.justifylasers.client.screen.LaserModuleScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
        HandledScreens.register(ModScreenHandlers.INDUSTRIAL_MACHINE, net.askcraft.justifylasers.client.screen.IndustrialMachineScreen::new);
        HandledScreens.register(ModScreenHandlers.SOLAR_CONCENTRATOR, net.askcraft.justifylasers.client.screen.SolarConcentratorScreen::new);
        HandledScreens.register(ModScreenHandlers.TABLET, net.askcraft.justifylasers.client.screen.TabletScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_TURRET, net.askcraft.justifylasers.client.screen.LaserTurretScreen::new);
    }

    public static void registerShader(String name, VertexFormat format, Consumer<ShaderProgram> loaded) {
        SHADERS.add(new ShaderRegistration(name, format, loaded));
    }

    public static void loadShaders(RegisterShadersEvent event) throws IOException {
        for (var registration : SHADERS) {
            event.registerShader(new ShaderProgram(event.getResourceProvider(), JustifyLasers.id(registration.name()), registration.format()),
                    registration.loaded());
        }
    }

    public static void sendSettings(LaserSettingsPacket packet) {
        Platform.NETWORK.send(packet, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    public static IClientItemExtensions cubeItemExtension() {
        return new IClientItemExtensions() {
            private BuiltinModelItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    renderer = new BuiltinModelItemRenderer(client.getBlockEntityRenderDispatcher(), client.getEntityModelLoader()) {
                        @Override
                        public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                           VertexConsumerProvider consumers, int light, int overlay) {
                            matrices.push();
                            matrices.translate(0.5D, 0.5D, 0.5D);
                            int color = LaserColor.byIndex(GameVersion.cubeColor(stack)).rgb();
                            RefocusingCubeModel.render(matrices, consumers, light, color, false, false);
                            matrices.pop();
                        }
                    };
                }
                return renderer;
            }
        };
    }

    public static IClientItemExtensions partItemExtension() {
        return new IClientItemExtensions() {
            private BuiltinModelItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    renderer = new BuiltinModelItemRenderer(client.getBlockEntityRenderDispatcher(), client.getEntityModelLoader()) {
                        @Override
                        public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                           VertexConsumerProvider consumers, int light, int overlay) {
                            LaserPartRenderer.renderItem(stack, mode, matrices, consumers, light, overlay);
                        }
                    };
                }
                return renderer;
            }
        };
    }

    private ClientPlatform() {
    }

    public static void sendConfiguratorMode(net.askcraft.justifylasers.network.ConfiguratorModePacket packet) {
        Platform.NETWORK.send(packet, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    public static void sendMirrorAim(net.askcraft.justifylasers.network.MirrorAimPacket packet) {
        Platform.NETWORK.send(packet, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    public static void sendSaberToggle(net.askcraft.justifylasers.network.SaberTogglePacket packet) {
        Platform.NETWORK.send(packet, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }

    public static void sendGunControl(net.askcraft.justifylasers.network.LaserGunControlPacket packet) {
        Platform.NETWORK.send(packet, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
    }
}
