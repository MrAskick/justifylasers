package net.askcraft.justifylasers.addon.lasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;


import net.askcraft.justifylasers.registry.*;

public final class LaserSounds extends ModSounds {
    public static void initialize() {
        LIGHT_BRIDGE_STEP = register("light_bridge_step");
        LASER_START = register("laser_start");
        LASER_IDLE = register("laser_idle");
        LASER_STOP = register("laser_stop");
        LASER_CONTACT = register("laser_contact");
        SABER_IDLE = register("saber_idle");
        SABER_IGNITE = register("saber_ignite");
        SABER_RETRACT = register("saber_retract");
        SABER_SWING = register("saber_swing");
        SABER_CLASH = register("saber_clash");
        SABER_CATCH = register("saber_catch");
        SABER_FIRE = register("saber_fire");
        STAFF_IGNITE = register("staff_ignite");
        STAFF_RETRACT = register("staff_retract");
    }

    private static SoundEvent register(String path) {
        var id = JustifyLasers.id(path);
        return Platform.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
