package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/** Lithium bypasses World's collision iterator for movement and edge sneaking. */
@Pseudo
@Mixin(targets = {
        "me.jellysquid.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper",
        "net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper"
}, remap = false)
public abstract class LithiumBridgeCollisionMixin {
    @Shadow @Final private World world;
    @Shadow @Final private Box box;
    @Unique private Iterator<VoxelShape> justifylasers$bridges;
    @Unique private boolean justifylasers$hasSyntheticShapes;

    @Inject(method = "computeNext", at = @At("HEAD"), cancellable = true, remap = false)
    private void justifylasers$includeBridge(CallbackInfoReturnable<VoxelShape> cir) {
        if (justifylasers$bridges == null) justifylasers$bridges = LightBridgeNetwork.collisions(world, box).iterator();
        if (justifylasers$bridges.hasNext()) {
            justifylasers$hasSyntheticShapes = true;
            cir.setReturnValue(justifylasers$bridges.next());
        }
    }

    @Inject(method = "collectAll", at = @At("HEAD"), cancellable = true, remap = false)
    @SuppressWarnings("unchecked")
    private void justifylasers$collectWithBridges(CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (justifylasers$bridges == null) justifylasers$bridges = LightBridgeNetwork.collisions(world, box).iterator();
        if (!justifylasers$hasSyntheticShapes && !justifylasers$bridges.hasNext()) return;
        // Lithium's terrain-only maxIndex cannot index a list containing synthetic shapes.
        // Retain its iterator/sweep, but skip that final ordering optimization for bridge queries.
        // Draining the actual iterator also preserves already-prefetched and partially consumed shapes.
        var result = new ArrayList<VoxelShape>();
        ((Iterator<VoxelShape>) (Object) this).forEachRemaining(result::add);
        cir.setReturnValue(result);
    }
}
