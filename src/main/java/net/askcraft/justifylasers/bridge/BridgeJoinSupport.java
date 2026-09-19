package net.askcraft.justifylasers.bridge;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** A small landing surface while the server restores the optical network after login. */
public final class BridgeJoinSupport {
    public static final String KEY = "JustifyLasersBridgeLanding";
    private BridgeJoinSupport() { }

    public static NbtCompound checkpoint(ServerPlayerEntity player) {
        Box feet = player.getBoundingBox().expand(.02, 0, .02).offset(0, -.04, 0);
        for (var field : LightBridgeNetwork.fields(player.getWorld())) {
            if (!field.active() || !field.bounds().intersects(feet)) continue;
            Box bounds = field.collisionBoxes().stream().filter(box -> box.intersects(feet) && Math.abs(box.maxY - player.getY()) <= .06)
                    .max(java.util.Comparator.comparingDouble(box -> box.maxY)).orElse(null);
            if (bounds == null) continue;
            var nbt = new NbtCompound();
            nbt.putString("Dimension", player.getWorld().getRegistryKey().getValue().toString());
            nbt.putLong("Emitter", field.origin().asLong());
            nbt.putDouble("X", player.getX()); nbt.putDouble("Y", bounds.maxY); nbt.putDouble("Z", player.getZ());
            return nbt;
        }
        return new NbtCompound();
    }

    public static void restore(ServerPlayerEntity player, NbtCompound nbt) {
        if (!nbt.contains("Emitter") || !player.getWorld().getRegistryKey().getValue().toString().equals(nbt.getString("Dimension"))) return;
        Vec3d feet = new Vec3d(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z"));
        BlockPos source = BlockPos.fromLong(nbt.getLong("Emitter"));
        if (!Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)
                || player.squaredDistanceTo(feet) > 1 || source.getSquaredDistance(BlockPos.ofFloored(feet)) > 514L * 514
                || player.isSpectator() || !player.isAlive()) return;
        Box pad = new Box(feet.x - .65, feet.y - .12, feet.z - .65, feet.x + .65, feet.y, feet.z + .65);
        LightBridgeNetwork.restoreLanding(player, source, pad);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.requestTeleport(player.getX(), feet.y, player.getZ());
    }
}
