package net.askcraft.justifylasers.mixin.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface MirrorFrustumAccess {
    @Accessor("frustum") Frustum justifylasers$frustum();
    @Accessor("frustum") void justifylasers$frustum(Frustum frustum);
}
