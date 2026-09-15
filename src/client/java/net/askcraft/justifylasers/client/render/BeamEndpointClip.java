package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

record BeamEndpointClip(Vec3d point, Vec3d normal, Vec3d inward) {
    BeamEndpointClip relativeTo(Vec3d origin) { return new BeamEndpointClip(point.subtract(origin), normal, inward); }

    static BeamEndpointClip atMirror(Vec3d point, Vec3d inward) {
        var world = MinecraftClient.getInstance().world;
        if (world == null || !(world.getBlockEntity(BlockPos.ofFloored(point)) instanceof LaserOpticBlockEntity mirror)
                || mirror.kind() != LaserOpticBlock.Kind.MIRROR) return null;
        Vec3d relative = point.subtract(Vec3d.ofCenter(mirror.getPos()));
        if (relative.lengthSquared() > 0.37 * 0.37 || Math.abs(relative.dotProduct(mirror.normal())) > 0.02) return null;
        Vec3d normal = mirror.normal();
        if (normal.dotProduct(inward) < 0) normal = normal.negate();
        return Math.abs(normal.dotProduct(inward)) < 1e-6 ? null : new BeamEndpointClip(point, normal, inward);
    }

    Vec3d clip(Vec3d vertex) {
        // A camera-facing halo can otherwise protrude through the back of a thin mirror.
        double penetration = -vertex.subtract(point).dotProduct(normal);
        return penetration > 0 ? vertex.add(inward.multiply(penetration / normal.dotProduct(inward))) : vertex;
    }
}
