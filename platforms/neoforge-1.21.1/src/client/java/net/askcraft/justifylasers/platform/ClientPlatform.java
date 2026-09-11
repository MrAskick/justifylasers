package net.askcraft.justifylasers.platform;

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
import net.minecraft.client.render.VertexFormats;
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

public final class ClientPlatform {
    private static Consumer<ShaderProgram> depthShaderLoaded;

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LaserConfig.resetServerMode());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            LaserRenderFrame frame = new LaserRenderFrame(client.world, event.getPartialTick().getTickDelta(false), event.getCamera(),
                    event.getPoseStack(), client.getBufferBuilders().getEntityVertexConsumers(), event.getFrustum());
            LaserWorldRenderer.render(frame);
            LaserScorchRenderer.prepare(frame);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            LaserScorchRenderer.tick(MinecraftClient.getInstance());
        });
    }

    public static void registerDepthShader(Consumer<ShaderProgram> loaded) {
        depthShaderLoaded = loaded;
    }

    public static void loadDepthShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderProgram(event.getResourceProvider(), JustifyLasers.id("depth_merge"), VertexFormats.POSITION),
                shader -> depthShaderLoaded.accept(shader));
    }

    public static void sendSettings(LaserSettingsPacket packet) {
        PacketDistributor.sendToServer(new SettingsPayload(packet));
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
}
