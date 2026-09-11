package net.askcraft.justifylasers.forge;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(JustifyLasers.MOD_ID)
public final class JustifyLasersForge {
    public JustifyLasersForge() {
        Platform.initialize(FMLJavaModLoadingContext.get().getModEventBus());
        JustifyLasers.initialize();
    }
}
