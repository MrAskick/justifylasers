package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public final class MirrorGeometry {
    public static final double HALF = 5.1 / 16, CUT = .75 / 16;
    public record Frame(Vec3d normal, Vec3d right, Vec3d up, double yaw, double pitch) { }

    public static Frame frame(Vec3d normal, Direction mount) {
        Vec3d x = mounted(new Vec3d(1, 0, 0), mount), y = mounted(new Vec3d(0, 1, 0), mount), z = mounted(new Vec3d(0, 0, 1), mount);
        Vec3d local = new Vec3d(normal.dotProduct(x), normal.dotProduct(y), normal.dotProduct(z));
        double yaw = Math.atan2(-local.x, local.z), pitch = Math.asin(Math.max(-1, Math.min(1, local.y)));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(local).normalize();
        return new Frame(normal, mounted(right, mount), mounted(up, mount), yaw, pitch);
    }
    public static Vec3d mounted(Vec3d vector, Direction mount) {
        return OpticalGeometry.mounted(vector.add(.5,.5,.5), mount).subtract(.5,.5,.5);
    }
    public static boolean contains(Vec3d relative, Frame frame) {
        double x = Math.abs(relative.dotProduct(frame.right)), y = Math.abs(relative.dotProduct(frame.up));
        return x <= HALF + 1e-8 && y <= HALF + 1e-8 && x + y <= 2 * HALF - CUT + 1e-8;
    }
    public static Vec3d reflectPoint(Vec3d point, Vec3d center, Vec3d normal) {
        return point.subtract(normal.multiply(2 * point.subtract(center).dotProduct(normal)));
    }

    public static VoxelShape supportShape(Vec3d normal, Direction mount) {
        VoxelShape result=modelBox(-6.6,-7.98,-4.6,6.6,-6.65,4.6,0,mount);
        result=VoxelShapes.union(result,modelBox(-2.05,-6.65,-2.05,2.05,-5.5,2.05,0,mount));
        double yaw=Math.PI-frame(normal,mount).yaw();
        for(int sign:new int[]{-1,1}) {
            result=VoxelShapes.union(result,modelBox(sign*6.3,-4.2,-.75,sign*7.1,1.2,.75,yaw,mount));
            result=VoxelShapes.union(result,modelBox(sign*4.75,-6.4,-.75,sign*7.1,-3.85,.75,yaw,mount));
            result=VoxelShapes.union(result,modelBox(sign*3.1,-6.4,-.75,sign*5.2,-5.6,.75,yaw,mount));
        }
        return result;
    }

    private static VoxelShape modelBox(double x1,double y1,double z1,double x2,double y2,double z2,double yaw,Direction mount) {
        double minX=1,minY=1,minZ=1,maxX=0,maxY=0,maxZ=0;
        double c=Math.cos(yaw),s=Math.sin(yaw);
        for(double x:new double[]{x1,x2}) for(double y:new double[]{y1,y2}) for(double z:new double[]{z1,z2}) {
            Vec3d point=mounted(new Vec3d(x*c+z*s,y,-x*s+z*c).multiply(1.0/16),mount).add(.5,.5,.5);
            minX=Math.min(minX,point.x); minY=Math.min(minY,point.y); minZ=Math.min(minZ,point.z);
            maxX=Math.max(maxX,point.x); maxY=Math.max(maxY,point.y); maxZ=Math.max(maxZ,point.z);
        }
        return VoxelShapes.cuboid(new Box(minX,minY,minZ,maxX,maxY,maxZ));
    }
    private MirrorGeometry() { }
}
