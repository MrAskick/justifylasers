package net.askcraft.justifylasers.network;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.item.LaserConfiguratorItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public record MirrorAimPacket(BlockPos pos, Hand hand, int slot, double yaw, double pitch) {
    public static final Identifier ID = JustifyLasers.id("mirror_aim");
    public MirrorAimPacket(PacketByteBuf buf) { this(buf.readBlockPos(), buf.readEnumConstant(Hand.class), buf.readByte(), buf.readDouble(), buf.readDouble()); }
    public void write(PacketByteBuf buf) { buf.writeBlockPos(pos); buf.writeEnumConstant(hand); buf.writeByte(slot); buf.writeDouble(yaw); buf.writeDouble(pitch); }
    public void apply(PlayerEntity player) {
        var world = player.getWorld();
        if (!player.isAlive() || player.isSpectator() || !player.canModifyBlocks() || !world.isChunkLoaded(pos)
                || !Double.isFinite(yaw) || !Double.isFinite(pitch) || player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 64
                || !world.canPlayerModifyAt(player, pos) || slot != (hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : 40)) return;
        var stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof LaserConfiguratorItem tool) || LaserConfiguratorItem.mode(stack) != 3
                || !player.getAbilities().creativeMode && tool.readEnergy(stack) < LaserConfiguratorItem.USE_COST
                || !(world.getBlockEntity(pos) instanceof LaserOpticBlockEntity mirror) || mirror.kind() != LaserOpticBlock.Kind.MIRROR) return;
        var hit = world.raycast(new RaycastContext(player.getEyePos(), Vec3d.ofCenter(pos), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(pos)) return;
        if (mirror.aimManually(yaw, pitch)) {
            if (!player.getAbilities().creativeMode) tool.writeEnergy(stack, tool.readEnergy(stack) - LaserConfiguratorItem.USE_COST);
            player.getInventory().markDirty();
        }
    }
}
