package net.askcraft.justifylasers.laser;

import com.google.gson.JsonParser;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.industry.SolarExposure;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class LuminousFluxTest {
    @Test void formatsEveryRequestedPrefixWithoutScientificNotation() {
        for (var entry : java.util.Map.of(0L, "0 lm", 999L, "999 lm", 1_000L, "1 klm", 12_340L, "12.34 klm",
                999_999L, "999.99 klm", 1_000_000L, "1 Mlm", 1_800_000_000L, "1.8 Glm", LuminousFlux.MAX, "1000000 Glm").entrySet())
            assertEquals(entry.getValue(), LuminousFlux.format(entry.getKey()));
        assertEquals("0 lm", LuminousFlux.format(-1));
    }

    @Test void splittingAndConversionNeverCreatePowerEvenWithHugeFlux() {
        for (long budget : new long[]{1, 999, 80_000, 656_000, 1_000_000, LuminousFlux.MAX}) {
            for (int branches = 1; branches <= 64; branches++) {
                long share = LuminousFlux.share(budget, 1.0 / branches);
                assertTrue(share * branches <= budget);
                assertTrue(LuminousFlux.toEnergyRate(share, 1000, .8) * (long) branches
                        <= Math.floor(budget / 1000.0 * .8));
            }
        }
        assertEquals(0, LuminousFlux.share(10, Double.NaN));
        assertEquals(10, LuminousFlux.share(10, 2));
    }

    @Test void defaultBalanceKeepsTheCollectorCloseToAnUpgradedEmitter() {
        var config = new LaserConfig();
        int emitter = LuminousFlux.toEnergyRate(LuminousFlux.fromEnergyRate(config.basePerTick + 64 * 8 + 64,
                config.lumensPerEnergyUnit), config.lumensPerEnergyUnit, config.energyTransmissionEfficiency);
        int collector = LuminousFlux.toEnergyRate(config.solarPeakFlux, config.lumensPerEnergyUnit, config.energyTransmissionEfficiency);
        assertEquals(524, emitter);
        assertEquals(384, collector);
        assertTrue(collector > emitter / 2 && collector < emitter * 2);
        assertEquals(30, config.solarPeakFlux / config.smallSolarPeakFlux);
        assertTrue(config.solarPeakFlux > 17 * config.smallSolarPeakFlux);
        assertTrue(config.solarPeakFlux < 34 * config.smallSolarPeakFlux);
        assertTrue(config.smallSolarPeakFlux > config.crystalGrowthFlux);
        config.validate();
        config.lumensPerEnergyUnit = 0;
        assertThrows(IllegalArgumentException.class, config::validate);
        config.lumensPerEnergyUnit = 1000;
        config.solarPeakFlux = Long.MAX_VALUE;
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test void solarCurveIsSmoothMonotonicAndSymmetricWithNoEarlyPlateau() {
        long allPanels = (1L << SolarExposure.PANELS.size()) - 1;
        double previous = -1;
        for (int step = 0; step <= 1000; step++) {
            double y = step / 1000.0, x = Math.sqrt(1 - y * y);
            double morning = SolarExposure.fraction(new Vec3d(x, y, 0), allPanels);
            double evening = SolarExposure.fraction(new Vec3d(-x, y, 0), allPanels);
            assertEquals(morning, evening, 1e-12);
            assertTrue(morning >= previous && morning <= 1);
            if (step > 16) assertTrue(morning > previous);
            previous = morning;
        }
        assertEquals(0, SolarExposure.activity(-1));
        assertEquals(1, SolarExposure.activity(1));
        assertTrue(SolarExposure.activity(.1) < .03);
        assertTrue(SolarExposure.activity(.8) < .95);
        assertEquals(0, SolarExposure.fraction(new Vec3d(0, 1, 0), 0));
        assertEquals(4 / 9.0, SolarExposure.fraction(new Vec3d(0, 1, 0), 15), 1e-12);
    }

    @Test void solarMaterialsOnlyHaveAssemblyRecipesAndReusableSchematics() throws Exception {
        var loader = getClass().getClassLoader();
        for (String id : new String[]{"solar_absorber", "small_solar_concentrator"}) {
            assertNull(loader.getResource("data/justifylasers/recipes/" + id + ".json"));
            try (var stream = Objects.requireNonNull(loader.getResourceAsStream("data/justifylasers/recipes/industry/" + id + ".json"))) {
                var recipe = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals("ASSEMBLY_CHAMBER", recipe.get("machine").getAsString());
                assertEquals(id, recipe.get("blueprint").getAsString());
                assertTrue(recipe.get("ticks").getAsInt() > 0 && recipe.get("energy").getAsInt() > 0);
                assertTrue(recipe.getAsJsonArray("inputs").size() <= 4);
            }
            assertNotNull(loader.getResource("assets/justifylasers/models/item/" + id + "_blueprint.json"));
        }
    }
}
