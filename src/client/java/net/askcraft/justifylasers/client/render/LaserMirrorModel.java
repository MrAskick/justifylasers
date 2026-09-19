package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.laser.MirrorGeometry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

final class LaserMirrorModel {
    static final OpticalComponentMesh PLATE = plate(), BASE = base(), STAND = stand(), GLASS = glass();

    static void render(LaserOpticBlockEntity optic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        boolean active = optic.getCachedState().get(LaserOpticBlock.LIT);
        var frame = MirrorGeometry.frame(optic.normal(), optic.facing());
        matrices.push();
        switch (optic.facing()) {
            case DOWN -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
            case NORTH -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            case SOUTH -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            case EAST -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90));
            case WEST -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
        }
        BASE.render(matrices, consumers, light, optic.rgb(), active, optic.emitsShaderLight());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(Math.PI - frame.yaw())));
        STAND.render(matrices, consumers, light, optic.rgb(), active, optic.emitsShaderLight());
        matrices.multiply(RotationAxis.POSITIVE_X.rotation((float)frame.pitch()));
        PLATE.render(matrices, consumers, light, optic.rgb(), active, optic.emitsShaderLight());
        GLASS.render(matrices, consumers, light, optic.rgb(), false, false);
        matrices.pop();
    }

    private static Vec3d[] outline(double half, double cut) {
        return new Vec3d[]{new Vec3d(-half,-half+cut,0),new Vec3d(-half,half-cut,0),new Vec3d(-half+cut,half,0),new Vec3d(half-cut,half,0),
                new Vec3d(half,half-cut,0),new Vec3d(half,-half+cut,0),new Vec3d(half-cut,-half,0),new Vec3d(-half+cut,-half,0)};
    }
    private static OpticalComponentMesh plate() {
        var b = new OpticalComponentMesh.Builder("laser_mirror");
        var outer = outline(6.0, 1.35); var inner = outline(5.12, .78); var lip = outline(5.58, 1.08);
        for (int i=0; i<8; i++) {
            int next = (i+1)%8;
            b.prism(i%2==0 ? "steel" : "corner", false, new Vec3d[]{outer[i],inner[i],inner[next],outer[next]},
                    new Vec3d(-6,-6,0), new Vec3d(6,6,0), -.03, .65);
            b.prism("light", true, new Vec3d[]{lip[i],inner[i],inner[next],lip[next]},
                    new Vec3d(-5.6,-5.6,0),new Vec3d(5.6,5.6,0), -.055,-.045);
        }
        for (int sx : new int[]{-1,1}) for (int sy : new int[]{-1,1})
            b.box("hinge", false, sx*4.15-.32, sy*5.62-.32,-.075,sx*4.15+.32,sy*5.62+.32,-.035);
        return b.build();
    }
    private static OpticalComponentMesh glass() {
        var b = new OpticalComponentMesh.Builder("laser_mirror");
        var edge = outline(5.1,.75);
        for (int i=0;i<8;i++) b.surface("mirror",false,Vec3d.ZERO,edge[i],edge[(i+1)%8],Vec3d.ZERO,38);
        return b.build();
    }
    private static OpticalComponentMesh base() {
        var b = new OpticalComponentMesh.Builder("laser_mirror");
        b.bevel("metal", -6.6,-7.98,-4.6,6.6,-6.65,4.6,.38);
        var top = new OpticalComponentMesh.Builder("laser_mirror");
        top.panel("base",false,-6.25,-4.25,6.25,4.25,6.635);
        b.add(top.build(),Direction.UP);
        for(Direction side:new Direction[]{Direction.NORTH,Direction.SOUTH}) {
            var f = new OpticalComponentMesh.Builder("laser_mirror");
            f.panel("base_side",false,-3.0,-7.8,3.0,-6.82,-4.62);
            f.panel("light",true,-2.55,-7.48,2.55,-7.04,-4.64);
            b.add(f.build(),side);
        }
        var pivot = new OpticalComponentMesh.Builder("laser_mirror");
        pivot.profile("hinge",false,new double[]{0,1.55,2.05,2.05},new double[]{5.5,5.5,5.9,6.6},2.05,255);
        pivot.profile("light",true,new double[]{1.55,1.98},new double[]{5.63,5.63},1.98,255);
        b.add(pivot.build(),Direction.UP);
        return b.build();
    }
    private static OpticalComponentMesh stand() {
        var b = new OpticalComponentMesh.Builder("laser_mirror");
        for(int sign:new int[]{-1,1}) {
            var min = new Vec3d(3.1,-6.45,0); var max = new Vec3d(7.1,1.25,0);
            var sections = new Vec3d[][] {
                    {new Vec3d(6.3,1.2,0),new Vec3d(7.1,1.2,0),new Vec3d(7.1,-4.2,0),new Vec3d(6.3,-3.85,0)},
                    {new Vec3d(6.3,-3.85,0),new Vec3d(7.1,-4.2,0),new Vec3d(5.2,-6.4,0),new Vec3d(4.75,-5.6,0)},
                    {new Vec3d(4.75,-5.6,0),new Vec3d(5.2,-6.4,0),new Vec3d(3.1,-6.4,0),new Vec3d(3.1,-5.6,0)} };
            for (var points : sections) {
                if (sign<0) for(int i=0;i<points.length;i++) points[i] = new Vec3d(-points[i].x,points[i].y,0);
                b.prism("arm",false,points,sign<0?new Vec3d(-max.x,min.y,0):min,sign<0?new Vec3d(-min.x,max.y,0):max,-.75,.75);
            }
            var hinge = new OpticalComponentMesh.Builder("laser_mirror");
            hinge.profile("hinge",false,new double[]{0,.65,1,1.05,1.05},new double[]{-7.3,-7.3,-7.1,-6.5,-6},1.05,255);
            hinge.profile("light",true,new double[]{.66,.99},new double[]{-7.24,-7.24},.99,255);
            b.add(hinge.build(),sign<0?Direction.WEST:Direction.EAST);
        }
        return b.build();
    }
    private LaserMirrorModel() { }
}
