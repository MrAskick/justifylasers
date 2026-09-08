package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.askcraft.justifylasers.screen.LaserReceiverScreenHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

    public static final ScreenHandlerType<LaserReceiverScreenHandler> LASER_RECEIVER = Registry.register(
            Registries.SCREEN_HANDLER, JustifyLasers.id("laser_receiver"),
            new ExtendedScreenHandlerType<>(LaserReceiverScreenHandler::new)
    );

    private ModScreenHandlers() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(LaserSettingsPacket.TYPE,
                (packet, player, responseSender) -> packet.apply(player));
    }
}
