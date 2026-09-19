package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Air has no vanilla step sound: use actual supported travel, including remote players. */
final class BridgeFootsteps {
    private static final Map<UUID, Step> STEPS = new HashMap<>();
    private static net.minecraft.client.world.ClientWorld world;
    private record Step(Vec3d position, Cadence cadence, net.minecraft.client.sound.SoundInstance sound) { }

    static final class Cadence {
        private double walked;
        private int cooldown;

        boolean tick(double distance, boolean supported, boolean sprinting) {
            if (cooldown > 0) cooldown--;
            if (!supported) {
                walked = 0;
                return false;
            }
            if (distance <= .002) return false;
            double stride = sprinting ? 1.35 : 1.55;
            walked = Math.min(stride, walked + distance);
            if (walked < stride || cooldown > 0) return false;
            walked -= stride;
            cooldown = sprinting ? 4 : 8;
            return true;
        }
    }

    static void tick(MinecraftClient client) {
        if (world != client.world) { world = client.world; STEPS.clear(); }
        if (world == null || client.player == null) return;
        STEPS.keySet().removeIf(id -> world.getPlayerByUuid(id) == null);
        for (PlayerEntity player : world.getPlayers()) {
            Step previous = STEPS.get(player.getUuid());
            Vec3d position = player.getPos();
            double distance = previous == null ? 0 : position.subtract(previous.position).horizontalLength();
            Cadence cadence = previous == null ? new Cadence() : previous.cadence;
            Box feet = player.getBoundingBox().contract(.04, 0, .04);
            Box contact = new Box(feet.minX, feet.minY - .045, feet.minZ, feet.maxX, feet.minY + .005, feet.maxZ);
            boolean supported = player.isOnGround() && !player.isSpectator() && !player.isSneaking()
                    && !player.hasVehicle() && !player.getAbilities().flying && distance < 1.5
                    && !LightBridgeNetwork.collisions(world, contact).isEmpty();
            var sound = previous == null ? null : previous.sound;
            if (cadence.tick(distance, supported, player.isSprinting()) && ClientSettings.get().soundVolume > 0
                    && player.squaredDistanceTo(client.player) < 32 * 32) {
                // Sprinting has a shorter cadence; replace this player's previous tail instead of layering it.
                if (sound != null) client.getSoundManager().stop(sound);
                sound = new net.minecraft.client.sound.PositionedSoundInstance(ModSounds.LIGHT_BRIDGE_STEP, SoundCategory.PLAYERS,
                        (float) ClientSettings.get().soundVolume * .6F, 1.0F,
                        world.random, position.x, position.y, position.z);
                client.getSoundManager().play(sound);
            }
            STEPS.put(player.getUuid(), new Step(position, cadence, sound));
        }
    }
    private BridgeFootsteps() { }
}
