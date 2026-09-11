package net.askcraft.justifylasers.neoforge;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(JustifyLasers.MOD_ID)
public final class JustifyLasersNeoForge {
    public JustifyLasersNeoForge(IEventBus modBus) {
        Platform.initialize(modBus);
        JustifyLasers.initialize();
    }
}
