package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class ScorchGeometry {
    public static final double SURFACE_OFFSET = 0.002D;
    private static final double[] SOOT_WIDTH = {0, 0.23D, 0.42D, 0.7D, 1};
    private static final int[] SOOT_COLOR = {0x090504, 0x100805, 0x24130B, 0x34241A, 0x34241A};
    private static final int[] SOOT_ALPHA = {245, 235, 158, 48, 0};
    private static final double[] HEAT_WIDTH = {0, 0.09D, 0.17D, 0.28D, 0.46D, 0.7D};
    private static final int[] HEAT_COLOR = {0xFFF4BD, 0xFFF0A0, 0xFFB72C, 0xFF5B08, 0xCB2603, 0x8E1902};
    private static final int[] HEAT_ALPHA = {255, 255, 245, 200, 64, 0};

    public static double radius(double widthScale) {
        return MathHelper.clamp(widthScale * 0.095D, 0.012D, 0.85D);
    }

    public static List<Patch> create(World world, Vec3d from, Vec3d to, Direction face, double radius) {
        Basis basis = basis(face);
        double plane = to.dotProduct(basis.normal);
        Point a = new Point(from.dotProduct(basis.u), from.dotProduct(basis.v));
        Point b = new Point(to.dotProduct(basis.u), to.dotProduct(basis.v));
        List<List<PointVertex>> soot = stroke(a, b, radius, SOOT_WIDTH, SOOT_COLOR, SOOT_ALPHA);
        List<List<PointVertex>> heat = stroke(a, b, radius, HEAT_WIDTH, HEAT_COLOR, HEAT_ALPHA);
        List<List<PointVertex>> emission = stroke(a, b, radius,
                new double[]{0, 0.11D}, new int[]{0xFFFFFF, 0xFFFFFF}, new int[]{255, 255});
        List<Patch> result = new ArrayList<>();
        Box area = new Box(from, to).expand(radius * 1.2D);
        for (BlockPos mutable : BlockPos.iterate(BlockPos.ofFloored(area.minX, area.minY, area.minZ),
                BlockPos.ofFloored(area.maxX, area.maxY, area.maxZ))) {
            if (!world.isChunkLoaded(mutable)) {
                continue;
            }
            BlockState state = world.getBlockState(mutable);
            if (state.isAir() || state.getBlock() instanceof LaserReceiverBlock && state.get(LaserReceiverBlock.FACING) == face) {
                continue;
            }
            BlockPos position = mutable.toImmutable();
            Vec3d origin = Vec3d.of(position);
            List<Vertex> sootVertices = new ArrayList<>();
            List<Vertex> heatVertices = new ArrayList<>();
            List<Vertex> emissionVertices = new ArrayList<>();
            for (Box localBox : state.getCollisionShape(world, position, ShapeContext.absent()).getBoundingBoxes()) {
                Box box = localBox.offset(position);
                double facePlane = face.getDirection() == Direction.AxisDirection.POSITIVE
                        ? box.getMax(face.getAxis()) : -box.getMin(face.getAxis());
                if (Math.abs(facePlane - plane) > 0.0001D) {
                    continue;
                }
                Vec3d min = new Vec3d(box.minX, box.minY, box.minZ);
                Vec3d max = new Vec3d(box.maxX, box.maxY, box.maxZ);
                Rect rectangle = new Rect(Math.min(min.dotProduct(basis.u), max.dotProduct(basis.u)),
                        Math.min(min.dotProduct(basis.v), max.dotProduct(basis.v)),
                        Math.max(min.dotProduct(basis.u), max.dotProduct(basis.u)),
                        Math.max(min.dotProduct(basis.v), max.dotProduct(basis.v)));
                clip(soot, rectangle, basis, plane, origin, sootVertices);
                clip(heat, rectangle, basis, plane, origin, heatVertices);
                clip(emission, rectangle, basis, plane, origin, emissionVertices);
            }
            if (!sootVertices.isEmpty()) {
                result.add(new Patch(position, state, face, List.copyOf(sootVertices),
                        List.copyOf(heatVertices), List.copyOf(emissionVertices)));
            }
        }
        return List.copyOf(result);
    }

    private static Basis basis(Direction face) {
        Vec3d normal = Vec3d.of(face.getVector());
        Vec3d u = face.getAxis() == Direction.Axis.X ? new Vec3d(0, 0, 1) : new Vec3d(1, 0, 0);
        return new Basis(normal, u, normal.crossProduct(u));
    }

    private static List<List<PointVertex>> stroke(Point a, Point b, double radius, double[] widths, int[] colors, int[] alpha) {
        List<List<PointVertex>> polygons = new ArrayList<>();
        double length = Math.hypot(b.u - a.u, b.v - a.v);
        double dx = length > 1.0E-6D ? (b.u - a.u) / length : 1;
        double dy = length > 1.0E-6D ? (b.v - a.v) / length : 0;
        if (length > 1.0E-6D) {
            int sections = Math.max(1, (int) Math.ceil(length / Math.max(0.04D, radius)));
            for (int section = 0; section < sections; section++) {
                Point start = a.lerp(b, section / (double) sections);
                Point end = a.lerp(b, (section + 1) / (double) sections);
                for (int ring = 0; ring < widths.length - 1; ring++) {
                    for (int sign : new int[]{-1, 1}) {
                        polygons.add(List.of(offset(start, -dy, dx, radius * widths[ring] * sign, colors[ring], alpha[ring]),
                                offset(end, -dy, dx, radius * widths[ring] * sign, colors[ring], alpha[ring]),
                                offset(end, -dy, dx, radius * widths[ring + 1] * sign, colors[ring + 1], alpha[ring + 1]),
                                offset(start, -dy, dx, radius * widths[ring + 1] * sign, colors[ring + 1], alpha[ring + 1])));
                    }
                }
            }
        }
        double angle = Math.atan2(dy, dx);
        if (length > 1.0E-6D) {
            cap(polygons, a, angle + Math.PI * 0.5D, Math.PI, radius, widths, colors, alpha);
            cap(polygons, b, angle - Math.PI * 0.5D, Math.PI, radius, widths, colors, alpha);
        } else {
            cap(polygons, a, 0, Math.PI * 2, radius, widths, colors, alpha);
        }
        return polygons;
    }

    private static void cap(List<List<PointVertex>> polygons, Point center, double angle, double sweep,
                            double radius, double[] widths, int[] colors, int[] alpha) {
        int sections = sweep > Math.PI ? 20 : 10;
        for (int side = 0; side < sections; side++) {
            double a = angle + sweep * side / sections;
            double b = angle + sweep * (side + 1) / sections;
            for (int ring = 0; ring < widths.length - 1; ring++) {
                polygons.add(List.of(offset(center, Math.cos(a), Math.sin(a), radius * widths[ring], colors[ring], alpha[ring]),
                        offset(center, Math.cos(a), Math.sin(a), radius * widths[ring + 1], colors[ring + 1], alpha[ring + 1]),
                        offset(center, Math.cos(b), Math.sin(b), radius * widths[ring + 1], colors[ring + 1], alpha[ring + 1]),
                        offset(center, Math.cos(b), Math.sin(b), radius * widths[ring], colors[ring], alpha[ring])));
            }
        }
    }

    private static PointVertex offset(Point center, double x, double y, double radius, int rgb, int alpha) {
        double u = center.u + x * radius;
        double v = center.v + y * radius;
        // World-anchored fine variation keeps adjacent strokes from looking like stamped circles.
        double roughness = 1 + 0.10D * Math.sin(u * 87.13D + v * 63.71D) * Math.sin(u * 41.93D - v * 79.31D);
        return new PointVertex(center.u + x * radius * roughness, center.v + y * radius * roughness, rgb, alpha);
    }

    private static void clip(List<List<PointVertex>> polygons, Rect rectangle, Basis basis, double plane,
                             Vec3d origin, List<Vertex> output) {
        for (List<PointVertex> polygon : polygons) {
            List<PointVertex> clipped = polygon;
            for (int edge = 0; edge < 4 && !clipped.isEmpty(); edge++) {
                List<PointVertex> next = new ArrayList<>();
                PointVertex previous = clipped.get(clipped.size() - 1);
                double previousDistance = rectangle.distance(previous, edge);
                for (PointVertex current : clipped) {
                    double currentDistance = rectangle.distance(current, edge);
                    if ((currentDistance >= 0) != (previousDistance >= 0)) {
                        next.add(previous.lerp(current, previousDistance / (previousDistance - currentDistance)));
                    }
                    if (currentDistance >= 0) {
                        next.add(current);
                    }
                    previous = current;
                    previousDistance = currentDistance;
                }
                clipped = next;
            }
            for (int index = 1; index < clipped.size() - 1; index++) {
                PointVertex a = clipped.get(0);
                PointVertex b = clipped.get(index);
                PointVertex c = clipped.get(index + 1);
                double area = (b.u - a.u) * (c.v - a.v) - (b.v - a.v) * (c.u - a.u);
                if (Math.abs(area) < 1.0E-12D) {
                    continue;
                }
                // All output triangles face outward, including mirrored sides and clipped caps.
                output.add(vertex(a, basis, plane, origin));
                output.add(vertex(area > 0 ? b : c, basis, plane, origin));
                output.add(vertex(area > 0 ? c : b, basis, plane, origin));
                output.add(vertex(a, basis, plane, origin));
            }
        }
    }

    private static Vertex vertex(PointVertex point, Basis basis, double plane, Vec3d origin) {
        Vec3d position = basis.u.multiply(point.u).add(basis.v.multiply(point.v))
                .add(basis.normal.multiply(plane + SURFACE_OFFSET)).subtract(origin);
        return new Vertex(position, point.rgb, point.alpha);
    }

    public record Patch(BlockPos position, BlockState state, Direction face,
                        List<Vertex> soot, List<Vertex> heat, List<Vertex> emission) {
    }

    public record Vertex(Vec3d position, int rgb, int alpha) {
    }

    private record Basis(Vec3d normal, Vec3d u, Vec3d v) {
    }

    private record Point(double u, double v) {
        private Point lerp(Point other, double t) {
            return new Point(MathHelper.lerp(t, u, other.u), MathHelper.lerp(t, v, other.v));
        }
    }

    private record PointVertex(double u, double v, int rgb, int alpha) {
        private PointVertex lerp(PointVertex other, double t) {
            int red = (int) MathHelper.lerp(t, rgb >> 16 & 255, other.rgb >> 16 & 255);
            int green = (int) MathHelper.lerp(t, rgb >> 8 & 255, other.rgb >> 8 & 255);
            int blue = (int) MathHelper.lerp(t, rgb & 255, other.rgb & 255);
            return new PointVertex(MathHelper.lerp(t, u, other.u), MathHelper.lerp(t, v, other.v),
                    red << 16 | green << 8 | blue, (int) MathHelper.lerp(t, alpha, other.alpha));
        }
    }

    private record Rect(double minU, double minV, double maxU, double maxV) {
        private double distance(PointVertex point, int edge) {
            return switch (edge) {
                case 0 -> point.u - minU;
                case 1 -> maxU - point.u;
                case 2 -> point.v - minV;
                default -> maxV - point.v;
            };
        }
    }

    private ScorchGeometry() {
    }
}
