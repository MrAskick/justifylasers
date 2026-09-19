package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.laser.MirrorGeometry;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Camera-relative planar view, including the transform from captured depth to virtual world depth. */
public record PlanarReflection(Vec3d eye, Matrix4f view, Matrix4f projection, Matrix4f depthToMain) {
    public static PlanarReflection create(Vec3d eye, Matrix4f view, Matrix4f projection, Vec3d center, Vec3d normal) {
        Vec3d reflectedEye = MirrorGeometry.reflectPoint(eye, center, normal);
        Matrix4f reflectedView = new Matrix4f(view).mul(reflection(normal));
        Vec3d towardsViewer = eye.subtract(center).dotProduct(normal) > 0 ? normal : normal.negate();
        Matrix4f clipped = clip(projection, reflectedView, reflectedEye, center, towardsViewer);
        // Reflection applied twice is identity; only the oblique near plane changes the projected depth.
        Matrix4f depthToMain = new Matrix4f(projection).mul(new Matrix4f(clipped).invert());
        return new PlanarReflection(reflectedEye, reflectedView, clipped, depthToMain);
    }

    public static Matrix4f reflection(Vec3d n) {
        float x=(float)n.x, y=(float)n.y, z=(float)n.z;
        return new Matrix4f().m00(1-2*x*x).m01(-2*x*y).m02(-2*x*z)
                .m10(-2*y*x).m11(1-2*y*y).m12(-2*y*z).m20(-2*z*x).m21(-2*z*y).m22(1-2*z*z);
    }

    /** Objects behind the physical pane must not cover the world reflected in front of it. */
    public static Matrix4f clip(Matrix4f projection, Matrix4f view, Vec3d eye, Vec3d center, Vec3d normal) {
        Vector4f plane = viewPlane(view, eye, center, normal);
        Vector4f q = new Matrix4f(projection).invert().transform(new Vector4f(Math.signum(plane.x),Math.signum(plane.y),1,1));
        plane.mul(2 / plane.dot(q));
        return new Matrix4f(projection).m02(plane.x-projection.m03()).m12(plane.y-projection.m13())
                .m22(plane.z-projection.m23()).m32(plane.w-projection.m33());
    }

    public static Vector4f shaderPlane(Matrix4f projection, Matrix4f view, Vec3d eye, Vec3d center, Vec3d normal) {
        return new Matrix4f(projection).invert().transpose().transform(viewPlane(view, eye, center, normal));
    }

    private static Vector4f viewPlane(Matrix4f view, Vec3d eye, Vec3d center, Vec3d normal) {
        Vector4f plane = new Vector4f((float) normal.x, (float) normal.y, (float) normal.z,
                (float) (normal.dotProduct(eye.subtract(center)) - .006));
        return new Matrix4f(view).invert().transpose().transform(plane);
    }
}
