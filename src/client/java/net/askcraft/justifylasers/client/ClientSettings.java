package net.askcraft.justifylasers.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.platform.Platform;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Local presentation preferences, deliberately separate from server gameplay configuration. */
public final class ClientSettings {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientSettings current = new ClientSettings();
    private static Path file;

    public boolean cubeLenses = true;
    public double cubeMagnification = 1.8;
    public int cubeLensDistance = 64;
    public int maxLensCubes = 64;
    public boolean scopeLens = true;
    public boolean scorchMarks = true;
    public int scorchDistance = 96;
    public boolean weaponSway = true;
    public boolean saberSparks = true;
    public boolean solarLightShafts = true;
    public double soundVolume = 1;
    public int maxSoundSources = 8;

    public static ClientSettings get() { return current; }
    public static void reset() { current = new ClientSettings(); }

    public static void initialize() { load(Platform.configDirectory().resolve("justifylasers-client.json")); }

    public static void load(Path path) {
        file = path;
        current = new ClientSettings();
        if (!Files.exists(path)) return;
        try {
            ClientSettings loaded = JSON.fromJson(Files.readString(path), ClientSettings.class);
            if (loaded == null) throw new JsonParseException("Empty client settings");
            loaded.normalize();
            current = loaded;
        } catch (IOException | JsonParseException exception) {
            JustifyLasers.LOGGER.warn("Cannot read client settings {}; using defaults", path, exception);
            // Preserve malformed input before the settings screen can save over it.
            try { Files.copy(path, path.resolveSibling(path.getFileName() + ".invalid"), StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException backupError) { JustifyLasers.LOGGER.warn("Cannot back up invalid client settings", backupError); }
        }
    }

    public void normalize() {
        cubeMagnification = finite(cubeMagnification, 1, 3, 1.8);
        cubeLensDistance = Math.max(8, Math.min(128, cubeLensDistance));
        maxLensCubes = Math.max(1, Math.min(64, maxLensCubes));
        scorchDistance = Math.max(16, Math.min(128, scorchDistance));
        soundVolume = finite(soundVolume, 0, 1, 1);
        maxSoundSources = Math.max(1, Math.min(64, maxSoundSources));
    }

    private static double finite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public static boolean save() {
        if (file == null) return false;
        current.normalize();
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "justifylasers-client-", ".tmp");
            Files.writeString(temporary, JSON.toJson(current) + System.lineSeparator());
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException exception) {
            JustifyLasers.LOGGER.warn("Cannot save client settings {}", file, exception);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException exception) { JustifyLasers.LOGGER.debug("Cannot remove settings temporary file", exception); }
        }
    }
}
