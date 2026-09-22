package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;

final class PrintingMachineModel {
    static final OpticalComponentMesh ENCODER=encoder(), CASING=casing(), FRAME=frame(), HEAD=head(), BED=bed();
    private static OpticalComponentMesh encoder() {
        var b=new OpticalComponentMesh.Builder("fuel_generator");
        b.bevel("armor",-7.6,-8,-7.6,7.6,-2,7.6,.45);
        b.bevel("metal",-6.7,-1.97,-3.2,6.7,7.2,6.8,.36);
        b.bevel("armor",-7.15,6.4,-3.6,7.15,7.8,7.1,.24);
        b.panel("screen",false,-5.95,-.9,5.95,5.6,-3.24);
        b.panel("light",true,-5.5,4.7,5.5,5.05,-3.28);
        for(int row=0;row<3;row++) {
            b.panel("light",true,-5.2,.2+row*1.25,-1.9,.48+row*1.25,-3.28);
            b.panel("metal",false,-1.5,.2+row*1.25,3.8,.45+row*1.25,-3.28);
        }
        b.bevel("metal",-6.8,-2,-7.1,6.8,-1.35,-3.6,.1);
        b.bevel("armor",-4.2,-1.31,-6.8,4.2,-.9,-4.4,.06);
        b.panel("screen",false,-4.8,-6,4.8,-3.8,-7.64);
        b.panel("light",true,-4.1,-5.8,4.1,-5.4,-7.67);
        for(Direction side:new Direction[]{Direction.EAST,Direction.WEST,Direction.SOUTH}) {
            var f=new OpticalComponentMesh.Builder("fuel_generator");
            f.panel("vent",false,-5.4,-6.2,5.4,-3.2,-7.64); b.add(f.build(),side);
        }
        return b.build();
    }
    private static OpticalComponentMesh casing() {
        var b=new OpticalComponentMesh.Builder("photopolymer_printer");
        b.add(ChamberModel.chassis("photopolymer_printer",false,true),Direction.NORTH);
        b.bevel("metal",-4,-4,-7.48,4,4,-7.1,.1);
        b.panel("dark",false,-3.5,-3.5,3.5,3.5,-7.51);
        for(double y:new double[]{-2.5,0,2.5}) b.panel("light",true,-2.6,y-.3,2.6,y+.3,-7.54);
        return b.build();
    }
    private static OpticalComponentMesh frame() {
        var b=new OpticalComponentMesh.Builder("photopolymer_printer");
        b.add(ChamberModel.chassis("photopolymer_printer",false,false),Direction.NORTH);
        for(double x:new double[]{-10.5,10.5}) {
            b.bevel("metal",x-.36,-8,-9.8,x+.36,10.4,9.8,.12);
            b.bevel("armor",x-1,8.8,-10.1,x+1,11.1,10.1,.15);
        }
        for(double z:new double[]{-7,7}) {
            b.bevel("armor",-9.7,-9.8,z-1.3,9.7,-5.4,z+1.3,.2);
            b.panel("light",true,-8,-8.8,8,-8.3,z-1.34);
        }
        for(double x:new double[]{-9,9}) b.bevel("metal",x-.6,-9.8,-6,x+.6,-5.4,6,.15);
        return b.build();
    }
    private static OpticalComponentMesh bed() {
        var b=new OpticalComponentMesh.Builder("photopolymer_printer");
        b.bevel("metal",-7.9,-.8,-5.9,7.9,0,5.9,.12);
        return b.build();
    }
    private static OpticalComponentMesh head() {
        var b=new OpticalComponentMesh.Builder("photopolymer_printer");
        b.bevel("armor",-10.3,6.2,-.8,10.3,8.7,.8,.16);
        b.bevel("metal",-2.2,2.8,-2,2.2,6.16,2,.18);
        var lens=new OpticalComponentMesh.Builder("photopolymer_printer");
        lens.panel("light",true,-1,-1,1,1,2.78); b.add(lens.build(),Direction.DOWN);
        return b.build();
    }
    static void render(IndustrialMachineBlockEntity machine,float delta,MatrixStack matrices,VertexConsumerProvider buffers,int light,int overlay) {
        boolean working=machine.status()==IndustrialMachineBlockEntity.Status.WORKING;
        FRAME.render(matrices,buffers,light,0xBD76FF,working,true);
        double progress=Math.min(1,machine.progress()/(double)Math.max(1,machine.duration()));
        double surface=-.53;
        matrices.push(); matrices.translate(0,surface,0); BED.render(matrices,buffers,light,0xFFFFFF,false,false); matrices.pop();
        matrices.push(); matrices.translate(0,0,headZ(machine,delta)); HEAD.render(matrices,buffers,light,0xBD76FF,working,true); matrices.pop();
        var model=machine.printModel();
        var completed=net.askcraft.justifylasers.printing.PrintData.read(machine.getStack(IndustrialMachineBlockEntity.OUTPUT));
        if(progress==0&&completed!=null){model=completed;progress=1;}
        if(model!=null && progress>0) {
            matrices.push(); matrices.translate(-.35,surface+.004,-.35); matrices.scale(.7F,.7F,.7F);
            PrintedModelRenderer.render(model,Direction.NORTH,matrices,buffers,working?net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE:light,overlay,Math.floor(progress*16),false); matrices.pop();
        }
        ChamberModel.resin(matrices,buffers,light,machine.water()/(float)machine.tankCapacity());
        ChamberModel.glass(matrices,buffers,light);
    }
    static double headZ(IndustrialMachineBlockEntity machine,float delta) {
        return machine.status()==IndustrialMachineBlockEntity.Status.WORKING?Math.sin((machine.getWorld().getTime()+delta)*.12)*.33:.34;
    }
    private PrintingMachineModel() { }
}
