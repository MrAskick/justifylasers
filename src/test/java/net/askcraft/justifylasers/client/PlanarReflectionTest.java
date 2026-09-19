package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.render.PlanarReflection;
import net.askcraft.justifylasers.laser.MirrorGeometry;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanarReflectionTest {
    private static Matrix4f projection() { return new Matrix4f().perspective((float)Math.toRadians(70),1.6F,.05F,256); }

    @Test void offAxisCameraProjectsObjectsAtTheirVirtualWorldPositions() {
        Vec3d center=new Vec3d(10,70,-12);
        for(Vec3d normal:new Vec3d[]{new Vec3d(0,0,1),new Vec3d(1,.3,.8).normalize(),new Vec3d(-.2,1,.1).normalize()}) {
            Vec3d right=normal.crossProduct(new Vec3d(0,1,0)).normalize();
            for(double offset:new double[]{-.6,0,.7}) {
                Vec3d eye=center.add(normal.multiply(3)).add(right.multiply(offset));
                Vec3d look=center.subtract(eye);
                var view=new Matrix4f().lookAt(0,0,0,(float)look.x,(float)look.y,(float)look.z,0,1,0);
                var capture=PlanarReflection.create(eye,view,projection(),center,normal);
                for(double distance:new double[]{.05,2,16}) {
                    Vec3d point=center.add(normal.multiply(distance)).add(right.multiply(.4));
                    var actual=project(capture.projection(),capture.view(),point.subtract(capture.eye()));
                    var virtual=project(projection(),view,MirrorGeometry.reflectPoint(point,center,normal).subtract(eye));
                    assertEquals(virtual.x,actual.x,1e-5);
                    assertEquals(virtual.y,actual.y,1e-5);
                    var restored=capture.depthToMain().transform(new Vector4f(actual.x,actual.y,actual.z,1));
                    assertEquals(virtual.z,restored.z/restored.w,1e-5,"Depth must belong to the reflected object, not the glass");
                }
            }
        }
    }

    @Test void lateralParallaxDependsOnObjectDistance() {
        Vec3d normal=new Vec3d(0,0,1);
        var start=PlanarReflection.create(new Vec3d(0,0,2),new Matrix4f(),projection(),Vec3d.ZERO,normal);
        var moved=PlanarReflection.create(new Vec3d(.2,0,2),new Matrix4f(),projection(),Vec3d.ZERO,normal);
        float near=displacement(start,moved,new Vec3d(.4,0,1));
        float far=displacement(start,moved,new Vec3d(.4,0,10));
        assertTrue(near>3.5F*far,"A flat image would move near and far objects together");
    }

    @Test void movingTowardsThePaneEnlargesNearbyReflectedObjectsMore() {
        Vec3d normal=new Vec3d(0,0,1);
        var start=PlanarReflection.create(new Vec3d(0,0,2),new Matrix4f(),projection(),Vec3d.ZERO,normal);
        var close=PlanarReflection.create(new Vec3d(0,0,.5),new Matrix4f(),projection(),Vec3d.ZERO,normal);
        float nearRatio=screenX(close,new Vec3d(.3,0,1))/screenX(start,new Vec3d(.3,0,1));
        float farRatio=screenX(close,new Vec3d(.3,0,10))/screenX(start,new Vec3d(.3,0,10));
        assertEquals(2,nearRatio,1e-5);
        assertTrue(nearRatio>farRatio);
    }

    private static float displacement(PlanarReflection a,PlanarReflection b,Vec3d point) { return Math.abs(screenX(a,point)-screenX(b,point)); }
    private static float screenX(PlanarReflection camera,Vec3d point) { return project(camera.projection(),camera.view(),point.subtract(camera.eye())).x; }
    private static Vector4f project(Matrix4f projection,Matrix4f view,Vec3d point) {
        var projected=new Matrix4f(projection).mul(view).transform(new Vector4f((float)point.x,(float)point.y,(float)point.z,1));
        return projected.div(projected.w);
    }
}
