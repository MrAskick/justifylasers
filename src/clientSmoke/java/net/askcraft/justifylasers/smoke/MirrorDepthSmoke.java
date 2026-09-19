package net.askcraft.justifylasers.smoke;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Production-client assertion: actual GPU terrain, separate depths and view-dependent parallax. */
final class MirrorDepthSmoke {
    static double[] inspect(MinecraftClient client, BlockPos pos, String name) {
        try {
            if (Boolean.getBoolean("justifylasers.smokeMirrorShaders")) {
                var views = net.askcraft.justifylasers.client.compat.IrisMirrorPass.class.getDeclaredField("SHADER_VIEWS"); views.setAccessible(true);
                if (((java.util.Map<?,?>)views.get(null)).isEmpty()) throw new AssertionError("Shader reflection test fell back to vanilla lighting");
            }
            var captures=MirrorRenderer.class.getDeclaredField("CAPTURES"); captures.setAccessible(true);
            Object capture=((java.util.Map<?,?>)captures.get(null)).get(pos);
            if(capture==null) throw new AssertionError("No reflected world was captured");
            var targetMethod=capture.getClass().getDeclaredMethod("target"); targetMethod.setAccessible(true);
            var depthMethod=capture.getClass().getDeclaredMethod("depthToMain"); depthMethod.setAccessible(true);
            Framebuffer target=(Framebuffer)targetMethod.invoke(capture);
            Matrix4f depthTransform=(Matrix4f)depthMethod.invoke(capture);
            ScreenshotRecorder.saveScreenshot(client.runDirectory,"prompt5-capture-"+name+".png",target,text -> { });
            var depths=MemoryUtil.memAllocFloat(target.textureWidth*target.textureHeight);
            int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try(var image=ScreenshotRecorder.takeScreenshot(target)) {
                RenderSystem.bindTexture(target.getDepthAttachment());
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
                long[] count=new long[2]; double[] xSum=new double[2], depthSum=new double[2];
                for(int y=0;y<image.getHeight();y+=2) for(int x=0;x<image.getWidth();x+=2) {
                    // Kappa reflects the colored targets on the floor as well. Those pixels have
                    // floor depth, so measure the actual targets above their reflected images.
                    if (Boolean.getBoolean("justifylasers.smokeMirrorShaders") && y > image.getHeight() * .56) continue;
                    int color=image.getColor(x,y),r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;
                    int index=r>40 && r>g*2 && r>b*2 ? 0 : b>40 && b>r*2 && b>g*1.5 ? 1 : -1;
                    if(index<0) continue;
                    float d=depths.get((image.getHeight()-1-y)*image.getWidth()+x);
                    if(d>=.999999F) continue;
                    var projected=depthTransform.transform(new Vector4f((x+.5F)/image.getWidth()*2-1,1-(y+.5F)/image.getHeight()*2,d*2-1,1));
                    count[index]++; xSum[index]+=(double)x/image.getWidth(); depthSum[index]+=projected.z/projected.w*.5+.5;
                }
                if(count[0]<20 || count[1]<20) throw new AssertionError("Missing reflected terrain: red="+count[0]+" blue="+count[1]);
                double near=depthSum[0]/count[0],far=depthSum[1]/count[1];
                if(far<=near+.001) throw new AssertionError("Reflected scene has no depth: near="+near+" far="+far);
                return new double[]{xSum[0]/count[0],xSum[1]/count[1]};
            } finally {
                RenderSystem.bindTexture(bound); MemoryUtil.memFree(depths);
            }
        } catch(ReflectiveOperationException exception) { throw new AssertionError("Cannot inspect planar capture",exception); }
    }
    static void assertComposite(MinecraftClient client,boolean occluded) {
        try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            int red=0; long brightness=0;
            for(int y=0;y<image.getHeight();y+=2) for(int x=0;x<image.getWidth();x+=2) {
                int color=image.getColor(x,y),r=color&255,g=(color>>>8)&255,b=(color>>>16)&255;
                brightness+=r+g+b;
                if(r>40 && r>g*2 && r>b*2) red++;
            }
            if(brightness<image.getWidth()*image.getHeight()) throw new AssertionError("Black main framebuffer during mirror verification");
            if(occluded ? red>8 : red<30) throw new AssertionError("Mirror composite occlusion="+occluded+" red pixels="+red);
        }
    }
    private MirrorDepthSmoke() { }
}
