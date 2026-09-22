package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Vec3d;

/** Affine bridge basis, evaluated once per field instead of once per vertex. */
record LightBridgeTransform(Vec3d origin, Vec3d across, Vec3d forward, Vec3d up) {
    org.joml.Matrix4f matrix() {
        return new org.joml.Matrix4f(
                (float) across.x, (float) across.y, (float) across.z, 0,
                (float) up.x, (float) up.y, (float) up.z, 0,
                (float) forward.x, (float) forward.y, (float) forward.z, 0,
                (float) origin.x, (float) origin.y, (float) origin.z, 1);
    }

    static LightBridgeTransform of(LightBridgeSpan span, Vec3d camera) {
        Vec3d origin = span.point(0, 0, 0);
        return new LightBridgeTransform(origin.subtract(camera), span.point(1, 0, 0).subtract(origin),
                Vec3d.of(span.facing().getVector()), span.normal());
    }

    void vertex(BufferBuilder buffer, double x, double z, double y, int rgb, int alpha) {
        RenderVersion.positionColorVertex(buffer,
                (float) (origin.x + across.x * x + forward.x * z + up.x * y),
                (float) (origin.y + across.y * x + forward.y * z + up.y * y),
                (float) (origin.z + across.z * x + forward.z * z + up.z * y), rgb, alpha);
    }
}
