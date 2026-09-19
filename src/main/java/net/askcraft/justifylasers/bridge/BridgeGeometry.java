package net.askcraft.justifylasers.bridge;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Thin, oriented prisms represented by sub-pixel steps for Minecraft's axis-aligned collision solver. */
public final class BridgeGeometry {
    private BridgeGeometry() { }

    public static Box bounds(Vec3d start, Vec3d across, Vec3d forward, Vec3d normal) {
        double x0 = start.x, y0 = start.y, z0 = start.z, x1 = x0, y1 = y0, z1 = z0;
        for (Vec3d axis : new Vec3d[]{across, forward, normal}) {
            x0 += Math.min(0, axis.x); y0 += Math.min(0, axis.y); z0 += Math.min(0, axis.z);
            x1 += Math.max(0, axis.x); y1 += Math.max(0, axis.y); z1 += Math.max(0, axis.z);
        }
        // Adjacent faces must have exactly equal coordinates, including negative world coordinates.
        return new Box(snap(x0), snap(y0), snap(z0), snap(x1), snap(y1), snap(z1));
    }

    private static double snap(double value) { return Math.rint(value * 1_000_000_000) / 1_000_000_000; }

    public static boolean cardinal(Vec3d vector) {
        return Math.max(Math.abs(vector.x), Math.max(Math.abs(vector.y), Math.abs(vector.z))) > .999999;
    }

    public static List<Box> boxes(Vec3d start, Vec3d across, Vec3d forward, Vec3d normal) {
        if (cardinal(across.normalize())) return List.of(bounds(start, across, forward, normal));
        int slices = Math.max(1, (int) Math.ceil(across.length() * 48));
        Vec3d step = across.multiply(1.0 / slices);
        var boxes = new ArrayList<Box>(slices);
        for (int i = 0; i < slices; i++) boxes.add(bounds(start.add(step.multiply(i)), step, forward, normal));
        return List.copyOf(boxes);
    }
}
