package net.askcraft.justifylasers.client.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;

record ItemModelBounds(Vec3d center, double radius) {
    static ItemModelBounds of(List<Vec3d> vertices) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Vec3d point : vertices) {
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        Vec3d center = new Vec3d((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        double radius = vertices.stream().mapToDouble(point -> point.distanceTo(center)).max().orElseThrow();
        return new ItemModelBounds(center, radius);
    }

    void fitGui(MatrixStack matrices) {
        // A bounding sphere fits under every GUI rotation, including custom resource packs.
        float scale = (float) (0.46 / radius);
        matrices.translate(0.5, 0.5, 0.5);
        matrices.scale(scale, scale, scale);
        matrices.translate(-center.x, -center.y, -center.z);
    }
}
