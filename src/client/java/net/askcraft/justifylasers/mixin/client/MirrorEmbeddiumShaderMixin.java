package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.compat.IrisMirrorPass;
import net.askcraft.justifylasers.platform.Platform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Oculus binds its extended terrain attributes at 1..4; Embeddium's vanilla program uses 0..3. */
@Pseudo
@Mixin(targets="me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer",remap=false)
public abstract class MirrorEmbeddiumShaderMixin {
    @ModifyArg(method="createShader",at=@At(value="INVOKE",
            target="Lme/jellysquid/mods/sodium/client/gl/shader/GlProgram$Builder;bindAttribute(Ljava/lang/String;I)Lme/jellysquid/mods/sodium/client/gl/shader/GlProgram$Builder;"),
            index=1,require=0,remap=false)
    private int justifylasers$extendedTerrainAttribute(int location) {
        return IrisMirrorPass.active() && !IrisMirrorPass.shaders() && Platform.isModLoaded("oculus") ? location+1 : location;
    }
}
