package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;

/** Shared optical profiles. Endpoints, orientation, color and animation remain per-instance data. */
final class GpuLightEffects {
    private enum Profile { BEAM, SABER, SABER_CLIPPED, CORE, SUNLIGHT }
    private static ShaderProgram halo, core, sunlight;

    static void initialize() {
        ClientPlatform.registerShader("gpu_halo", VertexFormats.POSITION_TEXTURE_COLOR, shader -> {
            GpuGeometryCache.clear();
            halo = shader;
        });
        ClientPlatform.registerShader("gpu_core", VertexFormats.POSITION_TEXTURE_COLOR, shader -> core = shader);
        ClientPlatform.registerShader("gpu_sunlight", VertexFormats.POSITION_TEXTURE_COLOR, shader -> sunlight = shader);
    }

    static boolean enabled() { return ClientSettings.get().gpuEffects && halo != null; }

    static boolean core(Vec3d center, Vec3d camera, int rgb, float time) {
        if (!enabled() || core == null) return false;
        var mesh = GpuGeometryCache.get(Profile.CORE, 3456 * 30L,
                () -> RenderVersion.staticMesh(VertexFormats.POSITION_TEXTURE_COLOR, GpuLightEffects::bakeCore));
        if (mesh == null) return false;
        Vec3d view = camera.subtract(center);
        Vec3d forward = view.lengthSquared() < 1e-8 ? new Vec3d(0, 0, 1) : view.normalize();
        Vec3d reference = Math.abs(forward.y) > .9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d right = reference.crossProduct(forward).normalize();
        vector(core, "Center", center.subtract(camera));
        vector(core, "Right", right);
        vector(core, "Up", forward.crossProduct(right));
        vector(core, "Forward", forward);
        color(core, "BeamColor", rgb);
        core.getUniform("Time").set(time);
        draw(mesh, core);
        return true;
    }

    private static void bakeCore(VertexConsumer out) {
        envelope(out, new double[]{0, .065, .09, .115, .145, .18, .22, .26, .31},
                new int[]{255, 255, 248, 220, 165, 94, 40, 10, 0}, .312, 0);
        for (boolean hot : new boolean[]{false, true}) {
            double width = hot ? .026 : .12;
            for (int strand = 0; strand < 3; strand++) for (int side = 0; side < 48; side++) {
                double a = side * Math.PI / 24, b = (side + 1) * Math.PI / 24;
                for (int corner = 0; corner < 4; corner++) {
                    descriptor(out, corner < 2 ? a : b, strand * Math.PI * 2 / 3,
                            corner == 0 || corner == 3 ? -width : width, hot ? .004F : .003F, 0,
                            hot ? 3 : 2, hot ? 218 : 42);
                }
            }
        }
        envelope(out, new double[]{0, .048, .068, .088, .12}, new int[]{255, 255, 246, 132, 0}, .114, 1);
    }

    private static void envelope(VertexConsumer out, double[] radii, int[] alpha, double depthRadius, int palette) {
        for (int ring = radii.length - 2; ring >= 0; ring--) for (int side = 0; side < 48; side++) {
            for (int corner = 0; corner < 4; corner++) {
                int r = ring + (corner == 1 || corner == 2 ? 1 : 0);
                double angle = (side + (corner >= 2 ? 1 : 0)) * Math.PI / 24;
                descriptor(out, Math.cos(angle) * radii[r], Math.sin(angle) * radii[r],
                        Math.sqrt(Math.max(0, depthRadius * depthRadius - radii[r] * radii[r])), 0, 0, palette, alpha[r]);
            }
        }
    }

    static boolean sunlight(Vec3d target, Vec3d sun, Vec3d side, Vec3d camera, double length, float time, int index) {
        if (!enabled() || sunlight == null) return false;
        var mesh = GpuGeometryCache.get(Profile.SUNLIGHT, 384 * 30L,
                () -> RenderVersion.staticMesh(VertexFormats.POSITION_TEXTURE_COLOR, GpuLightEffects::bakeSunlight));
        if (mesh == null) return false;
        vector(sunlight, "Start", target.subtract(camera));
        vector(sunlight, "Sun", sun);
        vector(sunlight, "Side", side);
        sunlight.getUniform("Length").set((float) length);
        sunlight.getUniform("Time").set(time);
        sunlight.getUniform("ShaftIndex").set((float) index);
        draw(mesh, sunlight);
        return true;
    }

    private static void bakeSunlight(VertexConsumer out) {
        double[] edges = {-1, -.6, 0, .6, 1}, opacity = {0, .3, 1, .3, 0};
        for (int section = 0; section < 24; section++) for (int edge = 0; edge < 4; edge++) {
            for (int corner = 0; corner < 4; corner++) {
                int e = edge + (corner >= 2 ? 1 : 0);
                double t = (section + (corner == 1 || corner == 2 ? 1 : 0)) / 24D;
                descriptor(out, edges[e], t, section / 24D, (float) opacity[e], 0, 0, 255);
            }
        }
    }

    static boolean beam(Vec3d start, Vec3d end, Vec3d axis, Vec3d side, Vec3d camera,
                        int rgb, float intensity, double width, BeamEndpointClip startClip, BeamEndpointClip endClip) {
        return halo(Profile.BEAM, start, end, axis, side, Vec3d.ZERO, camera, rgb, intensity, width, startClip, endClip);
    }

