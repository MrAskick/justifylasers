package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelSeamsTest {
    @Test
    void modulePanelsDoNotShareAnExposedPlane() {
        int overlaps = 0;
        for (var entry : LaserModuleModel.meshes().entrySet()) {
            var mesh = entry.getValue();
            for (int i = 0; i < mesh.size(); i++) for (int j = i + 1; j < mesh.size(); j++) {
                var a = mesh.get(i); var b = mesh.get(j);
                if (a.normal().dotProduct(b.normal()) < 0.999999) continue;
                if (Math.abs(a.normal().dotProduct(a.a().subtract(b.a()))) > 1e-7) continue;
                int axis = Math.abs(a.normal().x) > 0.999999 ? 0 : Math.abs(a.normal().y) > 0.999999 ? 1 : Math.abs(a.normal().z) > 0.999999 ? 2 : -1;
                if (axis < 0) continue;
                var aa = List.of(a.a(), a.b(), a.c(), a.d()); var bb = List.of(b.a(), b.b(), b.c(), b.d());
                if (!rectangle(aa,axis) || !rectangle(bb,axis)) continue;
                boolean overlap = true;
                for (int k = 0; k < 3; k++) if (k != axis) {
                    int component = k;
                    double minA=aa.stream().mapToDouble(p->get(p,component)).min().orElseThrow(), maxA=aa.stream().mapToDouble(p->get(p,component)).max().orElseThrow();
                    double minB=bb.stream().mapToDouble(p->get(p,component)).min().orElseThrow(), maxB=bb.stream().mapToDouble(p->get(p,component)).max().orElseThrow();
                    overlap &= Math.min(maxA,maxB)-Math.max(minA,minB)>1e-5;
                }
                if (overlap) {
                    if (overlaps < 100) System.out.println(entry.getKey()+" "+a.material()+" "+a.a()+" / "+a.c()+" overlaps "+b.material()+" "+b.a()+" / "+b.c());
                    overlaps++;
                }
            }
        }
        assertEquals(0, overlaps, "Coplanar module panels");
    }
    private static boolean rectangle(List<Vec3d> p, int axis) {
        for(int i=0;i<4;i++) {
            Vec3d e=p.get((i+1)%4).subtract(p.get(i));
            if (e.lengthSquared()<1e-10) return false;
            int changes=0;
            for(int k=0;k<3;k++) if(k!=axis&&Math.abs(get(e,k))>1e-7) changes++;
            if(changes!=1) return false;
        }
        return true;
    }
    private static double get(Vec3d p,int axis) {return axis==0?p.x:axis==1?p.y:p.z;}
}
