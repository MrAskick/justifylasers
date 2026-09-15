package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

final class LaserMirrorModel {
    static final OpticalComponentMesh PLATE=plate();
    static final Map<Direction,OpticalComponentMesh> MOUNTS=mounts();

    static void render(LaserOpticBlockEntity optic, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        boolean active=optic.getCachedState().get(LaserOpticBlock.LIT);
        MOUNTS.get(optic.facing()).render(matrices,consumers,light,optic.rgb(),active,optic.emitsShaderLight());
        matrices.push();
        try {
            Vec3d normal=optic.normal();
            matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0,0,-1),new Vector3f((float)normal.x,(float)normal.y,(float)normal.z)));
            PLATE.render(matrices,consumers,light,optic.rgb(),active,optic.emitsShaderLight());
        } finally { matrices.pop(); }
    }

    private static OpticalComponentMesh plate() {
        var b=new OpticalComponentMesh.Builder("laser_mirror");
        // The reflective face stays on the existing optical plane through the block center.
        b.profile("mirror",false,new double[]{0,5.11},new double[]{0,0},5.11,255);
        b.profile("ring",true,new double[]{5.12,5.31,5.63,5.76,5.76},new double[]{-0.025,-0.27,-0.27,-0.04,0.73},5.76,255);
        var back=new OpticalComponentMesh.Builder("laser_mirror");
        back.profile("back",true,new double[]{0,5.76},new double[]{-0.74,-0.74},5.76,255);
        b.add(back.build(),Direction.SOUTH);
        for(Direction side:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var lug=new OpticalComponentMesh.Builder("laser_mirror");
            lug.box("stud",true,-0.48,5.05,-0.36,0.48,5.82,0.14);
            // Rotate the clamp in the mirror plane without rotating its UV coordinates.
            double angle=side.getHorizontal()*Math.PI/2;
            addRotated(b,lug.build(),angle);
        }
        return b.build();
    }

    private static void addRotated(OpticalComponentMesh.Builder builder,OpticalComponentMesh mesh,double angle) {
        double c=Math.cos(angle),s=Math.sin(angle);
        java.util.function.UnaryOperator<Vec3d> turn=p->new Vec3d(p.x*c-p.y*s,p.x*s+p.y*c,p.z);
        var faces=mesh.faces().stream().map(f->new OpticalComponentMesh.Face(turn.apply(f.a()),turn.apply(f.b()),turn.apply(f.c()),turn.apply(f.d()),turn.apply(f.normal()),
                f.ua(),f.ub(),f.uc(),f.ud(),f.glowing(),f.alpha())).toList();
        builder.add(new OpticalComponentMesh("laser_mirror",faces),Direction.NORTH);
    }

    private static Map<Direction,OpticalComponentMesh> mounts() {
        var b=new OpticalComponentMesh.Builder("laser_mirror");
        b.bevel("metal",-3.52,-7.9,-3.52,3.52,-6.22,3.52,0.22);
        var top=new OpticalComponentMesh.Builder("laser_mirror");
        top.panel("base",true,-3.26,-3.26,3.26,3.26,6.205);
        b.add(top.build(),Direction.UP);
        for(Direction side:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            var edge=new OpticalComponentMesh.Builder("laser_mirror");
            edge.box("base_side",true,-2.68,-7.56,-3.535,2.68,-6.57,-3.515);
            b.add(edge.build(),side);
        }
        for(int x:new int[]{-1,1}) for(int z:new int[]{-1,1}) {
            b.bevel("corner",x*3.05-0.51,-7.87,z*3.05-0.51,x*3.05+0.51,-6.18,z*3.05+0.51,0.16);
        }
        var bearing=new OpticalComponentMesh.Builder("laser_mirror");
        bearing.profile("collar",true,new double[]{0,1.35,1.73,1.73},new double[]{5.6,5.6,5.77,6.2},1.73,255);
        b.add(bearing.build(),Direction.UP);
        // Keep the support outside the reflective aperture, including when the disc tilts.
        for(int sign:new int[]{-1,1}) {
            b.bevel("metal",sign*6.0-0.43,-4.8,-0.65,sign*6.0+0.43,0.25,0.65,0.15);
            b.box("arm",true,sign*6.0-0.3,-4.6,-0.67,sign*6.0+0.3,-0.8,-0.651);
            b.bevel("steel",sign*4.8-1.6,-6.3,-0.8,sign*4.8+1.6,-4.79,0.8,0.15);
            b.box("hinge",true,sign*6.0-0.66,-0.78,-0.81,sign*6.0+0.66,0.78,0.81);
        }
        Map<Direction,OpticalComponentMesh> result=new EnumMap<>(Direction.class);
        // Canonical support points down; face orientation points out of the mounting surface.
        var canonical=new OpticalComponentMesh.Builder("laser_mirror");
        canonical.add(b.build(),Direction.DOWN);
        for(Direction face:Direction.values()) {
            var oriented=new OpticalComponentMesh.Builder("laser_mirror");
            oriented.add(canonical.build(),face);
            result.put(face,oriented.build());
        }
        return Map.copyOf(result);
    }

    private LaserMirrorModel() { }
}
