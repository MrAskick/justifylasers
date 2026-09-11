package net.askcraft.justifylasers.fabric;

import net.askcraft.justifylasers.JustifyLasers;
import net.fabricmc.api.ModInitializer;

public final class JustifyLasersFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        JustifyLasers.initialize();
    }
}
