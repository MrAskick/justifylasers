package net.askcraft.justifylasers.fabric;

import net.askcraft.justifylasers.client.JustifyLasersClient;
import net.fabricmc.api.ClientModInitializer;

public final class JustifyLasersFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        JustifyLasersClient.initialize();
    }
}
