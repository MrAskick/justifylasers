package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Vec3d;

/** Object-space metal grain: one tile per block, shared by caps, bevels and panels. */
final class ModelTextureUV {
    static float u(Vec3d point, Vec3d normal, double scale) {
        return (float) ((Math.abs(normal.x) > Math.abs(normal.z) ? point.z : point.x) * scale);
    }

    static float v(Vec3d point, Vec3d normal, double scale) {
        return (float) ((Math.abs(normal.y) > 0.7 ? point.z : point.y) * scale);
    }

    private ModelTextureUV() { }
}
