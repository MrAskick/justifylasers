package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaberFencingTest {
    @Test void cameraPolesDoNotInvertTheRollBasis() {
        for (int yaw = -720; yaw <= 720; yaw += 15) {
            var right = SaberPose.right(yaw);
            assertEquals(1, right.length(), 1e-9);
            for (double pitch : new double[]{-90,-89.999,0,89.999,90}) {
                var forward = Vec3d.fromPolar((float)pitch,yaw).normalize();
                assertEquals(0,right.dotProduct(forward),1e-4);
                assertEquals(1,right.crossProduct(forward).length(),1e-6);
            }
        }
    }
    @Test void segmentContactFindsIntersectionsParallelEdgesAndEndpoints() {
        var a = new SaberGeometry.Segment(new Vec3d(-1, 0, 0), new Vec3d(1, 0, 0));
        assertNotNull(SaberGeometry.contact(a, new SaberGeometry.Segment(new Vec3d(0,-1,0),new Vec3d(0,1,0)), .05));
        assertNotNull(SaberGeometry.contact(a, new SaberGeometry.Segment(new Vec3d(-1,.04,0),new Vec3d(1,.04,0)), .05));
        assertNull(SaberGeometry.contact(a, new SaberGeometry.Segment(new Vec3d(-1,.07,0),new Vec3d(1,.07,0)), .05));
        assertNotNull(SaberGeometry.contact(a, new SaberGeometry.Segment(new Vec3d(1,0,0),new Vec3d(2,1,0)), .01));
    }

    @Test void staffGripsStayWithinBothHandsAtEveryCameraAngle() {
        var main = new Vec3d(-.3125, 1.4, 0);
        var support = new Vec3d(.3125, 1.4, 0);
        for (int pitch = -90; pitch <= 90; pitch += 15) for (int yaw = -180; yaw <= 180; yaw += 15) {
            var axis = Vec3d.fromPolar(pitch, yaw);
            var hilt = SaberPose.twoHandedGrip(new Vec3d(-.2, 1, .5), main, support, axis);
            assertEquals(.69, hilt.distanceTo(main), 1e-6);
            assertEquals(.69, hilt.subtract(axis.multiply(.24)).distanceTo(support), 1e-6);
        }
    }

    @Test void frontalGuardHasNoRearOrSideImmunity() {
        var forward = new Vec3d(0,0,1);
        assertTrue(SaberGeometry.faces(forward, new Vec3d(.1,0,1),110));
        assertFalse(SaberGeometry.faces(forward, new Vec3d(0,0,-1),110));
        assertFalse(SaberGeometry.faces(forward, new Vec3d(1,0,0),110));
    }

    @Test void everyDirectedCutHasFiniteContinuousPosesAndANonActiveRecovery() {
        for (boolean staff : new boolean[]{false,true}) for (var cut : SaberCut.values()) for (int hand : new int[]{-1,1}) {
            var state = new SaberState(SaberState.Action.ATTACK,cut,2,100,3,5,6,80,100,1,staff);
            assertFalse(state.activeAt(102.99)); assertTrue(state.activeAt(103)); assertTrue(state.activeAt(107.99)); assertFalse(state.activeAt(108));
            assertEquals(staff && cut == SaberCut.RETURNING ? -1 : 1,state.leadingEnd());
            var previous = SaberPose.combat(state,hand,100,true);
            for (double time=100.01;time<120;time+=.01) {
                var pose = SaberPose.combat(state,hand,time,true);
                assertEquals(1,pose.axis().length(),1e-6);
                assertTrue(pose.axis().distanceTo(previous.axis()) < .1,"No snap between animation phases: " + cut + " at " + time);
                previous=pose;
            }
        }
    }
}
