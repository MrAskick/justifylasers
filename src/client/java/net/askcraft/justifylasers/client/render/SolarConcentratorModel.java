package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

final class SolarConcentratorModel {
    static final OpticalComponentMesh MESH=base();
    static final double DISH_HEIGHT = 1.85;
    static final OpticalComponentMesh DISH=SolarDishModel.build("solar_concentrator",21,5.4,9,1.1,true);
    static final OpticalComponentMesh PORT=SolarDishModel.port("solar_concentrator",4.6);
    static final OpticalComponentMesh OUTLET=outlet();

    private static OpticalComponentMesh outlet(){
        var b=new OpticalComponentMesh.Builder("solar_concentrator");
        b.bevel("metal",-5.5,-5.5,-39.6,5.5,5.5,-23.9,.6);
        for(int sign:new int[]{-1,1})b.panel("strip",true,-3,sign*4.5-.25,3,sign*4.5+.25,-39.63);
        return b.build();
    }

    private static OpticalComponentMesh base(){
        var b=new OpticalComponentMesh.Builder("solar_concentrator");
        b.bevel("metal",-23.8,-8,-23.8,23.8,-3.4,23.8,.9);
        b.bevel("metal",-11,-3.35,-11,11,8,11,.8);
        for(Direction side:Direction.Type.HORIZONTAL){
            var face=new OpticalComponentMesh.Builder("solar_concentrator");
            for(int n=-1;n<=1;n++){
                double x=n*16;
                face.bevel("metal",x-6.8,-3.3,-23.6,x+6.8,4.1,-22.9,.15);
                face.panel("vent",false,x-4.5,-2.5,x+4.5,2.5,-23.63);
                face.panel("strip",true,x-5.3,3.1,x+5.3,3.7,-23.66);
            }
            face.bevel("armor",-9,-2,-12,9,9,-10.8,.25);
            face.panel("casing",false,-7.7,0,7.7,7.5,-12.03);
            b.add(face.build(),side);
        }
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){
            double X=x*18,Z=z*18;
            b.bevel("armor",X-4.7,-7.95,Z-4.7,X+4.7,-2.9,Z+4.7,.7);
            Vec3d foot=new Vec3d(X,-2.85,Z),top=new Vec3d(0,28.1,z*13.6);
            SolarDishModel.rail(b,"metal",foot,top,2.05);
            SolarDishModel.rail(b,"strip",foot.add(x*2.02,0,z*2.02),top.add(x*2.02,0,z*2.02),.33,true);
        }
        for(int z:new int[]{-1,1}){
            b.bevel("armor",-2.4,26.7,z*13.6-2.4,2.4,29.35,z*13.6+2.4,.4);
            b.bevel("metal",-1.1,28.5,z*9.4-1.6,1.1,29.45,z*9.4+1.6,.15);
        }
        SolarDishModel.rail(b,"metal",new Vec3d(0,29.45,-13.7),new Vec3d(0,29.45,13.7),.55);
        return b.build();
    }

    static float sunTilt(Vec3d sun) {
        return sun.y > 0 ? (float)Math.atan2(-sun.x,sun.y) : 0;
    }

    static Vec3d dishPoint(Vec3d point, float tilt) {
        return new Vec3d(point.x*Math.cos(tilt)-point.y*Math.sin(tilt),
                point.x*Math.sin(tilt)+point.y*Math.cos(tilt)+DISH_HEIGHT,point.z);
    }

    static void render(MatrixStack matrices,VertexConsumerProvider consumers,int light,boolean active,Direction output,float tilt){
        MESH.render(matrices,consumers,light,0x52DFFF,active,true);
        matrices.push();
        matrices.translate(0,DISH_HEIGHT,0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(tilt));
        DISH.render(matrices,consumers,light,0x52DFFF,active,true);
        matrices.pop();
        for(Direction side:Direction.Type.HORIZONTAL){
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-side.asRotation()));
            OUTLET.render(matrices,consumers,light,0x52DFFF,active&&side==output,true);
            matrices.translate(0,0,-2.479);
            PORT.render(matrices,consumers,light,SolarConcentratorBlockEntity.BEAM_RGB,active&&side==output,true);
            matrices.pop();
        }
    }
    private SolarConcentratorModel() { }
}
