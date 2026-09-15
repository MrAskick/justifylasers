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
    public double energyTransmissionEfficiency = 0.8;
    public double laserVolume = 0.65;
    public int maxLaserSoundSources = 8;
    public int laserGunRange = 64;
    public float laserGunDamage = 0.5F;
    public int laserGunHitsPerSecond = 20;
    public double laserGunKnockback = 1;
    public int turretRange = 32;
    public boolean industrialProgression = true;
    public int machineCapacity = 100_000;
    public int machineTransfer = 512;
    public int generatorPerTick = 128;
    public int smelterPerTick = 32;
    public int crystalGrowerPerTick = 64;
    public int assemblyPerTick = 160;
    public int alloySmeltingTicks = 200;
    public int crystalGrowthTicks = 600;
    public int laserAssemblyTicks = 400;
    public int crystalTankCapacity = 8_000;
    public int crystalWaterPerRecipe = 1_000;
    public float saberDamage = 6;
    public float staffDamage = 6.5F;
    public int saberWindupTicks = 3;
    public int saberActiveTicks = 4;
    public int saberRecoveryTicks = 5;
    public int staffWindupTicks = 4;
    public int staffActiveTicks = 5;
    public int staffRecoveryTicks = 6;
    public float saberStamina = 100;
    public float saberAttackCost = 12;
    public float staffAttackCost = 20;
    public float saberBlockCost = 18;
    public float saberParryCost = 4;
    public float saberGuardDrain = .65F;
    public float saberStaminaRegen = 1.25F;
    public int saberParryWindowTicks = 3;
    public int saberGuardBreakTicks = 10;
    public int saberStaggerImmunityTicks = 24;
    public double saberGuardAngle = 110;

    public static LaserConfig get() {
        return current;
    }

    public static boolean technicalMode() {
        return technicalMode;
    }

    public boolean enablesEnergy(Predicate<String> loaded) {
        return energyMode == EnergyMode.ON || energyMode == EnergyMode.AUTO && (industrialProgression || technicalMods.stream().anyMatch(loaded));
    }

    public LaserEnergyCost.Rates rates() {
        return new LaserEnergyCost.Rates(basePerTick, miningPerTick, miningSpeedMultiplier,
                damagePerHealthPoint, knockbackPerHit, ignitionPerHit);
    }

    public void validate() {
        if (crystalTankCapacity < 1_000 || crystalTankCapacity > 64_000 || crystalWaterPerRecipe < 1 || crystalWaterPerRecipe > crystalTankCapacity)
            throw new IllegalArgumentException("Invalid crystal chamber water capacity or consumption");
        if (!Float.isFinite(saberDamage) || saberDamage < 0 || saberDamage > 100 || !Float.isFinite(staffDamage) || staffDamage < 0 || staffDamage > 100
                || saberWindupTicks < 1 || saberWindupTicks > 40 || staffWindupTicks < 1 || staffWindupTicks > 40
                || saberActiveTicks < 1 || saberActiveTicks > 40 || staffActiveTicks < 1 || staffActiveTicks > 40
                || saberRecoveryTicks < 1 || saberRecoveryTicks > 40 || staffRecoveryTicks < 1 || staffRecoveryTicks > 40
                || !Float.isFinite(saberStamina) || saberStamina < 20 || saberStamina > 1000
                || !Float.isFinite(saberAttackCost) || saberAttackCost <= 0 || saberAttackCost > saberStamina
                || !Float.isFinite(staffAttackCost) || staffAttackCost <= 0 || staffAttackCost > saberStamina
                || !Float.isFinite(saberBlockCost) || saberBlockCost <= 0 || saberBlockCost > saberStamina
                || !Float.isFinite(saberParryCost) || saberParryCost <= 0 || saberParryCost > saberBlockCost
                || !Float.isFinite(saberGuardDrain) || saberGuardDrain <= 0 || saberGuardDrain > saberStamina
                || !Float.isFinite(saberStaminaRegen) || saberStaminaRegen <= 0 || saberStaminaRegen > saberStamina
                || saberParryWindowTicks < 1 || saberParryWindowTicks > 6 || saberGuardBreakTicks < 2 || saberGuardBreakTicks > 20
                || saberStaggerImmunityTicks < saberGuardBreakTicks || saberStaggerImmunityTicks > 100
                || !Double.isFinite(saberGuardAngle) || saberGuardAngle < 30 || saberGuardAngle > 160)
            throw new IllegalArgumentException("Invalid saber fencing damage, timings or stamina settings");
        if (machineCapacity < 1 || machineTransfer < 1 || generatorPerTick < 1 || generatorPerTick > machineCapacity
                || smelterPerTick < 1 || smelterPerTick > machineCapacity || crystalGrowerPerTick < 1 || crystalGrowerPerTick > machineCapacity
                || assemblyPerTick < 1 || assemblyPerTick > machineCapacity
                || alloySmeltingTicks < 1 || alloySmeltingTicks > 72_000 || crystalGrowthTicks < 1 || crystalGrowthTicks > 72_000
                || laserAssemblyTicks < 1 || laserAssemblyTicks > 72_000) throw new IllegalArgumentException("Invalid industrial machine rates, capacity or processing times");
        if (laserGunRange < 1 || laserGunRange > 512 || turretRange < 1 || turretRange > 128
                || !Float.isFinite(laserGunDamage) || laserGunDamage < 0 || laserGunDamage > 100
                || laserGunHitsPerSecond < 1 || laserGunHitsPerSecond > 20
                || !Double.isFinite(laserGunKnockback) || laserGunKnockback < 0 || laserGunKnockback > 10) {
            throw new IllegalArgumentException("Invalid laser gun or turret range, damage, hit rate or knockback");
        }
        if (energyMode == null || technicalMods == null || technicalMods.stream().anyMatch(id -> id == null || !id.matches("[a-z][a-z0-9_]{1,63}"))) {
            throw new IllegalArgumentException("Invalid energyMode or technicalMods in justifylasers.json");
        }
        if (capacity < 1 || maxInput < 1) throw new IllegalArgumentException("Energy capacity and maxInput must be positive");
        if (!Double.isFinite(energyTransmissionEfficiency) || energyTransmissionEfficiency <= 0 || energyTransmissionEfficiency >= 1) {
            throw new IllegalArgumentException("energyTransmissionEfficiency must be between 0 and 1 (exclusive)");
        }
        rates();
        if (!Double.isFinite(laserVolume) || laserVolume < 0 || laserVolume > 1 || maxLaserSoundSources < 1 || maxLaserSoundSources > 64) {
            throw new IllegalArgumentException("laserVolume must be in [0, 1] and maxLaserSoundSources in [1, 64]");
        }
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
