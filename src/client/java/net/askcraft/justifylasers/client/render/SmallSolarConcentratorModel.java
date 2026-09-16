package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.industry.SolarExposure;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public final class SmallSolarConcentratorModel {
    private static final String MATERIAL="small_solar_concentrator";
    static final OpticalComponentMesh BASE=base();
    static final OpticalComponentMesh DISH=SolarDishModel.build(MATERIAL,5.6,1.25,3.4,.32);
    static final OpticalComponentMesh PORT=SolarDishModel.port(MATERIAL,1.7);
    static final OpticalComponentMesh OUTLET=outlet();

    private static OpticalComponentMesh outlet(){
        var b=new OpticalComponentMesh.Builder(MATERIAL);
        b.bevel("metal",-1.65,-3.8,-7.7,1.65,1.1,-2.7,.28);
        return b.build();
    }

    private static OpticalComponentMesh base(){
        var b=new OpticalComponentMesh.Builder(MATERIAL);
        b.bevel("metal",-4,-8,-4,4,-4.4,4,.4);
        b.bevel("metal",-1.3,-4.39,-1.3,1.3,1.5,1.3,.25);
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})
            b.bevel("armor",x*3.1-.5,-7.85,z*3.1-.5,x*3.1+.5,-4.5,z*3.1+.5,.15);
        for(Direction side:Direction.Type.HORIZONTAL){
            var f=new OpticalComponentMesh.Builder(MATERIAL);
            f.panel("casing",false,-2.5,-7.3,2.5,-5,-4.025);
            f.panel("strip",true,-2.7,-4.9,2.7,-4.55,-4.05);
            for(int sign:new int[]{-1,1})f.panel("strip",true,sign*3.1-.15,-7.15,sign*3.1+.15,-5.1,-4.045);
            b.add(f.build(),side);
        }
        return b.build();
    }

    public static void render(SolarConcentratorBlockEntity source,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light){
        boolean active=source!=null&&source.isBeamActive();
        BASE.render(matrices,consumers,light,0x52DFFF,active,true);
        matrices.push();
        matrices.translate(0,.1,0);
        double tilt=0;
        if(source!=null&&source.getWorld()!=null){
            var sun=SolarExposure.sunDirection(source.getWorld(),delta);
            if(sun.y>0)tilt=Math.toDegrees(Math.atan2(-sun.x,sun.y));
        }
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)tilt));
        DISH.render(matrices,consumers,light,0x52DFFF,active,true);
        matrices.pop();
        matrices.push();
        var side=source==null?Direction.NORTH:source.outputSide();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-side.asRotation()));
        OUTLET.render(matrices,consumers,light,0x52DFFF,active,true);
        matrices.translate(0,0,-.479);
        PORT.render(matrices,consumers,light,SolarConcentratorBlockEntity.BEAM_RGB,active,true);
        matrices.pop();
    }
    private SmallSolarConcentratorModel() { }
}
