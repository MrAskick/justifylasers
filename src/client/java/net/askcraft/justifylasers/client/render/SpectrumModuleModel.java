package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

final class SpectrumModuleModel {
    private static final String MATERIAL = "spectrum_module";
    static final OpticalComponentMesh FRAME = frame(), PRISM = prism();
    static final List<OpticalComponentMesh> DIAL = dial();
    private static final int[] COLORS = {0xFF3434,0xFF7022,0xFFD52D,0xDDFF33,0x66FF36,0x26FFBA,0x24E9FF,0x2695FF,0x5442FF,0x9D38FF,0xE53CFF,0xFF3EB0};

    static void render(ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(.5,.5,.5);
        if (mode == ModelTransformationMode.GUI) matrices.scale(.82F,.82F,.82F);
        FRAME.render(matrices, consumers, light, 0x45DDFF, true, true);
        PRISM.render(matrices, consumers, light, 0xFFFFFF, true, true);
        for (int i=0;i<DIAL.size();i++) DIAL.get(i).render(matrices, consumers, light, COLORS[i], true, true);
        matrices.pop();
    }

    private static OpticalComponentMesh frame() {
        var b = new OpticalComponentMesh.Builder(MATERIAL);
        for (int sx : new int[]{-1,1}) for (int sz : new int[]{-1,1}) {
            double x=sx*5.55,z=sz*5.55;
            b.bevel("metal",x-.7,-5.9,z-.7,x+.7,4.2,z+.7,.13);
            for (double y : new double[]{-8,3.7}) b.bevel("dark",x-1.45,y,z-1.45,x+1.45,y+2.8,z+1.45,.28);
        }
        for (Direction side : Direction.Type.HORIZONTAL) {
            var face = new OpticalComponentMesh.Builder(MATERIAL);
            for (double y : new double[]{-7.1,4.3}) face.bevel("metal",-4,y,-6.2,4,y+1.1,-5.3,.13);
            face.panel("light",true,-3.7,-6.25,3.7,-5.99,-6.24);
            face.panel("light",true,-3.7,4,3.7,4.24,-6.24);
            for (int sign : new int[]{-1,1}) face.panel("light",true,sign*5.55-.16,-4.7,sign*5.55+.16,2.9,-6.29);
            face.profile("metal",false,new double[]{0,2.3,2.3,1.9,1.6,1.6},new double[]{-5.3,-5.3,-6.7,-7.3,-7.3,-7.7},2.3,255);
            face.profile("prism",true,new double[]{0,1.53},new double[]{-7.72,-7.72},1.6,255);
            face.surface("glass",false,new Vec3d(-4.5,-5.9,-5.65),new Vec3d(-4.5,3.9,-5.65),
                    new Vec3d(4.5,3.9,-5.65),new Vec3d(4.5,-5.9,-5.65),62);
            b.add(face.build(),side);
        }
        b.bevel("dark",-4.9,-7.8,-4.9,4.9,-7.2,4.9,.2);
        var top = new OpticalComponentMesh.Builder(MATERIAL);
        top.profile("metal",false,new double[]{0,4.8,5.1,5.1,4.7,0},new double[]{-5.3,-5.3,-5.7,-6.5,-6.75,-6.75},5.1,255);
        top.profile("dark",false,new double[]{3,4.5},new double[]{-6.78,-6.78},4.5,255);
        top.profile("metal",false,new double[]{0,2.65,2.8,2.8,2.5,0},new double[]{-6.79,-6.79,-7,-7.6,-7.85,-7.85},2.8,255);
        b.add(top.build(),Direction.UP);
        return b.build();
    }

    private static OpticalComponentMesh prism() {
        var b=new OpticalComponentMesh.Builder(MATERIAL);
        var a=new Vec3d(-3.6,-4,-2.7); var c=new Vec3d(3.6,-4,-2.7); var d=new Vec3d(0,-4,3.6);
        var points=new Vec3d[]{a,c,d};
        for (int i=0;i<3;i++) {
            var p=points[i]; var q=points[(i+1)%3];
            b.surface("prism",true,p,p.add(0,7.2,0),q.add(0,7.2,0),q,255);
        }
        b.surface("prism",true,a.add(0,7.2,0),d.add(0,7.2,0),c.add(0,7.2,0),c.add(0,7.2,0),255);
        b.surface("prism",true,a,c,d,d,255);
        return b.build();
    }

    private static List<OpticalComponentMesh> dial() {
        var result=new ArrayList<OpticalComponentMesh>();
        for(int i=0;i<12;i++) {
            double start=i*Math.PI/6+.024,end=(i+1)*Math.PI/6-.024;
            var b=new OpticalComponentMesh.Builder(MATERIAL);
            b.surface("light",true,ring(3.05,start),ring(4.43,start),ring(4.43,end),ring(3.05,end),255);
            result.add(b.build());
        }
        return List.copyOf(result);
    }
    private static Vec3d ring(double r,double a) { return new Vec3d(r*Math.sin(a),6.81,r*Math.cos(a)); }
    private SpectrumModuleModel() { }
}
