package net.askcraft.justifylasers.registry;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;

public final class ModSounds {
    public static SoundEvent LASER_START;
    public static SoundEvent LASER_IDLE;
    public static SoundEvent LASER_STOP;
    public static SoundEvent LASER_CONTACT;
    public static SoundEvent SABER_IDLE;
    public static SoundEvent SABER_IGNITE;
    public static SoundEvent SABER_RETRACT;
    public static SoundEvent SABER_SWING;
    public static SoundEvent SABER_CLASH;
    public static SoundEvent SABER_CATCH;
    public static SoundEvent SABER_FIRE;
    public static SoundEvent STAFF_IGNITE;
    public static SoundEvent STAFF_RETRACT;
    public static SoundEvent LIGHT_BRIDGE_STEP;

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

    private ModSounds() {
    }
}
