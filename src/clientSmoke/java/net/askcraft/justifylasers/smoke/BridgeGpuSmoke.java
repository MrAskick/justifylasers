package net.askcraft.justifylasers.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.client.render.LaserRenderLayers;
import net.askcraft.justifylasers.client.render.LightBridgeGpuRenderer;
import net.askcraft.justifylasers.client.render.GpuGeometryCache;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pixel comparisons of the production GPU program against the retained CPU renderer. */
final class BridgeGpuSmoke {
    private static final int SIZE = 512;
    private static final Class<?> GPU = LightBridgeGpuRenderer.class;
    private static final List<VertexBuffer> OLD_BUFFERS = new ArrayList<>();

    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) {
            verify(client);
            client.reloadResources();
        }
        if (tick == 70) {
            for (var buffer : OLD_BUFFERS) if (!buffer.isClosed()) throw new AssertionError("Bridge VBO survived shader reload");
            verify(client);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("BRIDGE_GPU_SMOKE_PASSED pixels=true cache=true reload=true");
            client.scheduleStop();
        }
    }

    private static void verify(MinecraftClient client) {
        GpuModelSmoke.verify(client);
        GpuEffectsSmoke.verify(client);
        try {
            Class<?> cpu = Class.forName(GPU.getPackageName() + ".LightBridgeRenderer");
            Class<?> field = Class.forName(cpu.getName() + "$Field");
            var constructor = field.getDeclaredConstructor(LightBridgeSpan.class,double.class);
            constructor.setAccessible(true);
            var queueField = cpu.getDeclaredField("QUEUED"); queueField.setAccessible(true);
            @SuppressWarnings("unchecked") var queue = (List<Object>)queueField.get(null);
            Method renderCpu = cpu.getDeclaredMethod("render",BufferBuilder.class,Vec3d.class); renderCpu.setAccessible(true);
            Method renderGpu = GPU.getDeclaredMethod("draw",LightBridgeSpan.class,double.class,Vec3d.class,Matrix4f.class,Matrix4f.class);
            renderGpu.setAccessible(true);
            Method ready = GPU.getDeclaredMethod("ready"); ready.setAccessible(true);
            if (!(boolean)ready.invoke(null)) throw new AssertionError("Bridge shader failed to load");
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            var savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            var savedSorter = RenderSystem.getVertexSorting();
            var savedShader = RenderSystem.getShader();
            float[] savedColor = RenderSystem.getShaderColor().clone();
            var target = new SimpleFramebuffer(SIZE,SIZE,true,MinecraftClient.IS_SYSTEM_MAC);
            try {
                RenderSystem.setShaderColor(1,1,1,1);
                var projection = new Matrix4f().perspective((float)Math.toRadians(55),1,.05F,1024);
                int[] colors = {0xFF1429,0x11CCFF,0x6F22ED,0xFFDD00,0xFFFFFF,0x23FF46};
                for (int i=0;i<18;i++) {
                    int section = i%6 >= 4 ? i%2 : -1;
                    int width = section < 0 ? i%3+1 : 1;
                    Direction facing = Direction.values()[i%6];
                    var pos = i>=12 ? new BlockPos(29_999_900,90,-29_999_900) : new BlockPos(8,16,-9);
                    var span = new LightBridgeSpan(pos,facing,Direction.NORTH,i%2==0,i%8,section,i%4,width,6.375,colors[i%6],100);
                    double time = i<6 ? 0 : i<12 ? 27.5 : 359_999.95;
                    var center = span.bounds().getCenter();
                    var camera = center.add(5,i%2==0 ? 7 : -7,-6);
                    var look = center.subtract(camera);
                    var view = new Matrix4f().lookAt(0,0,0,(float)look.x,(float)look.y,(float)look.z,0,1,0);
                    queue.clear(); queue.add(constructor.newInstance(span,time));
                    for (boolean occluded : new boolean[]{false,true}) {
                        try (var reference = render(target,span,time,camera,view,projection,renderCpu,renderGpu,false,occluded);
                             var actual = render(target,span,time,camera,view,projection,renderCpu,renderGpu,true,occluded)) {
                            compare(client,reference,actual,i,occluded);
                        }
                    }
                }
                queue.clear();
                cacheLimits(renderGpu,projection);
                if (GL11.glGetError()!=GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error after bridge rendering");
            } finally {
                queue.clear();
                target.delete();
                GL11.glClearDepth(1);
                RenderSystem.depthMask(true);
                RenderSystem.setProjectionMatrix(savedProjection,savedSorter);
                RenderSystem.setShader(() -> savedShader);
                RenderSystem.setShaderColor(savedColor[0],savedColor[1],savedColor[2],savedColor[3]);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);
                RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Cannot inspect bridge renderers",failure); }
    }

    private static NativeImage render(SimpleFramebuffer target,LightBridgeSpan span,double time,Vec3d camera,
                                      Matrix4f view,Matrix4f projection,Method cpu,Method gpu,boolean accelerated,boolean occluded)
            throws ReflectiveOperationException {
        GpuGeometryCache.beginFrame();
        target.setClearColor(.07F,.1F,.14F,1);
        RenderSystem.depthMask(true);
        target.clear(MinecraftClient.IS_SYSTEM_MAC);
        target.beginWrite(true);
        GL11.glClearDepth(occluded ? 0 : 1);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        RenderSystem.setProjectionMatrix(projection,VertexSorter.BY_DISTANCE);
        RenderVersion.pushModelView(view);
        LaserRenderLayers.SHADER_BEAM_HALO.startDrawing();
        try {
            if (accelerated) gpu.invoke(null,span,time,camera,view,projection);
            else {
                var buffer = RenderVersion.beginQuads(VertexFormats.POSITION_COLOR);
                cpu.invoke(null,buffer,camera);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            }
        } finally {
            LaserRenderLayers.SHADER_BEAM_HALO.endDrawing();
            RenderVersion.popModelView();
        }
        return ScreenshotRecorder.takeScreenshot(target);
    }

    private static void compare(MinecraftClient client,NativeImage cpu,NativeImage gpu,int index,boolean occluded) {
        long difference=0; int visible=0,bad=0;
        int background=cpu.getColor(0,0);
        for (int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) {
            int a=cpu.getColor(x,y),b=gpu.getColor(x,y),max=0;
            if (a!=background || b!=background) visible++;
            for (int channel=0;channel<24;channel+=8) {
                int d=Math.abs((a>>channel&255)-(b>>channel&255)); difference+=d; max=Math.max(max,d);
            }
            if(max>8)bad++;
        }
        double mean=(double)difference/Math.max(1,visible)/3;
        double fraction=(double)bad/Math.max(1,visible);
        if(index==0 && !occluded || mean>.5 || fraction>.005) {
            try {
                cpu.writeTo(client.runDirectory.toPath().resolve("bridge-reference-"+index+".png"));
                gpu.writeTo(client.runDirectory.toPath().resolve("bridge-gpu-"+index+".png"));
            } catch(java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
        if(occluded ? visible!=0 : visible<100) throw new AssertionError("Bridge visibility index="+index+" occluded="+occluded+" pixels="+visible);
        if(mean>.5 || fraction>.005) throw new AssertionError("Bridge image mismatch index="+index+" mean="+mean+" bad="+fraction);
        LoggerFactory.getLogger("justifylasers-client-smoke").info("BRIDGE_GPU_PIXELS case={} occluded={} mean={} bad={} pixels={}",index,occluded,mean,fraction,visible);
    }

    private static void cacheLimits(Method gpu,Matrix4f projection) throws ReflectiveOperationException {
        var cacheField=GpuGeometryCache.class.getDeclaredField("ENTRIES"); cacheField.setAccessible(true);
        var bytesField=GpuGeometryCache.class.getDeclaredField("bytes"); bytesField.setAccessible(true);
        var cache=(Map<?,?>)cacheField.get(null);
        var span=new LightBridgeSpan(BlockPos.ORIGIN,Direction.SOUTH,3,512.25,0xFF0000,100);
        var view=new Matrix4f();
        gpu.invoke(null,span,0,Vec3d.ZERO,view,projection);
        int count=cache.size(); long bytes=bytesField.getLong(null);
        var recolored=new LightBridgeSpan(new BlockPos(20,30,40),Direction.UP,3,512.25,0x00FF00,100);
        gpu.invoke(null,recolored,100,Vec3d.ZERO,view,projection);
        if(cache.size()!=count || bytesField.getLong(null)!=bytes) throw new AssertionError("GPU mesh not shared between colors/orientations");
        OLD_BUFFERS.clear();
        for(var mesh:cache.values()) {
            var method=mesh.getClass().getDeclaredMethod("buffer"); method.setAccessible(true);
            OLD_BUFFERS.add((VertexBuffer)method.invoke(mesh));
        }
        var settings = net.askcraft.justifylasers.client.ClientSettings.get();
        int limit = settings.gpuCacheMiB, upload = settings.gpuUploadMiB;
        try {
            settings.gpuCacheMiB = 16; settings.gpuUploadMiB = 1;
            GpuGeometryCache.beginFrame();
            if (GpuGeometryCache.residentBytes() > 16L * 1048576) throw new AssertionError("Lowering the GPU budget did not evict geometry");
            for (int i = 0; i < 8; i++) {
                GpuGeometryCache.beginFrame();
                if (!(boolean)gpu.invoke(null, span.withLength(480 + i * .017), i, Vec3d.ZERO, view, projection))
                    throw new AssertionError("First upload at the minimum GPU budget failed");
                if ((boolean)gpu.invoke(null, span.withLength(430 + i * .013), i, Vec3d.ZERO, view, projection))
                    throw new AssertionError("Upload rate did not defer uncached geometry to the CPU renderer");
                if (GpuGeometryCache.residentBytes() > 16L * 1048576) throw new AssertionError("GPU cache exceeded its minimum budget");
            }
        } finally { settings.gpuCacheMiB = limit; settings.gpuUploadMiB = upload; }
        for(int i=0;i<72;i++) gpu.invoke(null,span.withLength(.02+i*.013),i,Vec3d.ZERO,view,projection);
        if(cache.size()>2048) throw new AssertionError("Unbounded bridge GPU cache");
        for(int i=0;i<16;i++) {
            GpuGeometryCache.beginFrame();
            gpu.invoke(null,span.withLength(512+i*.01),i,Vec3d.ZERO,view,projection);
        }
        if(!OLD_BUFFERS.get(0).isClosed()) throw new AssertionError("Evicted bridge VBO was not released");
        if(bytesField.getLong(null)>64L*1024*1024) throw new AssertionError("Bridge cache exceeded 64 MiB");
        for(var mesh:cache.values()) {
            var method=mesh.getClass().getDeclaredMethod("buffer"); method.setAccessible(true);
            OLD_BUFFERS.add((VertexBuffer)method.invoke(mesh));
        }
    }
    private BridgeGpuSmoke() { }
}
