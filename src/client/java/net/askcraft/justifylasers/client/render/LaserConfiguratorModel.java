package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class LaserConfiguratorModel {
    static final OpticalComponentMesh MESH = build();

    public static void render(net.minecraft.item.ItemStack stack, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        int color = net.askcraft.justifylasers.item.LaserConfiguratorItem.color(net.askcraft.justifylasers.item.LaserConfiguratorItem.mode(stack));
        MESH.render(matrices, consumers, light, color, true, true);
        matrices.pop();
    }

    private static OpticalComponentMesh build() {
        var body = new OpticalComponentMesh.Builder("configurator");
        body.bevel("metal",-2.15,-1.45,-1.05,2.15,13.1,1.05,0.24);
        body.solid("rubber",-2.17,0.1,-0.9,-1.9,11.7,0.9);
        body.solid("rubber",1.9,0.1,-0.9,2.17,11.7,0.9);
        body.bevel("steel",-2.4,-2.4,-1.2,2.4,-0.9,1.2,0.25);
        body.bevel("metal",-2.45,-3.45,-1.22,2.45,-2.65,1.22,0.2);
        for (Direction face : new Direction[]{Direction.NORTH,Direction.SOUTH}) {
            var panel = new OpticalComponentMesh.Builder("configurator");
            panel.box("control",true,-1.45,7.4,-1.085,1.45,12.85,-1.055);
            panel.box(face == Direction.NORTH ? "grip" : "back",face == Direction.NORTH,
                    -1.325,-0.8,-1.09,1.325,7.25,-1.056);
            panel.box("end",true,-2.28,-2.65,-1.18,2.28,-2.4,-0.9);
            panel.box("collar",true,-1.82,13.0,-1.09,1.82,14.52,-0.95);
            body.add(panel.build(),face);
        }
        for (Direction side : new Direction[]{Direction.EAST,Direction.WEST}) {
            var flank = new OpticalComponentMesh.Builder("configurator");
            flank.box("side_light",true,-0.78,8.3,-2.22,0.78,12.1,-2.18);
            body.add(flank.build(),side);
        }
        for(int sign:new int[]{-1,1}) for(double y:new double[]{1.5,3.8,6.1})
            body.solid("metal",sign*1.45-0.28,y,-1.16,sign*1.45+0.28,y+0.3,-1.10);

        // Five contiguous extrusions follow the fork silhouette rather than texturing a solid rectangle.
        double[][] outer={{121,17},{121,61},{73,130},{73,333},{132,350},{132,390}};
        double[][] inner={{177,17},{162,62},{126,137},{126,274},{162,308},{162,390}};
        var jaw=new OpticalComponentMesh.Builder("configurator");
        Vec3d min=sheet(70,393), max=sheet(198,15);
        for(int i=0;i<outer.length-1;i++) jaw.prism("jaw",true,
                new Vec3d[]{sheet(outer[i]),sheet(outer[i+1]),sheet(inner[i+1]),sheet(inner[i])}, min,max,-0.95,0.95);
        body.add(jaw.build(),Direction.NORTH);
        body.add(jaw.build(),Direction.SOUTH);

        body.bevel("metal",-1.33,16.9,-1.12,1.33,19.57,1.12,0.25);
        for(int sign:new int[]{-1,1}) {
            body.bevel("steel",-0.68,18.25+sign*2.0-0.28,-0.55,0.68,18.25+sign*2.0+0.28,0.55,0.08);
            body.bevel("steel",sign*1.83-0.3,17.45,-0.58,sign*1.83+0.3,19.05,0.58,0.08);
        }
        for(Direction face:new Direction[]{Direction.NORTH,Direction.SOUTH}) {
            var core=new OpticalComponentMesh.Builder("configurator");
            core.panel("core",true,-1.25,16.95,1.25,19.52,-1.135);
            for(int sign:new int[]{-1,1}) {
                core.panel("tab",true,-0.59,18.25+sign*2.0-0.19,0.59,18.25+sign*2.0+0.19,-0.565);
                core.panel("side_light",true,sign*1.83-0.21,17.54,sign*1.83+0.21,18.96,-0.595);
            }
            body.add(core.build(),face);
        }
        return body.build();
    }

    private static Vec3d sheet(double[] p) { return sheet(p[0],p[1]); }
    private static Vec3d sheet(double x,double y) { return new Vec3d((x-255)/40,23-(y-15)/40,0); }
    private LaserConfiguratorModel() { }
}
