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
        ClientSettings.initialize();
        LaserDepthMerger.initialize();
        LaserScopeRenderer.initialize();
        net.askcraft.justifylasers.client.render.CubeLensRenderer.initialize();
        IrisCompatibility.initialize();
        LaserRenderLayers.initialize();
        ClientPlatform.initialize();
    }

    public static void tick(MinecraftClient client) {
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
