package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.LaserSoundController;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.SaberCombat;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstanceListener;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

final class SaberAudioSmoke implements SoundInstanceListener {
    private static final SaberAudioSmoke LISTENER = new SaberAudioSmoke();
    private static final Map<String, Integer> EVENTS = new HashMap<>();
    private static final Map<String, SoundInstance> LAST = new HashMap<>();
    private static int surfaceBefore;

    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) {
            ClientSettings.reset();
            LaserConfig.get().laserVolume = .65;
            client.getSoundManager().registerListener(LISTENER);
            verifyRecordings(client);
            client.player.getInventory().selectedSlot = 0;
            client.getServer().execute(() -> scene(client));
        }
        if (tick == 50) {
            EVENTS.clear(); LAST.clear();
            equip(client, Hand.MAIN_HAND, false, false);
        }
        if (tick == 70) { count("saber_catch", 1); count("saber_ignite", 0); loops(0); }
        if (tick == 75) toggle(client, Hand.MAIN_HAND);
        if (tick == 82) client.options.attackKey.setPressed(true);
        if (tick == 90) {
            count("saber_ignite", 1); loops(1);
            try {
                var sound = LAST.get("saber_ignite");
                var remaining = sound.getClass().getDeclaredField("remaining");
                remaining.setAccessible(true);
                if (remaining.getInt(sound) != 0 || sound.isRepeatable()) throw new AssertionError("Recorded ignition must finish naturally");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        if (tick == 98) {
            client.options.attackKey.setPressed(false);
            if (EVENTS.getOrDefault("saber_swing", 0) < 1) throw new AssertionError("Network swing was silent");
        }
        if (tick == 100) toggle(client, Hand.MAIN_HAND);
        if (tick == 120) { count("saber_retract", 1); loops(0); }
        if (tick == 130) equip(client, Hand.OFF_HAND, true, false);
        if (tick == 150) { count("saber_catch", 2); count("staff_ignite", 0); }
        if (tick == 155) toggle(client, Hand.OFF_HAND);
        if (tick == 175) { count("staff_ignite", 1); loops(1); }
        if (tick == 180) toggle(client, Hand.MAIN_HAND);
        if (tick == 195) { count("saber_ignite", 2); loops(2); }
        if (tick == 200) client.getServer().execute(() -> player(client).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY));
        if (tick == 220) { count("saber_retract", 2); count("staff_retract", 0); loops(1); }
        if (tick == 230) toggle(client, Hand.OFF_HAND);
        if (tick == 245) { count("staff_retract", 1); loops(0); }
        if (tick == 250) toggle(client, Hand.OFF_HAND);
        if (tick == 270) { count("staff_ignite", 2); client.reloadResources(); }
        if (tick == 310) { count("staff_ignite", 2); count("saber_catch", 2); loops(1); ClientSettings.get().soundVolume = 0; }
        if (tick == 315) {
            loops(0);
            if (!transients().isEmpty()) throw new AssertionError("Mute must stop recorded tails immediately");
            ClientSettings.get().soundVolume = 1;
        }
        if (tick == 330) { count("staff_ignite", 2); loops(1); }
        if (tick == 335) {
            surfaceBefore = EVENTS.getOrDefault("saber_fire", 0);
            ClientSettings.get().scorchMarks = false;
            client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(), 510.5, 2, 513.65, 0, 0));
        }
        if (tick == 390) {
            int contacts = EVENTS.getOrDefault("saber_fire", 0) - surfaceBefore;
            if (contacts < 1 || contacts > 3) throw new AssertionError("Surface audio missing or not rate-limited with decals disabled: " + contacts);
            client.getServer().execute(() -> player(client).setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY));
        }
        if (tick == 440) {
            count("staff_retract", 2); loops(0);
            if (!transients().isEmpty()) throw new AssertionError("Finished one-shots leaked voices");
            client.getSoundManager().unregisterListener(LISTENER);
            ClientSettings.reset();
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_AUDIO_SMOKE_PASSED events={} shader={}", EVENTS,
                    net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    // SoundInstanceListener gained the audible-distance argument in 1.21.
    public void onSoundPlayed(SoundInstance sound, WeightedSoundSet set) { record(sound); }
    public void onSoundPlayed(SoundInstance sound, WeightedSoundSet set, float distance) { record(sound); }

    private static void record(SoundInstance sound) {
        if (!sound.getId().getNamespace().equals("justifylasers")) return;
        EVENTS.merge(sound.getId().getPath(), 1, Integer::sum);
        LAST.put(sound.getId().getPath(), sound);
        LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_AUDIO_EVENT id={} file={}", sound.getId(), sound.getSound().getIdentifier());
    }

    private static void verifyRecordings(MinecraftClient client) {
        int total = 0;
        for (var entry : Map.of("saber_catch", 1, "saber_ignite", 1, "saber_retract", 1, "staff_ignite", 1, "staff_retract", 1,
                "saber_idle", 5, "saber_swing", 9, "saber_fire", 3).entrySet()) {
            var set = client.getSoundManager().get(JustifyLasers.id(entry.getKey()));
            if (set == null || set.getWeight() != entry.getValue()) throw new AssertionError("Missing sound variants: " + entry.getKey());
            var found = new HashSet<net.minecraft.util.Identifier>();
            var random = Random.create(714);
            for (int sample = 0; sample < 512; sample++) {
                var sound = set.getSound(random);
                if (!found.add(sound.getLocation())) continue;
                try (var input = client.getResourceManager().open(sound.getLocation()); var audio = new OggAudioStream(input)) {
                    if (audio.getFormat().getChannels() != 1 || audio.getFormat().getSampleRate() != 44100)
                        throw new AssertionError("Incorrect native audio format: " + sound.getLocation());
                } catch (java.io.IOException error) { throw new AssertionError(error); }
            }
            if (found.size() != entry.getValue()) throw new AssertionError("Random sound selection omitted variants: " + entry.getKey());
            total += found.size();
        }
        LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_VORBIS_DECODE_PASSED recordings={}", total);
    }

    private static void count(String event, int expected) {
        if (EVENTS.getOrDefault(event, 0) != expected) throw new AssertionError(event + ": expected " + expected + ", got " + EVENTS);
    }

    private static void loops(int expected) {
        try {
            var field = LaserSoundController.class.getDeclaredField("LOOPS"); field.setAccessible(true);
            var loops = (Map<?, ?>)field.get(null);
            if (loops.size() != expected) throw new AssertionError("Expected " + expected + " independent loops, got " + loops.size());
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static java.util.List<?> transients() {
        try {
            var field = LaserSoundController.class.getDeclaredField("TRANSIENTS"); field.setAccessible(true);
            return (java.util.List<?>)field.get(null);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void toggle(MinecraftClient client, Hand hand) {
        client.getServer().execute(() -> {
            if (!SaberCombat.toggle(player(client), hand)) throw new AssertionError("Saber toggle rejected: " + hand);
        });
    }

    private static void equip(MinecraftClient client, Hand hand, boolean staff, boolean active) {
        client.getServer().execute(() -> {
            var stack = new ItemStack(staff ? ModBlocks.LIGHT_STAFF : ModBlocks.LASER_SABER);
            LaserSaberItem.setActive(stack, active);
            player(client).setStackInHand(hand, stack);
        });
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        for (int x = 506; x <= 516; x++) for (int z = 506; z <= 516; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.STONE.getDefaultState());
            for (int y = 2; y <= 8; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        for (int x = 506; x <= 516; x++) for (int y = 2; y <= 7; y++)
            world.setBlockState(new BlockPos(x, y, 514), Blocks.SMOOTH_STONE.getDefaultState());
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE); player.getInventory().clear(); player.getInventory().selectedSlot = 0;
        player.teleport(world, 510.5, 2, 510.5, 0, 0);
    }

    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
}
