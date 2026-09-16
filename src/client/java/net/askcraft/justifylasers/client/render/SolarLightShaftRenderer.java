package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity;
import net.askcraft.justifylasers.industry.SolarExposure;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/** Broad, low-opacity sunlight shafts. No collision, white laser core, impact flare or optical power. */
final class SolarLightShaftRenderer {
    private record Shaft(Vec3d origin, Vec3d sun, long panels, float time, boolean small) { }
    private static final Map<BlockPos,Shaft> SOURCES = new LinkedHashMap<>();

    static void queue(SolarConcentratorBlockEntity source, Vec3d camera, float delta) {
        if (!net.askcraft.justifylasers.client.ClientSettings.get().solarLightShafts || SOURCES.size() >= 16
                || camera.squaredDistanceTo(Vec3d.ofCenter(source.getPos())) > 128*128) return;
        SOURCES.put(source.getPos(),new Shaft(Vec3d.of(SolarStructure.origin(source)),SolarExposure.sunDirection(source.getWorld(),delta),
                source.visiblePanels(),source.getWorld().getTime()+delta,source.small()));
    }
    static boolean queued() { return !SOURCES.isEmpty(); }
    static void clear() { SOURCES.clear(); }
    static void render(VertexConsumer buffer, Vec3d camera) {
        for (Shaft shaft : SOURCES.values()) {
            for (int index = 0; index < (shaft.small ? 1 : 8); index++) {
                if (shaft.panels == 0) continue;
                double angle = index * Math.PI / 4;
                Vec3d dishPoint = SolarConcentratorModel.dishPoint(new Vec3d(Math.cos(angle)*1.05,.23,Math.sin(angle)*1.05),SolarConcentratorModel.sunTilt(shaft.sun));
                Vec3d target = shaft.origin.add(shaft.small ? new Vec3d(.5,.6,.5) : dishPoint.add(1.5,.5,1.5));
                Vec3d side = shaft.sun.crossProduct(camera.subtract(target));
                if (side.lengthSquared() < 1e-8) side = shaft.sun.crossProduct(new Vec3d(0,0,1));
                side = side.normalize();
                // Segments keep the near field stable and let the upper end vanish far above the build limit.
                double length = 1024 / Math.max(.12,shaft.sun.y);
                for (int section = 0; section < 24; section++) {
                    double a = section/24d, b = (section+1)/24d;
                    double da = length*a*a, db = length*b*b;
                    Vec3d start = target.add(shaft.sun.multiply(da)), end = target.add(shaft.sun.multiply(db));
                    double wa = .20 + Math.min(3.2,da*.014), wb = .20 + Math.min(3.2,db*.014);
                    float motion = .9F + .1F*(float)Math.sin(shaft.time*.018 + index*1.7 + a*6);
                    double near = Math.min(1,camera.distanceTo(start)/.8);
                    int alphaA = (int)(15 * motion * near * Math.pow(1-a,1.7));
                    int alphaB = (int)(15 * motion * near * Math.pow(1-b,1.7));
                    ribbon(buffer,start,end,side,wa,wb,alphaA,alphaB,camera);
                }
            }
        }
    }
    private static void ribbon(VertexConsumer buffer, Vec3d a, Vec3d b, Vec3d side, double widthA, double widthB, int alphaA, int alphaB, Vec3d camera) {
        double[] edges = {-1,-.6,0,.6,1};
        double[] opacity = {0,.3,1,.3,0};
        for (int i=0;i<edges.length-1;i++) {
            vertex(buffer,a.add(side.multiply(widthA*edges[i])).subtract(camera),(int)(alphaA*opacity[i]));
            vertex(buffer,b.add(side.multiply(widthB*edges[i])).subtract(camera),(int)(alphaB*opacity[i]));
            vertex(buffer,b.add(side.multiply(widthB*edges[i+1])).subtract(camera),(int)(alphaB*opacity[i+1]));
            vertex(buffer,a.add(side.multiply(widthA*edges[i+1])).subtract(camera),(int)(alphaA*opacity[i+1]));
        }
    }
    private static void vertex(VertexConsumer buffer, Vec3d p, int alpha) {
        RenderVersion.endVertex(buffer.vertex((float)p.x,(float)p.y,(float)p.z).color(255,243,210,alpha));
    }
    private SolarLightShaftRenderer() { }
}
