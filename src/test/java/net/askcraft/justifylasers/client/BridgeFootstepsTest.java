package net.askcraft.justifylasers.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeFootstepsTest {
    @Test void travelCadenceHasSeparateWalkAndSprintLimits() {
        for (boolean sprinting : new boolean[]{false, true}) {
        for (double speed : new double[]{.1, .215, .28, .7, 1.4}) {
            var cadence = new BridgeFootsteps.Cadence();
            int last = -100, sounds = 0;
            for (int tick = 0; tick < 200; tick++) if (cadence.tick(speed, true, sprinting)) {
                assertTrue(tick - last >= (sprinting ? 4 : 8));
                last = tick;
                sounds++;
            }
            assertTrue(sounds > 0 && sounds <= (sprinting ? 50 : 25));
        }
        }
    }

    @Test void normalSprintIsNoticeablyFasterThanWalking() {
        var walk = new BridgeFootsteps.Cadence(); var sprint = new BridgeFootsteps.Cadence();
        int walks=0, sprints=0;
        for (int i=0;i<200;i++) {
            if (walk.tick(.215,true,false)) walks++;
            if (sprint.tick(.28,true,true)) sprints++;
        }
        assertTrue(walks >= 24 && sprints >= walks * 1.5);
    }

    @Test void standingStillOrLeavingTheBridgeDoesNotTriggerPendingSteps() {
        var cadence = new BridgeFootsteps.Cadence();
        assertFalse(cadence.tick(1.4, true, false));
        for (int tick = 0; tick < 30; tick++) assertFalse(cadence.tick(0, true, false));
        assertFalse(cadence.tick(.5, false, false));
        assertFalse(cadence.tick(.5, true, false));
    }
}
