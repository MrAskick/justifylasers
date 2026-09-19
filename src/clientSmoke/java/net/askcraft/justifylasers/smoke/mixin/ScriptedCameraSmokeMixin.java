package net.askcraft.justifylasers.smoke.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class ScriptedCameraSmokeMixin {
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void justifylasers$keepScriptedCamera(CallbackInfo ci) {
        // Desktop mouse motion must not rotate the camera between GPU parallax measurements.
        // The fixture still sends its scripted mirror-drag input through MirrorControls.
        if (Boolean.getBoolean("justifylasers.smokePrompt5")) ci.cancel();
    }
}
