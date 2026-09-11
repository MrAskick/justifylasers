package net.askcraft.justifylasers.energy;

import net.askcraft.justifylasers.config.LaserConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LaserEnergyTest {
    @Test
    void bufferSimulationLimitsAndOverflowAreSafe() {
        AtomicInteger changed = new AtomicInteger();
        LaserEnergyBuffer buffer = new LaserEnergyBuffer(() -> 1000, () -> 100, changed::incrementAndGet);
        assertEquals(100, buffer.receive(Long.MAX_VALUE, true));
        assertEquals(0, buffer.stored());
        assertEquals(0, changed.get());
        for (int index = 0; index < 10; index++) assertEquals(100, buffer.receive(Long.MAX_VALUE, false));
        assertEquals(0, buffer.receive(100, false));
        assertEquals(0, buffer.receive(-1, false));
        assertFalse(buffer.consume(1001));
        assertFalse(buffer.consume(-1));
        assertTrue(buffer.consume(1000));
        assertEquals(0, buffer.stored());
        buffer.restore(Long.MAX_VALUE);
        assertEquals(1000, buffer.stored());
        buffer.restore(Long.MIN_VALUE);
        assertEquals(0, buffer.stored());
    }

    @Test
    void fasterMiningAndHigherDamageIncreaseConsumption() {
        LaserEnergyCost.Rates rates = new LaserConfig().rates();
        assertEquals(80, cost(rates, false, 100, false, 20, 20, 10, true));
        int previous = 0;
        for (int speed = 0; speed <= 100; speed++) {
            int current = cost(rates, true, speed, false, 0, 1, 0, false);
            assertTrue(current >= previous);
            previous = current;
        }
        assertEquals(2480, previous);
        int baseline = cost(rates, false, 0, true, 0.5, 20, 1, false);
        assertTrue(cost(rates, false, 0, true, 1, 20, 1, false) > baseline);
        assertTrue(cost(rates, false, 0, true, 0.5, 20, 2, false) > baseline);
        assertTrue(cost(rates, false, 0, true, 0.5, 20, 1, true) > baseline);
        assertTrue(cost(rates, false, 0, true, 0.5, 10, 1, false) < baseline);
        assertEquals(cost(rates, false, 0, true, 1, 10, 0, false),
                cost(rates, false, 0, true, 0.5, 20, 0, false));
    }

    @Test
    void moduleCostAddsToOperatingCostWithoutOverflow() {
        LaserEnergyCost.Rates rates = new LaserConfig().rates();
        for (int count = 0; count <= 64; count++) {
            assertEquals(80 + count, LaserEnergyCost.perTick(rates, false, 0, false, 0, 20, 0, false, count));
            assertEquals(80 + 9 * count, LaserEnergyCost.perTick(rates, false, 0, false, 0, 20, 0, false, 8L * count + count));
            assertEquals(cost(rates, true, 100, true, 20, 20, 10, true) + 9 * count,
                    LaserEnergyCost.perTick(rates, true, 100, true, 20, 20, 10, true, 9L * count));
        }
        assertEquals(Integer.MAX_VALUE, LaserEnergyCost.perTick(rates, false, 0, false, 0, 20, 0, false, Long.MAX_VALUE));
        assertEquals(80, LaserEnergyCost.perTick(rates, false, 0, false, 0, 20, 0, false, -1));
    }

    @Test
    void technicalModeRequiresAConfiguredModUnlessExplicitlyEnabled() {
        LaserConfig config = new LaserConfig();
        assertFalse(config.enablesEnergy(id -> false));
        assertTrue(config.enablesEnergy("mekanism"::equals));
        assertFalse(config.enablesEnergy("unrelated_mod"::equals));
        config.energyMode = LaserConfig.EnergyMode.ON;
        assertTrue(config.enablesEnergy(id -> false));
        config.energyMode = LaserConfig.EnergyMode.OFF;
        assertFalse(config.enablesEnergy(id -> true));
    }

    @Test
    void invalidCostsAreRejectedRatherThanProducingFreeEnergy() {
        LaserConfig config = new LaserConfig();
        config.miningSpeedMultiplier = Double.NaN;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.miningSpeedMultiplier = 20;
        config.basePerTick = 0;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.basePerTick = Integer.MAX_VALUE;
        config.miningPerTick = Integer.MAX_VALUE;
        config.validate();
        assertEquals(Integer.MAX_VALUE, cost(config.rates(), true, 100, true, 20, 20, 10, true));
    }

    private static int cost(LaserEnergyCost.Rates rates, boolean mining, int speed, boolean damage,
                            double hitDamage, int rate, double knockback, boolean ignition) {
        return LaserEnergyCost.perTick(rates, mining, speed, damage, hitDamage, rate, knockback, ignition, 0);
    }
}
