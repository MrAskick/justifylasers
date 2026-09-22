package net.askcraft.justifylasers.mixin.client;

import net.askcraft.justifylasers.block.PrintedModelBlock;
import net.askcraft.justifylasers.block.LightBridgeBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ParticleManager.class)
abstract class PrintedModelParticlesMixin {
    @Redirect(method="addBlockBreakParticles",at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    private VoxelShape justifylasers$boundedDebris(BlockState state,BlockView world,BlockPos pos){
        var shape=state.getOutlineShape(world,pos);
        // Vanilla emits at least eight particles per shape box. Only debris uses the single envelope;
        // the actual outline and collision retain every printed voxel.
        return (state.getBlock() instanceof PrintedModelBlock || state.getBlock() instanceof LightBridgeBlock)
                && !shape.isEmpty() ? VoxelShapes.cuboid(shape.getBoundingBox()) : shape;
    }
}
