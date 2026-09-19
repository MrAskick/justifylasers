package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Complete the offscreen pass before Iris begins the main world pipeline. */
@Mixin(GameRenderer.class)
public abstract class MirrorWorldRenderMixin {
    @Unique private Matrix4f justifylasers$cullingProjection;

    @ModifyArg(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;)V"),index=2)
    private Matrix4f justifylasers$rememberCullingProjection(Matrix4f projection) {
        justifylasers$cullingProjection = new Matrix4f(projection);
        return projection;
    }

    @Redirect(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"))
    private void justifylasers$mirrorWorld(WorldRenderer world, MatrixStack view, float delta, long limit, boolean outline, Camera camera,
            GameRenderer renderer, LightmapTextureManager lightmap, Matrix4f projection) {
        var access = (MirrorFrustumAccess) world;
        var mainFrustum = access.justifylasers$frustum();
        try {
            MirrorRenderer.prepare(camera, view, projection, delta,
                    (eye, reflectedView, reflectedProjection) -> {
                        world.setupFrustum(reflectedView, eye.getPos(), justifylasers$cullingProjection);
                        world.render(reflectedView, delta, limit, false, eye, renderer, lightmap, reflectedProjection);
                    });
        } finally {
            access.justifylasers$frustum(mainFrustum);
        }
        world.render(view, delta, limit, outline, camera, renderer, lightmap, projection);
    }
}
