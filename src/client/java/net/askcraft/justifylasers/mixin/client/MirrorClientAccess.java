package net.askcraft.justifylasers.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MirrorClientAccess {
    @Mutable @Accessor("framebuffer") void justifylasers$framebuffer(Framebuffer framebuffer);
}
