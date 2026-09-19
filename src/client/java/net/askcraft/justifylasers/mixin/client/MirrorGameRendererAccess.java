package net.askcraft.justifylasers.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface MirrorGameRendererAccess {
    @Mutable @Accessor("camera") void justifylasers$camera(Camera camera);
}
