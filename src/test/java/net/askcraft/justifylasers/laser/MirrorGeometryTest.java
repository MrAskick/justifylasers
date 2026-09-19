package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.client.render.PlanarReflection;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MirrorGeometryTest {
    @Test void paneBasisMatchesYawAndTiltOnEveryMount() {
        for (Direction mount : Direction.values()) for (double yaw : new double[]{-179,-80,0,75,179}) for (double pitch : new double[]{-89,-25,0,35,89}) {
            Vec3d normal = OpticalGeometry.normal(yaw,pitch);
            var frame = MirrorGeometry.frame(normal,mount);
            assertEquals(0,frame.right().dotProduct(normal),1e-8);
            assertEquals(0,frame.up().dotProduct(normal),1e-8);
            assertEquals(1,frame.up().length(),1e-8);
            var matrix = new Matrix4f().rotateY((float)(Math.PI-frame.yaw())).rotateX((float)frame.pitch());
            var p = matrix.transformDirection(new org.joml.Vector3f(0,0,-1));
            assertTrue(MirrorGeometry.mounted(new Vec3d(p),mount).distanceTo(normal)<1e-5, mount+" "+yaw+" "+pitch+" expected="+normal+" actual="+MirrorGeometry.mounted(new Vec3d(p),mount));
        }
    }
    @Test void rectangularApertureExcludesOnlyClippedCorners() {
        var frame = MirrorGeometry.frame(new Vec3d(0,0,1),Direction.UP);
        assertTrue(MirrorGeometry.contains(frame.right().multiply(.30),frame));
        assertTrue(MirrorGeometry.contains(frame.right().multiply(.29).add(frame.up().multiply(.29)),frame));
        assertFalse(MirrorGeometry.contains(frame.right().multiply(.318).add(frame.up().multiply(.318)),frame));
        assertFalse(MirrorGeometry.contains(frame.up().multiply(.33),frame));
    }

    @Test void uShapedStandHasNoInvisibleCentralStem() {
        for(Direction mount:Direction.values()) for(double yaw:new double[]{0,45,90,135,180}) {
            Vec3d normal=MirrorGeometry.mounted(OpticalGeometry.normal(yaw,0),mount);
            var support=MirrorGeometry.supportShape(normal,mount);
            Vec3d through=MirrorGeometry.mounted(new Vec3d(0,-.15,0),mount).add(.5,.5,.5);
            assertTrue(support.getBoundingBoxes().stream().noneMatch(box->box.contains(through)));
            Vec3d base=MirrorGeometry.mounted(new Vec3d(0,-.46,0),mount).add(.5,.5,.5);
            assertTrue(support.getBoundingBoxes().stream().anyMatch(box->box.contains(base)));
        }
    }
    @Test void reflectionIsAnInvolutionAndKeepsThePlaneFixed() {
        Vec3d center = new Vec3d(8,4,-3), eye = new Vec3d(12,7,1), normal = new Vec3d(1,2,-1).normalize();
        Vec3d reflected = MirrorGeometry.reflectPoint(eye,center,normal);
        assertEquals(0,MirrorGeometry.reflectPoint(reflected,center,normal).distanceTo(eye),1e-9);
        assertEquals(0,MirrorGeometry.reflectPoint(center,center,normal).distanceTo(center),1e-9);
        var matrix = PlanarReflection.reflection(normal);
        assertEquals(-1,matrix.determinant(),1e-5);
        assertTrue(new Matrix4f(matrix).mul(matrix).equals(new Matrix4f(),1e-5F));
    }
    @Test void obliqueNearPlaneClipsTheWorldBehindTheMirror() {
        var projection = new Matrix4f().perspective((float)Math.toRadians(70),1.5F,.05F,256);
        // Reflected eye is at z=2, looking towards negative z; glass lies at z=0.
        var clipped = PlanarReflection.clip(projection,new Matrix4f(),new Vec3d(0,0,2),Vec3d.ZERO,new Vec3d(0,0,-1));
        var visible = clipped.transform(new Vector4f(0,0,-3,1));
        var behind = clipped.transform(new Vector4f(0,0,-1,1));
        assertTrue(visible.z >= -visible.w && visible.z <= visible.w);
        assertTrue(behind.z < -behind.w);
        assertEquals(projection.m00(),clipped.m00());
        assertEquals(projection.m11(),clipped.m11());
    }
}
