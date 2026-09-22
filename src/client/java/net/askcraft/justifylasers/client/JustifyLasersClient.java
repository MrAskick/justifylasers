package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.render.LaserDepthMerger;
import net.askcraft.justifylasers.client.render.LaserRenderLayers;
import net.askcraft.justifylasers.client.render.LaserScopeRenderer;
import net.askcraft.justifylasers.client.render.LaserScorchRenderer;
import net.askcraft.justifylasers.client.render.LaserSaberRenderer;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.laser.LaserScorchMarks;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class JustifyLasersClient {
    public static void initialize() {
        net.askcraft.justifylasers.network.LightBridgePacket.receiver = packet ->
                net.askcraft.justifylasers.bridge.LightBridgeNetwork.receive(MinecraftClient.getInstance().world, packet);
        ClientSettings.initialize();
        net.askcraft.justifylasers.client.render.GpuModelRenderer.initialize();
        LaserDepthMerger.initialize();
        net.askcraft.justifylasers.client.render.LightBridgeGpuRenderer.initialize();
        LaserScopeRenderer.initialize();
        net.askcraft.justifylasers.client.render.CubeLensRenderer.initialize();
        net.askcraft.justifylasers.client.render.MirrorRenderer.initialize();
        IrisCompatibility.initialize();
        LaserRenderLayers.initialize();
        ClientPlatform.initialize();
        net.minecraft.client.item.ModelPredicateProviderRegistry.register(net.askcraft.justifylasers.registry.ModLaserParts.AMPLIFIER,
                net.askcraft.justifylasers.JustifyLasers.id("amplifier_tier"),
                (stack, world, entity, seed) -> net.askcraft.justifylasers.item.LaserAmplifierItem.tier(stack) / 10F);
    }

    public static void tick(MinecraftClient client) {
        ConfiguratorControls.tick(client);
        MirrorControls.tick(client);
        BridgeFootsteps.tick(client);
        ClientSettingsKey.tick(client);
        SaberControls.tick(client);
        LaserGunControls.tick(client);
        LaserSaberRenderer.tick(client);
        var saberContacts = ClientSettings.get().scorchMarks || ClientSettings.get().soundVolume > 0
                && LaserConfig.get().laserVolume > 0
                ? LaserSaberRenderer.contacts(client) : List.<LaserScorchMarks.WeaponContact>of();
        LaserScorchRenderer.tick(client, saberContacts);
        LaserSoundController.tick(client, saberContacts);
        net.askcraft.justifylasers.client.render.LaserGunRenderer.tick(client);
    }

    private JustifyLasersClient() {
    }
}
