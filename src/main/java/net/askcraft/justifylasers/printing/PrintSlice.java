package net.askcraft.justifylasers.printing;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Clips a face at the current print layer without stretching geometry or its UVs. */
public final class PrintSlice {
    public record Vertex(Vec3d position,double u,double v) {
        Vertex interpolate(Vertex to,double t){return new Vertex(position.lerp(to.position,t),u+(to.u-u)*t,v+(to.v-v)*t);}
    }
    public static List<Vertex> below(List<Vertex> face,double height) {
        var result=new ArrayList<Vertex>();
        for(int i=0;i<face.size();i++){
            var a=face.get(i);var b=face.get((i+1)%face.size());boolean ai=a.position.y<=height,bi=b.position.y<=height;
            if(ai)result.add(a);
            if(ai!=bi)result.add(a.interpolate(b,(height-a.position.y)/(b.position.y-a.position.y)));
        }
        return result;
    }
    public static List<Vertex> cap(PrintDesign.Element cube,double height){
        var points=new ArrayList<Vec3d>();
        for(var side:net.minecraft.util.math.Direction.values()){
            var corners=cube.corners(side);
            for(int i=0;i<4;i++){var a=corners[i];var b=corners[(i+1)%4];
                if((a.y<height)!=(b.y<height)&&Math.abs(a.y-b.y)>1e-9){var p=a.lerp(b,(height-a.y)/(b.y-a.y));if(points.stream().noneMatch(old->old.squaredDistanceTo(p)<1e-12))points.add(p);}
            }
        }
        if(points.size()<3)return List.of();
        Vec3d center=points.stream().reduce(Vec3d.ZERO,Vec3d::add).multiply(1d/points.size());
        points.sort(java.util.Comparator.comparingDouble(p->-Math.atan2(p.z-center.z,p.x-center.x)));
        return points.stream().map(p->new Vertex(p,p.x,p.z)).toList();
    }
    private PrintSlice(){ }
}
