package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
    public static final ScreenHandlerType<LaserEmitterScreenHandler> LASER_EMITTER = Registry.register(
            Registries.SCREEN_HANDLER,
            JustifyLasers.id("laser_emitter"),
            new ExtendedScreenHandlerType<>(LaserEmitterScreenHandler::new)
    );

    private ModScreenHandlers() {
    }

    public static void initialize() {
    }
}
