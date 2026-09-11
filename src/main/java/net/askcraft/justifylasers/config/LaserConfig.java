package net.askcraft.justifylasers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.askcraft.justifylasers.energy.LaserEnergyCost;
import net.askcraft.justifylasers.platform.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Predicate;

public final class LaserConfig {
    public enum EnergyMode { AUTO, ON, OFF }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static LaserConfig current = new LaserConfig();
    private static boolean technicalMode;

    public EnergyMode energyMode = EnergyMode.AUTO;
    public List<String> technicalMods = List.of("mekanism", "techreborn", "modern_industrialization", "powah", "thermal", "oritech");
    public int capacity = 2_000_000;
    public int maxInput = 100_000;
    public int basePerTick = 80;
    public int miningPerTick = 120;
    public double miningSpeedMultiplier = 20;
    public double damagePerHealthPoint = 40;
    public double knockbackPerHit = 8;
    public double ignitionPerHit = 10;

    public static LaserConfig get() {
        return current;
    }

    public static boolean technicalMode() {
        return technicalMode;
    }

    public boolean enablesEnergy(Predicate<String> loaded) {
        return energyMode == EnergyMode.ON || energyMode == EnergyMode.AUTO && technicalMods.stream().anyMatch(loaded);
    }

    public LaserEnergyCost.Rates rates() {
        return new LaserEnergyCost.Rates(basePerTick, miningPerTick, miningSpeedMultiplier,
                damagePerHealthPoint, knockbackPerHit, ignitionPerHit);
    }

    public void validate() {
        if (energyMode == null || technicalMods == null || technicalMods.stream().anyMatch(id -> id == null || !id.matches("[a-z][a-z0-9_]{1,63}"))) {
            throw new IllegalArgumentException("Invalid energyMode or technicalMods in justifylasers.json");
        }
        if (capacity < 1 || maxInput < 1) throw new IllegalArgumentException("Energy capacity and maxInput must be positive");
        rates();
    }

    public static void initialize() {
        Path path = Platform.configDirectory().resolve("justifylasers.json");
        try {
            if (Files.exists(path)) {
                LaserConfig loaded = JSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), LaserConfig.class);
                if (loaded == null) throw new IllegalArgumentException("Config cannot be empty");
                loaded.validate();
                current = loaded;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, JSON.toJson(current) + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            technicalMode = current.enablesEnergy(Platform::isModLoaded);
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load laser configuration: " + path + ". The existing file was not overwritten.", exception);
        }
    }

    public static void applyServerMode(boolean enabled) {
        technicalMode = enabled;
    }

    public static void resetServerMode() {
        technicalMode = current.enablesEnergy(Platform::isModLoaded);
    }
}
