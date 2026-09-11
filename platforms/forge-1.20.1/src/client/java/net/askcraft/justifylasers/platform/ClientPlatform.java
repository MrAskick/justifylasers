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
import net.minecraft.client.render.VertexFormats;
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
    private static Consumer<ShaderProgram> depthShaderLoaded;

    public static void initialize() {
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LaserConfig.resetServerMode());
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            LaserRenderFrame frame = new LaserRenderFrame(client.world, event.getPartialTick(), event.getCamera(),
                    event.getPoseStack(), client.getBufferBuilders().getEntityVertexConsumers(), event.getFrustum());
            LaserWorldRenderer.render(frame);
            LaserScorchRenderer.prepare(frame);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) LaserScorchRenderer.tick(MinecraftClient.getInstance());
        });
        HandledScreens.register(ModScreenHandlers.LASER_EMITTER, LaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.POWERED_LASER_EMITTER, PoweredLaserEmitterScreen::new);
        HandledScreens.register(ModScreenHandlers.LASER_RECEIVER, LaserReceiverScreen::new);
    }

    public static void registerDepthShader(Consumer<ShaderProgram> loaded) {
        depthShaderLoaded = loaded;
    }

    public static void loadDepthShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderProgram(event.getResourceProvider(), JustifyLasers.id("depth_merge"), VertexFormats.POSITION),
                shader -> depthShaderLoaded.accept(shader));
    }

    public static void sendSettings(LaserSettingsPacket packet) {
        Platform.NETWORK.sendToServer(packet);
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
                            int color = stack.hasNbt() ? LaserColor.byIndex(stack.getNbt().getInt("Color")).rgb() : LaserColor.RED.rgb();
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
}
