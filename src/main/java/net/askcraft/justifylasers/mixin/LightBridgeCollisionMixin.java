package net.askcraft.justifylasers.mixin;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.CollisionView;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
public abstract class LightBridgeCollisionMixin implements CollisionView {
    // Override the inherited default on World: Forge 47's Mixin cannot inject into an interface.
    @Override public Iterable<VoxelShape> getBlockCollisions(Entity entity, Box box) {
        World world = (World) (Object) this;
        Iterable<VoxelShape> blocks = () -> new BlockCollisionSpliterator<>(world, entity, box, false, (pos, shape) -> shape);
        return LightBridgeNetwork.appendCollisions(world, box, blocks);
    }
}
