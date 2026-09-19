package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

final class FuelGeneratorModel {
    static final OpticalComponentMesh MESH=build();
    private static OpticalComponentMesh build(){
        var b=new OpticalComponentMesh.Builder("fuel_generator");
        b.bevel("metal",-7.4,-7.85,-7.4,7.4,6.65,7.4,.4);
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){
            double X=x*6.7,Z=z*6.7;
            b.bevel("armor",X-.75,-5.65,Z-.75,X+.75,4.7,Z+.75,.2);
            for(double y:new double[]{-7.9,4.85}){
                double height=y<0?2.05:3.05;
                b.bevel("metal",X-1.2,y,Z-1.2,X+1.2,y+height,Z+1.2,.25);
                if(y>0)SolarDishModel.upPanel(b,"light",true,X,Z,y+height+.025,.55);
                for(Direction side:new Direction[]{x<0?Direction.WEST:Direction.EAST,z<0?Direction.NORTH:Direction.SOUTH}){
                    var cap=new OpticalComponentMesh.Builder("fuel_generator");
                    double across=side.getAxis()==Direction.Axis.X?Z:X;
                    if(side==Direction.SOUTH||side==Direction.WEST)across=-across;
                    cap.panel(y>0?"light":"metal",y>0,across-.5,y+.65,across+.5,y+height-.65,-7.925);
                    b.add(cap.build(),side);
                }
            }
            SolarDishModel.rail(b,"metal",new net.minecraft.util.math.Vec3d(x*2.25,6.95,z*2.25),
                    new net.minecraft.util.math.Vec3d(x*5.15,6.95,z*5.15),.38);
        }
        for(Direction side:Direction.Type.HORIZONTAL){
            var face=new OpticalComponentMesh.Builder("fuel_generator");
            face.bevel("armor",-5.45,4.85,-7.85,5.45,7.3,-4.9,.22);
            face.bevel("armor",-5.45,-7.8,-7.85,5.45,-5.95,-6.9,.18);
            face.panel("grille",false,-4.8,-7.4,4.8,-6.4,-7.875);
            face.surface("grille",false,new net.minecraft.util.math.Vec3d(-3.9,7.325,-6.9),new net.minecraft.util.math.Vec3d(-3.9,7.325,-5.5),
                    new net.minecraft.util.math.Vec3d(3.9,7.325,-5.5),new net.minecraft.util.math.Vec3d(3.9,7.325,-6.9),255);
            for(int sign:new int[]{-1,1}){
                double x=sign*5.5;
                face.bevel("armor",x-.4,-5.8,-7.92,x+.4,4.6,-7.2,.1);
                for(double y:new double[]{-4.85,3.55}){
                    face.panel("metal",false,x-.19,y-.22,x+.19,y+.22,-7.945);
                    face.panel("light",true,x-.15,y-.12,x+.15,y+.12,-7.97);
                }
            }
            if(side!=Direction.NORTH){
                face.readableLabel("badge",-4.8,5.25,4.8,6.95,-7.875);
                face.panel("heat",true,-4.9,-5.7,4.9,4.6,-7.49);
                for(int sign:new int[]{-1,1}){
                    double x=sign*2.65;
                    face.bevel("metal",x-.38,-5.55,-7.96,x+.38,4.45,-7.58,.08);
                    face.panel("armor",false,x-.12,-4.65,x+.12,3.45,-7.985);
                    for(double y:new double[]{-5.35,3.85})face.bevel("metal",x-.7,y,-8.05,x+.7,y+.55,-7.48,.08);
                }
                for(double y:new double[]{-5.7,4.5})face.bevel("metal",-4.95,y,-7.94,4.95,y+.15,-7.51,.03);
            }else{
                face.panel("grille",false,-4.7,5.25,4.7,6.9,-7.875);
                face.bevel("metal",-5.05,-.2,-8.02,5.05,4.6,-7.51,.09);
                face.panel("screen",false,-4.9,0,4.9,4.4,-8.045);
                face.bevel("metal",-1.9,.08,-8.09,-1.8,4.35,-8.02,.015);
                face.bevel("armor",-5.1,-5.8,-7.96,-1.85,-.6,-7.5,.13);
                face.panel("vent",false,-4.8,-5.45,-2.15,-.95,-7.985);
                for(double y=-5.1;y<-.9;y+=.8)face.bevel("metal",-4.65,y,-8.02,-2.3,y+.12,-7.96,.012);
                face.bevel("armor",-.8,-5.8,-8.0,4.85,-.6,-7.3,.18);
                face.panel("metal",false,-.45,-5.4,4.5,-1,-8.025);
                for(double x:new double[]{.7,3.25}){
                    face.bevel("metal",x-.7,-2.1,-8.11,x+.7,-1,-7.95,.04);
                    face.panel("light",true,x-.45,-1.9,x+.45,-1.2,-8.135);
                    face.bevel("metal",x-.48,-2.4,-8.35,x+.48,-1.65,-8.12,.07);
                    face.bevel("metal",x-.48,-5.1,-8.42,x+.48,-2.39,-8.18,.05);
                    face.panel("strip",true,x-.4,-4.15,x+.4,-3.8,-8.445);
                    face.bevel("metal",x-.7,-5.75,-8.4,x+.7,-5.12,-7.98,.07);
                }
            }
            b.add(face.build(),side);
        }
        SolarDishModel.upPanel(b,"heat",true,0,0,6.68,5.25);
        b.bevel("metal",-2.2,6.7,-2.2,2.2,7.9,2.2,.2);
        SolarDishModel.upPanel(b,"light",true,0,0,7.925,.85);
        return b.build();
    }

    static void render(IndustrialMachineBlockEntity machine,MatrixStack matrices,VertexConsumerProvider consumers,int light){
        boolean working=machine!=null&&machine.status()==IndustrialMachineBlockEntity.Status.WORKING;
        float heat=machine==null?0:Math.min(1,Math.max(0,(machine.temperature()-20)/1180F));
        int glow=0xFF0000 | (int)(65+heat*140)<<8 | (int)(8+heat*65);
        MESH.render(matrices,consumers,light,glow,working||heat>.03F,true);
        if(IrisCompatibility.isRenderingShadowPass() || !net.askcraft.justifylasers.client.ClientSettings.get().machineDisplays)return;
        matrices.push();
        matrices.translate(.293,.252,-.506);
        matrices.scale(-.005F,-.005F,.005F);
        var font=MinecraftClient.getInstance().textRenderer;
        int fuel=machine==null?0:machine.fuel(),total=machine==null?1:Math.max(1,machine.fuelTotal());
        int bars=Math.max(0,Math.min(8,(int)(8L*fuel/total)));
        String meter="▮".repeat(bars)+"·".repeat(8-bars);
        line(font,matrices,consumers,Text.translatable("gui.justifylasers.industry.fuel_label"),0,0,80,0xFFCE75);
        line(font,matrices,consumers,Text.literal(meter),0,12,80,0xFFCE75);
        line(font,matrices,consumers,Text.literal((machine==null?0:machine.generatedRate())+" "+Platform.ENERGY_UNIT+"/t"),0,24,80,0xFFDDA0);
        String status=machine==null?"idle":machine.status().name().toLowerCase(java.util.Locale.ROOT);
        line(font,matrices,consumers,Text.translatable("gui.justifylasers.industry.status."+status),0,36,80,working?0xFFA622:0x75929B);
        line(font,matrices,consumers,Text.translatable("gui.justifylasers.industry.efficiency_label"),86,0,33,0xBFBFBF);
        line(font,matrices,consumers,Text.literal((machine==null?0:machine.efficiency())+"%"),86,12,33,0xFFA622);
        line(font,matrices,consumers,Text.translatable("gui.justifylasers.industry.temperature_label"),86,24,33,0xBFBFBF);
        line(font,matrices,consumers,Text.literal((machine==null?20:machine.temperature())+"°C"),86,36,33,0xFFA622);
        matrices.pop();
    }
    private static void line(TextRenderer font,MatrixStack matrices,VertexConsumerProvider consumers,Text text,int x,int y,int width,int color){
        matrices.push();
        matrices.translate(x,y,0);
        float fit=Math.min(1,width/(float)Math.max(1,font.getWidth(text)));
        matrices.scale(fit,fit,1);
        font.draw(text,0,0,color,false,matrices.peek().getPositionMatrix(),consumers,TextRenderer.TextLayerType.NORMAL,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }
    private FuelGeneratorModel() { }
}
