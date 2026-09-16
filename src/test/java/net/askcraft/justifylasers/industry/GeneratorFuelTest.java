package net.askcraft.justifylasers.industry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorFuelTest {
    @Test void lavaPeakIsNet128WithNinetyFivePercentEfficiency() {
        assertEquals(95, GeneratorFuel.LAVA.efficiencyAt(12_000));
        assertEquals(128, GeneratorFuel.LAVA.outputNumerator(12_000,128)/GeneratorFuel.OUTPUT_DIVISOR);
        assertEquals(0, GeneratorFuel.LAVA.outputNumerator(200,128));
    }

    @Test void coldToLavaPeakTakesFiveMinutesAndCooldownIsThreeTimesFaster() {
        int temperature=200;
        for(int tick=0;tick<5900;tick++)temperature=GeneratorFuel.LAVA.approach(temperature,2,true);
        assertEquals(12_000,temperature);
        assertEquals(11994,GeneratorFuel.LAVA.approach(temperature,2,false));
        for(int tick=0;tick<1967;tick++)temperature=GeneratorFuel.LAVA.approach(temperature,2,false);
        assertEquals(200,temperature);
    }

    @Test void weakerFuelCannotReuseLavaTemperatureAsFullPower() {
        assertEquals(3_000,GeneratorFuel.KINDLING.maxTemperature());
        assertEquals(45,GeneratorFuel.KINDLING.efficiencyAt(12_000));
        assertEquals(14,GeneratorFuel.KINDLING.outputNumerator(12_000,128)/GeneratorFuel.OUTPUT_DIVISOR);
        assertEquals(11994,GeneratorFuel.KINDLING.approach(12_000,2,true));
    }

    @Test void outputAndEfficiencyRiseMonotonicallyWithoutExceedingTheFuelLimit() {
        for(var fuel:new GeneratorFuel[]{GeneratorFuel.LAVA,GeneratorFuel.BLAZE,GeneratorFuel.COAL,GeneratorFuel.CHARCOAL,
                GeneratorFuel.WOOD,GeneratorFuel.KINDLING,GeneratorFuel.PLANT,GeneratorFuel.WOOL,GeneratorFuel.KELP}) {
            long previous=0;
            for(int temperature=200;temperature<=12_000;temperature++) {
                long output=fuel.outputNumerator(temperature,128);
                assertTrue(output>=previous && output<=128*GeneratorFuel.OUTPUT_DIVISOR);
                assertTrue(fuel.efficiencyAt(temperature)<=fuel.efficiency());
                previous=output;
            }
        }
    }
}
