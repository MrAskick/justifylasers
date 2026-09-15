package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Component-local UVs and a separate, neutral emission mask for optical hardware. */
final class OpticalComponentMesh {
    record Face(Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d normal,
                ComponentAtlas.Uv ua, ComponentAtlas.Uv ub, ComponentAtlas.Uv uc, ComponentAtlas.Uv ud,
                boolean glowing, int alpha) { }

    private final String kind;
    private final List<Face> faces;

    OpticalComponentMesh(String kind, List<Face> faces) {
        this.kind = kind;
        this.faces = List.copyOf(faces);
    }

    List<Face> faces() { return faces; }

    void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int rgb, boolean active, boolean emission) {
        VertexConsumer solid = consumers.getBuffer(RenderLayer.getEntitySolid(ComponentAtlas.texture(kind, "base")));
        draw(matrices, solid, light, 0xFFFFFF, false, false);
        if (active) {
            String mask = emission && IrisCompatibility.isShaderPackInUse() ? "glow" : "indicator";
            VertexConsumer glow = consumers.getBuffer(RenderLayer.getEntityCutout(ComponentAtlas.texture(kind, mask)));
            draw(matrices, glow, emission ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, rgb, true, false);
        }
        if (!IrisCompatibility.isRenderingShadowPass() && faces.stream().anyMatch(f -> f.alpha < 255)) {
            // The core supplies opaque depth; the cover must not hide its late white-hot halo.
            VertexConsumer glass = consumers.getBuffer(LaserCrystalModel.EmissiveLayers.lens(ComponentAtlas.texture(kind, "base")));
            draw(matrices, glass, light, 0xFFFFFF, false, true);
        }
    }

    private void draw(MatrixStack matrices, VertexConsumer buffer, int light, int rgb, boolean glow, boolean glass) {
        for (Face f : faces) {
            if (glass != (f.alpha < 255) || glow && !f.glowing) continue;
            Vec3d offset = glow ? f.normal.multiply(0.00035) : Vec3d.ZERO;
            vertex(buffer, matrices, f.a.add(offset), f.normal, f.ua, rgb, f.alpha, light);
            vertex(buffer, matrices, f.b.add(offset), f.normal, f.ub, rgb, f.alpha, light);
            vertex(buffer, matrices, f.c.add(offset), f.normal, f.uc, rgb, f.alpha, light);
            vertex(buffer, matrices, f.d.add(offset), f.normal, f.ud, rgb, f.alpha, light);
        }
    }

    private static void vertex(VertexConsumer buffer, MatrixStack matrices, Vec3d p, Vec3d n, ComponentAtlas.Uv uv, int rgb, int alpha, int light) {
        RenderVersion.endVertex(RenderVersion.normal(buffer.vertex(matrices.peek().getPositionMatrix(), (float) p.x, (float) p.y, (float) p.z)
                        .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha).texture(uv.u(), uv.v())
                        .overlay(OverlayTexture.DEFAULT_UV).light(light), matrices.peek().getNormalMatrix(), (float) n.x, (float) n.y, (float) n.z));
    }

    static final class Builder {
        private final String kind;
        private final ComponentAtlas atlas;
        private final List<Face> faces = new ArrayList<>();

        Builder(String kind) { this.kind = kind; atlas = ComponentAtlas.load(kind, "base"); }
        OpticalComponentMesh build() { return new OpticalComponentMesh(kind, faces); }

        void add(OpticalComponentMesh mesh, Direction side) { add(mesh, Vec3d.of(side.getVector())); }

        void add(OpticalComponentMesh mesh, Vec3d normal) {
            Vec3d right = Math.abs(normal.y) > 0.999 ? new Vec3d(1, 0, 0) : normal.crossProduct(new Vec3d(0, 1, 0)).normalize();
            Vec3d up = right.crossProduct(normal);
            UnaryOperator<Vec3d> turn = p -> right.multiply(p.x).add(up.multiply(p.y)).subtract(normal.multiply(p.z));
            for (Face f : mesh.faces) faces.add(new Face(turn.apply(f.a), turn.apply(f.b), turn.apply(f.c), turn.apply(f.d), turn.apply(f.normal),
                    f.ua, f.ub, f.uc, f.ud, f.glowing, f.alpha));
        }

        void box(String part, boolean glow, double x, double y, double z, double X, double Y, double Z) {
            var metal=atlas.region("metal");
            box(new ComponentAtlas.Skin(atlas.region(part),metal,metal,metal,metal,metal,false,true), glow, new Vec3d(x,y,z), new Vec3d(X,Y,Z));
        }

        void solid(String part, double x, double y, double z, double X, double Y, double Z) {
            box(atlas.trim(part), false, new Vec3d(x,y,z), new Vec3d(X,Y,Z));
        }

        private void box(ComponentAtlas.Skin skin, boolean glow, Vec3d min, Vec3d max) {
            double x=min.x,y=min.y,z=min.z,X=max.x,Y=max.y,Z=max.z;
            projected(skin, glow, min, max, new Vec3d(x,y,z),new Vec3d(x,Y,z),new Vec3d(X,Y,z),new Vec3d(X,y,z));
            projected(skin, false, min, max, new Vec3d(X,y,Z),new Vec3d(X,Y,Z),new Vec3d(x,Y,Z),new Vec3d(x,y,Z));
            projected(skin, false, min, max, new Vec3d(x,y,Z),new Vec3d(x,Y,Z),new Vec3d(x,Y,z),new Vec3d(x,y,z));
            projected(skin, false, min, max, new Vec3d(X,y,z),new Vec3d(X,Y,z),new Vec3d(X,Y,Z),new Vec3d(X,y,Z));
            projected(skin, false, min, max, new Vec3d(x,Y,z),new Vec3d(x,Y,Z),new Vec3d(X,Y,Z),new Vec3d(X,Y,z));
            projected(skin, false, min, max, new Vec3d(x,y,Z),new Vec3d(x,y,z),new Vec3d(X,y,z),new Vec3d(X,y,Z));
        }

        void bevel(String part, double x, double y, double z, double X, double Y, double Z, double cut) {
            Vec3d min = new Vec3d(x,y,z), max = new Vec3d(X,Y,Z);
            ComponentAtlas.Skin skin = atlas.trim(part);
            Vec3d[][] rings = {octagon(x+cut,z+cut,X-cut,Z-cut,y,cut/2), octagon(x,z,X,Z,y+cut,cut),
                    octagon(x,z,X,Z,Y-cut,cut), octagon(x+cut,z+cut,X-cut,Z-cut,Y,cut/2)};
            for (int band=0; band<3; band++) for (int i=0; i<8; i++) {
                int next=(i+1)%8;
                projected(skin, false, min, max, rings[band][i], rings[band+1][i], rings[band+1][next], rings[band][next]);
            }
            for (int end : new int[]{0,3}) {
                Vec3d center = rings[end][0].add(rings[end][4]).multiply(0.5);
                for (int i=0;i<8;i++) projected(skin, false, min, max, center, rings[end][end==0?i:(i+1)%8], rings[end][end==0?(i+1)%8:i], center);
            }
        }

        // A revolved profile uses one planar UV projection over the complete rim, not one repeated tile per segment.
        void profile(String part, boolean glow, double[] radii, double[] depths, double textureRadius, int alpha) {
            for (int band=0; band<radii.length-1; band++) for (int i=0;i<48;i++) {
                double a=i*Math.PI/24, b=(i+1)*Math.PI/24;
                Vec3d[] p={circle(radii[band],depths[band],a),circle(radii[band],depths[band],b),
                        circle(radii[band+1],depths[band+1],b),circle(radii[band+1],depths[band+1],a)};
                ComponentAtlas.Uv[] uv=new ComponentAtlas.Uv[4];
                for(int j=0;j<4;j++) uv[j]=atlas.region(part).uv((p[j].x/textureRadius+1)/2,(1-p[j].y/textureRadius)/2);
                face(p, uv, glow, alpha);
            }
        }

        void panel(String part, boolean glow, double x, double y, double X, double Y, double z) {
            Vec3d min=new Vec3d(x,y,z),max=new Vec3d(X,Y,z);
            projected(atlas.surface(part),glow,min,max,new Vec3d(x,y,z),new Vec3d(x,Y,z),new Vec3d(X,Y,z),new Vec3d(X,y,z));
        }

        void trimmedPanel(String part, boolean glow, double x, double y, double X, double Y, double z) {
            Vec3d min = new Vec3d(x, y, z), max = new Vec3d(X, Y, z);
            projected(atlas.trim(part), glow, min, max, new Vec3d(x,y,z), new Vec3d(x,Y,z), new Vec3d(X,Y,z), new Vec3d(X,y,z));
        }

        // Adjacent convex sections share one UV field, so an extruded bent jaw keeps its original panel layout.
        void prism(String part, boolean glow, Vec3d[] outline, Vec3d uvMin, Vec3d uvMax, double front, double back) {
            prism(part, part, glow, outline, uvMin, uvMax, front, back);
        }

        void prism(String part, String backPart, boolean glow, Vec3d[] outline, Vec3d uvMin, Vec3d uvMax, double front, double back) {
            var region = atlas.region(part);
            var backRegion = atlas.region(backPart);
            Vec3d[] a = new Vec3d[4], b = new Vec3d[4];
            ComponentAtlas.Uv[] uv = new ComponentAtlas.Uv[4], reversed = new ComponentAtlas.Uv[4];
            for (int i = 0; i < 4; i++) {
                a[i] = new Vec3d(outline[i].x, outline[i].y, front);
                b[3-i] = new Vec3d(outline[i].x, outline[i].y, back);
                uv[i] = region.uv((a[i].x-uvMin.x)/(uvMax.x-uvMin.x), (uvMax.y-a[i].y)/(uvMax.y-uvMin.y));
                reversed[3-i] = backRegion.uv((a[i].x-uvMin.x)/(uvMax.x-uvMin.x), (uvMax.y-a[i].y)/(uvMax.y-uvMin.y));
            }
            if (normal(a[0],a[1],a[2],a[3]).z > 0) {
                for (int i = 0; i < 2; i++) {
                    Vec3d temp=a[i]; a[i]=a[3-i]; a[3-i]=temp;
                    temp=b[i]; b[i]=b[3-i]; b[3-i]=temp;
                    var t=uv[i]; uv[i]=uv[3-i]; uv[3-i]=t;
                    t=reversed[i]; reversed[i]=reversed[3-i]; reversed[3-i]=t;
                }
            }
            face(a,uv,glow,255); face(b,reversed,glow,255);
            var min=new Vec3d(uvMin.x,uvMin.y,front); var max=new Vec3d(uvMax.x,uvMax.y,back);
            for(int i=0;i<4;i++) {
                int next=(i+1)%4;
                projected(atlas.trim("metal"),false,min,max,a[next],a[i],new Vec3d(a[i].x,a[i].y,back),new Vec3d(a[next].x,a[next].y,back));
            }
        }

        private void projected(ComponentAtlas.Skin skin, boolean glow, Vec3d min, Vec3d max, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
            Vec3d n=normal(a,b,c,d);
            face(new Vec3d[]{a,b,c,d},new ComponentAtlas.Uv[]{skin.project(a,n,min,max),skin.project(b,n,min,max),skin.project(c,n,min,max),skin.project(d,n,min,max)},glow,255);
        }

        private void face(Vec3d[] p, ComponentAtlas.Uv[] uv, boolean glow, int alpha) {
            Vec3d n=normal(p[0],p[1],p[2],p[3]);
            faces.add(new Face(p[0].multiply(1/16d),p[1].multiply(1/16d),p[2].multiply(1/16d),p[3].multiply(1/16d),n,
                    uv[0],uv[1],uv[2],uv[3],glow,alpha));
        }

        private static Vec3d normal(Vec3d a,Vec3d b,Vec3d c,Vec3d d) {
            Vec3d n=b.subtract(a).crossProduct(c.subtract(a));
            if(n.lengthSquared()<1e-14) n=c.subtract(a).crossProduct(d.subtract(a));
            return n.multiply(1/n.length());
        }

        private static Vec3d circle(double radius,double z,double angle) { return new Vec3d(Math.cos(angle)*radius,Math.sin(angle)*radius,z); }
        private static Vec3d[] octagon(double x,double z,double X,double Z,double y,double cut) {
            return new Vec3d[]{new Vec3d(x+cut,y,z),new Vec3d(X-cut,y,z),new Vec3d(X,y,z+cut),new Vec3d(X,y,Z-cut),
                    new Vec3d(X-cut,y,Z),new Vec3d(x+cut,y,Z),new Vec3d(x,y,Z-cut),new Vec3d(x,y,z+cut)};
        }
    }
}
