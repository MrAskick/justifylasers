package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Faceted collector with separate panel, trim and emissive surfaces, in model pixels. */
final class SolarDishModel {
    static OpticalComponentMesh build(String material, double radius, double rise, double focus, double rim) {
        return build(material, radius, rise, focus, rim, false);
    }

    static OpticalComponentMesh build(String material, double radius, double rise, double focus, double rim, boolean continuousGrid) {
        var b = new OpticalComponentMesh.Builder(material);
        double inner = radius * .22;
        for (int i = 0; i < 8; i++) {
            double a = (i + .5) * Math.PI / 4, c = (i + 1.5) * Math.PI / 4;
            Vec3d p = radial(inner, 0, a), q = radial(inner, 0, c);
            Vec3d midP = radial(radius * .53, rise * .27, a), midQ = radial(radius * .53, rise * .27, c);
            Vec3d outerP = radial(radius, rise, a), outerQ = radial(radius, rise, c);
            if (continuousGrid) {
                b.planarSurface("grid", p, q, midQ, midP, radius);
                b.planarSurface("grid", midP, midQ, outerQ, outerP, radius);
            } else {
                b.surface("mirror", false, p, q, midQ, midP, 255);
                b.surface("panel", false, midP, midQ, outerQ, outerP, 255);
            }
            b.surface("metal", false, outerP.add(0,-rim,0), outerP, outerQ, outerQ.add(0,-rim,0), 255);
            b.surface("metal", false, radial(inner,-rim,a), outerP.add(0,-rim,0), outerQ.add(0,-rim,0), radial(inner,-rim,c), 255);
            Vec3d inset = outerQ.subtract(outerP).normalize().multiply(rim * 1.2);
            rail(b, "armor", outerP.add(inset).add(0,.03,0), outerQ.subtract(inset).add(0,.03,0), rim * .7);
            rail(b, "strip", p.add(0,.045,0), outerP.add(0,.045,0), rim * .24, true);
            double cap = rim * 1.3;
            b.bevel("armor", outerP.x-cap, outerP.y-.25, outerP.z-cap, outerP.x+cap, outerP.y+cap, outerP.z+cap, cap*.24);
            upPanel(b,"light",true,outerP.x,outerP.z,outerP.y+cap+.025,cap*.6);
        }
        b.bevel("metal",-inner,-rim,-inner,inner,.5,inner,rim*.35);
        upPanel(b,"light",true,0,0,.525,inner*.7);
        double head=radius*.105;
        for(int i=0;i<3;i++){
            double angle=i*Math.PI*2/3;
            Vec3d foot=radial(radius*.39,rise*.16,angle),top=radial(head,focus-head,angle);
            rail(b,"metal",foot,top,rim*.55);
            rail(b,"strip",foot.add(radial(rim*.57,0,angle)),top.add(radial(rim*.57,0,angle)),rim*.14,true);
        }
        b.bevel("armor",-head,focus-head,-head,head,focus+head,head,head*.2);
        upPanel(b,"light",true,0,0,focus+head+.025,head*.63);
        for(Direction side:Direction.Type.HORIZONTAL){
            var face=new OpticalComponentMesh.Builder(material);
            face.panel("light",true,-head*.63,focus-head*.63,head*.63,focus+head*.63,-head-.025);
            b.add(face.build(),side);
        }
        return b.build();
    }

    static void upPanel(OpticalComponentMesh.Builder b,String part,boolean glow,double x,double z,double y,double half){
        b.surface(part,glow,new Vec3d(x-half,y,z-half),new Vec3d(x-half,y,z+half),
                new Vec3d(x+half,y,z+half),new Vec3d(x+half,y,z-half),255);
    }

    static void rail(OpticalComponentMesh.Builder b,String part,Vec3d start,Vec3d end,double half){ rail(b,part,start,end,half,false); }
    static void rail(OpticalComponentMesh.Builder b,String part,Vec3d start,Vec3d end,double half,boolean glow){
        Vec3d axis=end.subtract(start).normalize();
        Vec3d right=axis.crossProduct(Math.abs(axis.y)>.95?new Vec3d(0,0,1):new Vec3d(0,1,0)).normalize().multiply(half);
        Vec3d up=right.normalize().crossProduct(axis).multiply(half);
        Vec3d[] corners={right.add(up),right.subtract(up),right.multiply(-1).subtract(up),up.subtract(right)};
        for(int i=0;i<4;i++){
            int next=(i+1)%4;
            b.surface(part,glow,start.add(corners[i]),start.add(corners[next]),end.add(corners[next]),end.add(corners[i]),255);
        }
    }

    static OpticalComponentMesh port(String material,double size){
        var b=new OpticalComponentMesh.Builder(material);
        b.bevel("armor",-size,-size,-.25,size,size,.4,.12);
        b.panel("metal",false,-size*.82,-size*.82,size*.82,size*.82,-.275);
        b.panel("lens",true,-size*.56,-size*.56,size*.56,size*.56,-.3);
        return b.build();
    }

    private static Vec3d radial(double r,double y,double angle){return new Vec3d(Math.cos(angle)*r,y,Math.sin(angle)*r);}
    private SolarDishModel() { }
}
