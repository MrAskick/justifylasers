package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.askcraft.justifylasers.screen.LaserReceiverScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
    public static ScreenHandlerType<LaserEmitterScreenHandler> LASER_EMITTER;
    public static ScreenHandlerType<LaserEmitterScreenHandler> POWERED_LASER_EMITTER;

    public static ScreenHandlerType<LaserReceiverScreenHandler> LASER_RECEIVER;

    private ModScreenHandlers() {
    }

    public static void initialize() {
        LASER_EMITTER = Platform.register(
                Registries.SCREEN_HANDLER,
                JustifyLasers.id("laser_emitter"),
                Platform.screenType(LaserEmitterScreenHandler::new)
        );

        POWERED_LASER_EMITTER = Platform.register(
                Registries.SCREEN_HANDLER, JustifyLasers.id("powered_laser_emitter"),
                Platform.screenType((syncId, inventory, pos) -> new LaserEmitterScreenHandler(syncId, inventory, pos, true))
        );

        LASER_RECEIVER = Platform.register(
                Registries.SCREEN_HANDLER, JustifyLasers.id("laser_receiver"),
                Platform.screenType(LaserReceiverScreenHandler::new)
        );
    }
}
