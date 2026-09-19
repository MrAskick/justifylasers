package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.IrisMirrorPass;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The reflected camera reverses winding; shadow cameras and fullscreen passes do not. */
@Pseudo
@Mixin(targets = {"net.irisshaders.iris.shadows.ShadowRenderer", "net.irisshaders.iris.pathways.FullScreenQuadRenderer"}, remap = false)
public abstract class MirrorShadowWindingMixin {
    @Unique private int justifylasers$winding;
    @Unique private boolean justifylasers$clip;

    @Inject(method = {"renderShadows", "renderQuad"}, at = @At("HEAD"), require = 0, remap = false)
    private void justifylasers$shadowWinding(CallbackInfo ci) {
        if (!IrisMirrorPass.shaders()) return;
        justifylasers$winding = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        justifylasers$clip = GL11.glIsEnabled(GL30.GL_CLIP_DISTANCE0);
        GL11.glDisable(GL30.GL_CLIP_DISTANCE0);
        GL11.glFrontFace(GL11.GL_CCW);
    }

    @Inject(method = {"renderShadows", "renderQuad"}, at = @At("RETURN"), require = 0, remap = false)
    private void justifylasers$mirrorWinding(CallbackInfo ci) {
        if (IrisMirrorPass.shaders()) {
            GL11.glFrontFace(justifylasers$winding);
            if (justifylasers$clip) GL11.glEnable(GL30.GL_CLIP_DISTANCE0);
        }
    }
}