    static boolean saber(net.askcraft.justifylasers.laser.LaserBeamTrace ray, int rgb, Vec3d camera) {
        Vec3d axis = ray.axis(), midpoint = ray.start().add(ray.end()).multiply(.5);
        Vec3d side = axis.crossProduct(camera.subtract(midpoint));
        if (side.lengthSquared() < 1e-8) side = axis.crossProduct(Math.abs(axis.y) > .9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0));
        side = side.normalize();
        Vec3d cap = camera.subtract(midpoint).normalize().crossProduct(side).normalize();
        return halo(ray.hasBlockHit() ? Profile.SABER_CLIPPED : Profile.SABER, ray.start(), ray.end(), axis, side, cap,
                camera, rgb, 1, SaberBladeRenderer.WIDTH * Math.min(1, ray.length()/.3), null, null);
    }

    private static boolean halo(Profile profile, Vec3d start, Vec3d end, Vec3d axis, Vec3d side, Vec3d cap, Vec3d camera,
                                int rgb, float intensity, double width, BeamEndpointClip a, BeamEndpointClip b) {
        if (!enabled()) return false;
        int bands = LaserBeamProfile.COLOR_RADII.length + LaserBeamProfile.CORE_RADII.length - 2;
        int vertices = bands * 8 * (profile == Profile.BEAM ? 1 : profile == Profile.SABER ? 17 : 9);
        var mesh = GpuGeometryCache.get(profile, vertices * 30L,
                () -> RenderVersion.staticMesh(VertexFormats.POSITION_TEXTURE_COLOR, out -> bakeHalo(out, profile)));
        if (mesh == null) return false;
        vector(halo,"Start",start.subtract(camera)); vector(halo,"End",end.subtract(camera));
        vector(halo,"Side",side); vector(halo,"CapAxis",cap);
        vector(halo,"Surface",axis.crossProduct(side).multiply(-(LaserBeamProfile.EMISSION_RADIUS+.002)*width));
        color(halo,"BeamColor",rgb);
        halo.getUniform("Width").set((float)width);
        halo.getUniform("Intensity").set(intensity);
        clip(halo,"Start",a,camera); clip(halo,"End",b,camera);
        draw(mesh,halo);
        return true;
    }

    private static void bakeHalo(VertexConsumer out, Profile profile) {
        for (boolean core : new boolean[]{false,true}) {
            double[] radii = core ? LaserBeamProfile.CORE_RADII : LaserBeamProfile.COLOR_RADII;
            int[] alpha = core ? LaserBeamProfile.CORE_ALPHA : LaserBeamProfile.COLOR_ALPHA;
            for(int ring=0;ring<radii.length-1;ring++) for(int sign:new int[]{-1,1}) {
                haloVertex(out,radii,alpha,ring,sign,0,0,0,core,profile);
                haloVertex(out,radii,alpha,ring,sign,1,0,0,core,profile);
                haloVertex(out,radii,alpha,ring+1,sign,1,0,0,core,profile);
                haloVertex(out,radii,alpha,ring+1,sign,0,0,0,core,profile);
                if(profile==Profile.BEAM)continue;
                for(int tip:new int[]{-1,1}) {
                    if(tip==1&&profile==Profile.SABER_CLIPPED)continue;
                    for(int arc=0;arc<8;arc++) {
                        double a=arc*Math.PI/16,b=(arc+1)*Math.PI/16;
                        haloVertex(out,radii,alpha,ring,sign,tip==1?1:0,a,tip,core,profile);
                        haloVertex(out,radii,alpha,ring,sign,tip==1?1:0,b,tip,core,profile);
                        haloVertex(out,radii,alpha,ring+1,sign,tip==1?1:0,b,tip,core,profile);
                        haloVertex(out,radii,alpha,ring+1,sign,tip==1?1:0,a,tip,core,profile);
                    }
                }
            }
        }
    }

    private static void haloVertex(VertexConsumer out,double[] radii,int[] alpha,int ring,int sign,int end,
                                    double angle,int tip,boolean core,Profile profile) {
        double brightness=core?1:LaserBeamProfile.COLOR_BRIGHTNESS[ring];
        int opacity=profile==Profile.BEAM?alpha[ring]:(int)(alpha[ring]*brightness);
        descriptor(out,radii[ring]*Math.cos(angle)*sign,end,radii[ring]*Math.sin(angle)*tip,
                profile==Profile.BEAM?(float)brightness:1,0,core?1:0,opacity);
    }

    static void descriptor(VertexConsumer out,double x,double y,double z,float u,float v,int palette,int alpha) {
        RenderVersion.endVertex(out.vertex((float)x,(float)y,(float)z).texture(u,v).color(palette,0,0,alpha));
    }
    static void vector(ShaderProgram shader,String uniform,Vec3d value) {
        shader.getUniform(uniform).set((float)value.x,(float)value.y,(float)value.z);
    }
    static void color(ShaderProgram shader,String uniform,int rgb) {
        shader.getUniform(uniform).set((rgb>>16&255)/255F,(rgb>>8&255)/255F,(rgb&255)/255F);
    }
    private static void clip(ShaderProgram shader,String end,BeamEndpointClip clip,Vec3d camera) {
        if(clip==null) {
            shader.getUniform(end+"Plane").set(0F,0F,0F,0F);
            shader.getUniform(end+"Push").set(0F,0F,0F);
        } else {
            var normal=clip.normal();
            shader.getUniform(end+"Plane").set((float)normal.x,(float)normal.y,(float)normal.z,(float)-normal.dotProduct(clip.point().subtract(camera)));
            vector(shader,end+"Push",clip.inward().multiply(1/normal.dotProduct(clip.inward())));
        }
    }
    static void draw(VertexBuffer mesh,ShaderProgram shader) {
        mesh.bind();
        try { mesh.draw(RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),shader); }
        finally { VertexBuffer.unbind(); }
    }
    private GpuLightEffects() { }
}
