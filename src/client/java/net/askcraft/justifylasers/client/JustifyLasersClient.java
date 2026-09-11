package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.render.LaserDepthMerger;
import net.askcraft.justifylasers.client.render.LaserRenderLayers;
import net.askcraft.justifylasers.platform.ClientPlatform;

public final class JustifyLasersClient {
    public static void initialize() {
        LaserDepthMerger.initialize();
        IrisCompatibility.initialize();
        LaserRenderLayers.initialize();
        ClientPlatform.initialize();
    }

    private JustifyLasersClient() {
    }
}
