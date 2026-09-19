package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class MirrorWorldRenderMixin {
    @Unique private Matrix4f justifylasers$cullingProjection;

    @ModifyArg(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),index=2)
    private Matrix4f justifylasers$rememberCullingProjection(Matrix4f projection) {
        justifylasers$cullingProjection = new Matrix4f(projection);
        return projection;
    }

    @Redirect(method="renderWorld",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"))
    private void justifylasers$mirrorWorld(WorldRenderer world, RenderTickCounter ticks, boolean outline, Camera camera, GameRenderer renderer,
            LightmapTextureManager lightmap, Matrix4f view, Matrix4f projection) {
        var matrices = new MatrixStack(); matrices.multiplyPositionMatrix(view);
        var access = (MirrorFrustumAccess) world;
        var mainFrustum = access.justifylasers$frustum();
        try {
            MirrorRenderer.prepare(camera, matrices, projection, ticks.getTickDelta(false),
                    (eye, reflectedView, reflectedProjection) -> {
                        // Oblique clip planes belong to rasterization, not the camera-cube culling loop.
                        world.setupFrustum(eye.getPos(), reflectedView.peek().getPositionMatrix(), justifylasers$cullingProjection);
                        world.render(ticks, false, eye, renderer, lightmap, reflectedView.peek().getPositionMatrix(), reflectedProjection);
                    });
        } finally {
            // Vanilla deliberately uses a different, safe FOV for culling, including during world entry.
            access.justifylasers$frustum(mainFrustum);
        }
        world.render(ticks, outline, camera, renderer, lightmap, view, projection);
    }
}
