package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.laser.LaserBeamSource;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserBeamPath;
import net.askcraft.justifylasers.laser.LaserScorchMarks;
import net.askcraft.justifylasers.registry.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LaserSoundController {
    private static final Map<Object, Voice> LOOPS = new HashMap<>();
    private static final Map<Object, Source> WEAPONS = new HashMap<>();
    private static final List<Voice> TRANSIENTS = new ArrayList<>();
    private static final Map<SaberKey, HeldSaber> HELD_SABERS = new HashMap<>();
    private static final Map<Object, Long> NEXT_SURFACE_SOUND = new HashMap<>();
    private static World world;

    public static void weapon(Object key, Vec3d position) {
        if (world != null) WEAPONS.put(key, new Source(key, position, 0.8F, null, world.getTime()));
    }

    public static void saberSwing(Vec3d position, boolean staff) {
        var client = MinecraftClient.getInstance();
        float gain = (float) (LaserConfig.get().laserVolume * ClientSettings.get().soundVolume);
        if (gain > 0) transientSound(client, ModSounds.SABER_SWING, position, gain * 0.9F, staff ? 0.95F : 1, 0,
                Math.min(LaserConfig.get().maxLaserSoundSources, ClientSettings.get().maxSoundSources));
    }

    public static void saberContact(Vec3d position) {
        float gain = (float) (LaserConfig.get().laserVolume * ClientSettings.get().soundVolume);
        if (gain > 0) transientSound(MinecraftClient.getInstance(), ModSounds.LASER_CONTACT, position, gain * 0.75F, 0.88F, 6,
                Math.min(LaserConfig.get().maxLaserSoundSources, ClientSettings.get().maxSoundSources));
    }

    public static void saberClash(Vec3d position, boolean parry) {
        float gain = (float)(LaserConfig.get().laserVolume * ClientSettings.get().soundVolume);
        if (gain > 0) transientSound(MinecraftClient.getInstance(), ModSounds.SABER_CLASH, position, gain, parry ? 1.22F : 0.94F, 8,
                Math.min(LaserConfig.get().maxLaserSoundSources, ClientSettings.get().maxSoundSources));
    }

    public static void tick(MinecraftClient client, List<LaserScorchMarks.WeaponContact> saberContacts) {
        if (client.world != world) {
            LOOPS.values().forEach(client.getSoundManager()::stop);
            TRANSIENTS.forEach(client.getSoundManager()::stop);
            LOOPS.clear();
            WEAPONS.clear();
            TRANSIENTS.clear();
            HELD_SABERS.clear();
            NEXT_SURFACE_SOUND.clear();
            world = client.world;
        }
        if (world == null || client.player == null || client.isPaused()) return;
        TRANSIENTS.removeIf(sound -> sound.isDone() || !client.getSoundManager().isPlaying(sound));
        int limit = Math.min(LaserConfig.get().maxLaserSoundSources, ClientSettings.get().maxSoundSources);
        float gain = (float) (LaserConfig.get().laserVolume * ClientSettings.get().soundVolume);
        if (gain == 0) {
            TRANSIENTS.forEach(client.getSoundManager()::stop);
            TRANSIENTS.clear();
        }
        Vec3d listener = client.player.getEyePos();
        Map<BlockPos, LaserBeamPath> paths = LaserBeamNetwork.paths(world, 1);
        WEAPONS.values().removeIf(source -> world.getTime() - source.seen() > 3);
        Map<Object, Source> sources = new HashMap<>(WEAPONS);
        collectSabers(client, sources, gain, limit);
        paths.forEach((pos, path) -> {
            if (world.getBlockEntity(pos) instanceof LaserBeamSource emitter)
                sources.put(pos, new Source(pos, Vec3d.ofCenter(pos), emitter.getBeamWidthScale(), path, world.getTime()));
        });
        List<Source> candidates = gain == 0 ? List.of() : sources.values().stream()
                .filter(source -> listener.squaredDistanceTo(source.position()) < 32 * 32)
                .sorted(Comparator.comparingDouble(source -> listener.squaredDistanceTo(source.position())
                        - (LOOPS.containsKey(source.key()) ? 16 : 0)))
                .limit(Math.max(1, limit * 3 / 4)).toList();
        Set<Object> wanted = new HashSet<>();
        candidates.forEach(source -> wanted.add(source.key()));
        var iterator = LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (wanted.contains(entry.getKey()) && !entry.getValue().isDone()
                    && client.getSoundManager().isPlaying(entry.getValue())) continue;
            client.getSoundManager().stop(entry.getValue());
            iterator.remove();
            if (gain > 0 && !entry.getValue().saberLoop && !sources.containsKey(entry.getKey())) {
                transientSound(client, ModSounds.LASER_STOP, entry.getValue().position(), gain * 0.65F,
                        entry.getValue().getPitch(), 14, limit);
            }
        }
        float mixedGain = gain / (float) Math.sqrt(Math.max(1, candidates.size()));
        for (Source source : candidates) {
            float width = source.width();
            float pitch = source.saber() ? 1 : MathHelper.clamp((float) Math.pow(width, -0.16), 0.65F, 1.5F);
            float volume = mixedGain * (source.saber() ? 0.4F : MathHelper.clamp(0.7F + width * 0.06F, 0.7F, 1));
            Voice voice = LOOPS.get(source.key());
            if (voice == null && LOOPS.size() + TRANSIENTS.size() < limit) {
                voice = new Voice(source.saber() ? ModSounds.SABER_IDLE : ModSounds.LASER_IDLE, source.position(), volume, pitch, -1);
                LOOPS.put(source.key(), voice);
                client.getSoundManager().play(voice);
                if (!source.saber()) transientSound(client, ModSounds.LASER_START, source.position(), volume, pitch, 14, limit);
            }
            if (voice != null) { voice.adjust(volume, pitch); voice.move(source.position()); }
            if (source.path() != null && Math.floorMod(world.getTime() + source.key().hashCode(), 24) == 0) {
                source.path().segments().stream().filter(ray -> ray.hasBlockHit()
                                && !LaserBeamPath.isOpticalInput(world, world.getBlockState(ray.hitBlock()), ray))
                        .filter(ray -> listener.squaredDistanceTo(ray.end()) < 16 * 16)
                        .min(Comparator.comparingDouble(ray -> listener.squaredDistanceTo(ray.end())))
                        .ifPresent(ray -> transientSound(client, ModSounds.LASER_CONTACT, ray.end(),
                                volume * 0.38F * (float) Math.sqrt(ray.power()), pitch, 10, limit));
            }
        }
        if (gain > 0) for (var contact : saberContacts) {
            var ray = contact.trace();
            if (!ray.hasBlockHit() || ray.hitSide() == null || listener.squaredDistanceTo(ray.end()) >= 16 * 16
                    || world.getTime() < NEXT_SURFACE_SOUND.getOrDefault(contact.source(), Long.MIN_VALUE)) continue;
            transientSound(client, ModSounds.SABER_FIRE, ray.end(), gain * .7F, 1, 0, limit);
            // Both ends of a staff share a contact voice; sustained cutting must not stack a sound every tick.
            NEXT_SURFACE_SOUND.put(contact.source(), world.getTime() + 22);
        }
    }

    private static void collectSabers(MinecraftClient client, Map<Object, Source> sources, float gain, int limit) {
        var present = new HashSet<SaberKey>();
        var players = new HashSet<UUID>();
        for (var player : client.world.getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            for (Hand hand : Hand.values()) {
                var stack = player.getStackInHand(hand);
                if (!(stack.getItem() instanceof LaserSaberItem saber)) continue;
                var key = new SaberKey(player.getUuid(), hand);
                present.add(key);
                players.add(player.getUuid());
                int slot = player == client.player && hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : -1;
                var next = new HeldSaber(saber, slot, LaserSaberItem.active(stack), player.getEyePos());
                var previous = HELD_SABERS.put(key, next);
                boolean drawn = previous == null || previous.item() != saber || previous.slot() != slot;
                if (previous != null && previous.active() && (drawn || !next.active())) retract(client, key, previous, gain, limit);
                if (drawn) transientSound(client, ModSounds.SABER_CATCH, next.position(), gain * .5F, 1, 0, limit);
                if (next.active() && (drawn || !previous.active()))
                    transientSound(client, saber.isStaff() ? ModSounds.STAFF_IGNITE : ModSounds.SABER_IGNITE, next.position(), gain * .85F, 1, 0, limit);
                if (next.active()) sources.put(key, new Source(key, next.position(), saber.isStaff() ? 1.5F : 1.1F, null, world.getTime(), true));
            }
        }
        var iterator = HELD_SABERS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (present.contains(entry.getKey())) continue;
            if (entry.getValue().active()) retract(client, entry.getKey(), entry.getValue(), gain, limit);
            iterator.remove();
        }
        NEXT_SURFACE_SOUND.keySet().retainAll(players);
    }

    private static void retract(MinecraftClient client, SaberKey key, HeldSaber saber, float gain, int limit) {
        var loop = LOOPS.remove(key);
        if (loop != null) client.getSoundManager().stop(loop);
        transientSound(client, saber.item().isStaff() ? ModSounds.STAFF_RETRACT : ModSounds.SABER_RETRACT, saber.position(), gain * .85F, 1, 0, limit);
    }

    private record SaberKey(UUID player, Hand hand) { }
    private record HeldSaber(LaserSaberItem item, int slot, boolean active, Vec3d position) { }

    private record Source(Object key, Vec3d position, float width, LaserBeamPath path, long seen, boolean saber) {
        private Source(Object key, Vec3d position, float width, LaserBeamPath path, long seen) { this(key, position, width, path, seen, false); }
    }

    private static void transientSound(MinecraftClient client, SoundEvent sound, Vec3d position,
                                       float volume, float pitch, int ticks, int limit) {
        if (volume <= 0 || client.player == null || client.player.getEyePos().squaredDistanceTo(position) >= 32 * 32
                || LOOPS.size() + TRANSIENTS.size() >= limit) return;
        Voice voice = new Voice(sound, position, volume, pitch, ticks);
        TRANSIENTS.add(voice);
        client.getSoundManager().play(voice);
    }

    private static final class Voice extends MovingSoundInstance {
        private int remaining;
        private final boolean saberLoop;

        private Voice(SoundEvent sound, Vec3d position, float volume, float pitch, int ticks) {
            super(sound, SoundCategory.BLOCKS, Random.create());
            saberLoop = sound == ModSounds.SABER_IDLE;
            x = position.x;
            y = position.y;
            z = position.z;
            repeat = ticks < 0;
            repeatDelay = 0;
            remaining = ticks;
            adjust(volume, pitch);
        }

        private void adjust(float volume, float pitch) {
            this.volume = volume;
            this.pitch = pitch;
        }
        private Vec3d position() { return new Vec3d(x, y, z); }
        private void move(Vec3d position) { x = position.x; y = position.y; z = position.z; }

        @Override
        public void tick() {
            // A zero lifetime lets recorded one-shots finish naturally instead of truncating their tails.
            if (remaining > 0 && --remaining == 0) setDone();
        }
    }

    private LaserSoundController() {
    }
}
