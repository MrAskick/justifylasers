package net.askcraft.justifylasers.smoke.mixin;

import net.askcraft.justifylasers.smoke.ArtifactWorldSmoke;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntityRenderDispatcher.class)
abstract class ArtifactBlockEntityMixin {
    private static final java.util.Set<String> logged = new java.util.HashSet<>();
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void isolate(net.minecraft.block.entity.BlockEntity entity, float delta,
                         net.minecraft.client.util.math.MatrixStack matrices,
                         net.minecraft.client.render.VertexConsumerProvider consumers, CallbackInfo ci) {
        int mask = ArtifactWorldSmoke.mask;
        String name = entity.getClass().getSimpleName();
        if (mask == 15 && logged.add(entity.getClass().getName()))
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("ARTIFACT_RENDER_CLASS {}", entity.getClass().getName());
        if (mask == 1 || mask == 5 && name.contains("LightBridge")
                || mask == 6 && name.contains("IndustrialMachine")
                || mask == 7 && (name.contains("Component") || name.contains("Solar"))
                || mask == 8 && name.contains("Printed")
                || mask == 9 && name.contains("Optic")
                || mask == 10 && name.contains("LaserPart")
                || mask == 11 && name.contains("Turret")
                || mask == 12 && (name.contains("Emitter") || name.contains("Receiver"))
                || mask == 13 && !net.askcraft.justifylasers.client.compat.IrisCompatibility.isRenderingShadowPass()
                || mask == 14 && net.askcraft.justifylasers.client.compat.IrisCompatibility.isRenderingShadowPass()
                || mask == 15 && !entity.getClass().getName().contains("justifylasers")
                || mask == 16 && entity.getClass().getName().contains("justifylasers")
                || mask == 20 && name.contains("EnergyCube")
                || mask == 21 && (name.contains("Pipe") || name.contains("Tank") || name.contains("Cable") || name.contains("Transporter"))
                || mask == 22 && entity.getClass().getName().startsWith("appeng")
                || mask == 23 && entity.getClass().getName().startsWith("net.minecraft")) ci.cancel();
    }
}
