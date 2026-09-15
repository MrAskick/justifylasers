package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Closed mineral solids. Layout clearance includes the full rotated silhouette, including the leaning tips. */
final class OreInclusions {
    static final double EDGE_CLEARANCE = .55, SHARD_CLEARANCE = .22;
    record Facet(List<Vec3d> vertices, List<ComponentAtlas.Uv> uv, boolean buried) { }
    record Shard(Direction face, List<Vec3d> footprint, List<Facet> facets, double height) { }

    static List<Shard> build(String mineral, int variant) {
        var result = new ArrayList<Shard>();
        for (Direction face : Direction.values()) {
            var random = new Random(0x4F524553L + mineral.hashCode() * 31L + variant * 7919L + face.getId() * 104729L);
            var placed = new ArrayList<Shard>();
            double[][] anchors = {{6,9,2.8,5.5,-22,2.7},{12,5,2.5,4.1,35,2.1},{2.7,3.6,2.1,2.8,-25,1.5}};
            for (double[] anchor : anchors) {
                Shard accepted = null;
                for (int attempt = 0; attempt < 64 && accepted == null; attempt++) {
                    var shard = shard(mineral, face, variant, anchor[0], anchor[1], anchor[2], anchor[3],
                            anchor[4] + (random.nextDouble() - .5) * 14, anchor[5], true);
                    if (fits(shard, placed)) accepted = shard;
                }
                if (accepted == null) throw new IllegalStateException("Overlapping ore anchor: " + mineral + "/" + variant + "/" + face);
                placed.add(accepted);
            }
            for (int chip = 0; chip < 9; chip++) {
                Shard accepted = null;
                for (int attempt = 0; attempt < 512 && accepted == null; attempt++) {
                    double width = .65 + random.nextDouble() * .75, length = .8 + random.nextDouble() * 1.15;
                    var shard = shard(mineral, face, variant, 1 + random.nextDouble() * 14, 1 + random.nextDouble() * 14,
                            width, length, random.nextDouble() * 180, .25 + random.nextDouble() * .55, false);
                    if (fits(shard, placed)) accepted = shard;
                }
                if (accepted == null) throw new IllegalStateException("No safe ore-chip position: " + mineral + "/" + variant + "/" + face);
                placed.add(accepted);
            }
            result.addAll(placed);
        }
        return List.copyOf(result);
    }

    private static boolean fits(Shard shard, List<Shard> others) {
        return shard.footprint().stream().allMatch(p -> p.x >= EDGE_CLEARANCE && p.x <= 16 - EDGE_CLEARANCE
                && p.y >= EDGE_CLEARANCE && p.y <= 16 - EDGE_CLEARANCE)
                && others.stream().allMatch(other -> separated(shard.footprint(), other.footprint(), SHARD_CLEARANCE));
    }

    static boolean separated(List<Vec3d> a, List<Vec3d> b, double gap) {
        for (var polygon : List.of(a, b)) for (int i = 0; i < polygon.size(); i++) {
            var edge = polygon.get((i + 1) % polygon.size()).subtract(polygon.get(i));
            var axis = new Vec3d(-edge.y, edge.x, 0).normalize();
            double minA = Double.POSITIVE_INFINITY, maxA = Double.NEGATIVE_INFINITY;
            double minB = Double.POSITIVE_INFINITY, maxB = Double.NEGATIVE_INFINITY;
            for (var p : a) { double d = p.dotProduct(axis); minA = Math.min(minA, d); maxA = Math.max(maxA, d); }
            for (var p : b) { double d = p.dotProduct(axis); minB = Math.min(minB, d); maxB = Math.max(maxB, d); }
            if (maxA + gap <= minB || maxB + gap <= minA) return true;
        }
        return false;
    }

    private static Shard shard(String mineral, Direction face, int variant, double x, double y, double width,
                               double length, double angle, double height, boolean large) {
        var rings = new ArrayList<List<Vec3d>>();
        double lean = large ? length * .13 : 0;
        boolean crystal = mineral.equals("photonite");
        double bevel = large ? crystal ? .32 : .12 : 0;
        double slope = large ? height * (crystal ? .65 : .45) : 0;
        rings.add(ring(width, length, bevel, 1, -.035, 0, lean, height));
        rings.add(ring(width, length, bevel, large ? 1 : .76, height - slope / 2, slope, lean, height));
        var localFaces = new ArrayList<List<Vec3d>>();
        var bottom = new ArrayList<>(rings.get(0));
        Collections.reverse(bottom);
        localFaces.add(bottom);
        for (int level = 1; level < rings.size(); level++) {
            var lower = rings.get(level - 1); var upper = rings.get(level);
            for (int i = 0; i < lower.size(); i++) {
                int next = (i + 1) % lower.size();
                localFaces.add(List.of(lower.get(i), lower.get(next), upper.get(next), upper.get(i)));
            }
        }
        localFaces.add(rings.get(rings.size() - 1));
        var atlas = ComponentAtlas.load(mineral, "base");
        var min = new Vec3d(-width / 2, -length / 2, -.035);
        var max = new Vec3d(width / 2, length / 2 + lean, height);
        double radians = Math.toRadians(angle);
        int quarter = (variant + face.getId()) & 3;
        var facets = new ArrayList<Facet>();
        for (int index = 0; index < localFaces.size(); index++) {
            var points = localFaces.get(index);
            var normal = points.get(1).subtract(points.get(0)).crossProduct(points.get(2).subtract(points.get(0))).normalize();
            String material = index == localFaces.size() - 1 ? "facet" : large && index % 2 == 0 ? "tip" : "edge";
            var region = atlas.region(material);
            facets.add(new Facet(points.stream().map(p -> onFace(face, place(p, x, y, radians, quarter))).toList(),
                    points.stream().map(p -> region.project(p, normal, min, max, true, true)).toList(), index == 0));
        }
        // An oriented box enclosing every ring is deliberately conservative at both beveled and leaning corners.
        var footprint = List.of(new Vec3d(min.x, min.y, 0), new Vec3d(max.x, min.y, 0),
                new Vec3d(max.x, max.y, 0), new Vec3d(min.x, max.y, 0)).stream()
                .map(p -> place(p, x, y, radians, quarter)).toList();
        return new Shard(face, footprint, List.copyOf(facets), height);
    }

    private static List<Vec3d> ring(double width, double length, double bevel, double scale, double z,
                                   double slope, double lean, double height) {
        double x = width * scale / 2, y = length * scale / 2, cut = Math.min(x, y) * bevel;
        double[][] corners = bevel > 0 ? new double[][]{{-x+cut,-y},{x-cut,-y},{x,-y+cut},{x,y-cut},
                {x-cut,y},{-x+cut,y},{-x,y-cut},{-x,-y+cut}} : new double[][]{{-x,-y},{x,-y},{x,y},{-x,y}};
        var ring = new ArrayList<Vec3d>();
        for (double[] p : corners) {
            double depth = z + p[1] / length * slope;
            ring.add(new Vec3d(p[0], p[1] + (depth + .035) / (height + .035) * lean, depth));
        }
        return List.copyOf(ring);
    }

    private static Vec3d place(Vec3d p, double x, double y, double angle, int quarter) {
        double u = p.x * Math.cos(angle) - p.y * Math.sin(angle) + x;
        double v = p.x * Math.sin(angle) + p.y * Math.cos(angle) + y;
        return switch (quarter) {
            case 1 -> new Vec3d(16-v,u,p.z);
            case 2 -> new Vec3d(16-u,16-v,p.z);
            case 3 -> new Vec3d(v,16-u,p.z);
            default -> new Vec3d(u,v,p.z);
        };
    }

    static Vec3d onFace(Direction face, Vec3d p) {
        return switch (face) {
            case NORTH -> new Vec3d(16-p.x,p.y,-p.z);
            case SOUTH -> new Vec3d(p.x,p.y,16+p.z);
            case WEST -> new Vec3d(-p.z,p.y,p.x);
            case EAST -> new Vec3d(16+p.z,p.y,16-p.x);
            case UP -> new Vec3d(p.x,16+p.z,16-p.y);
            case DOWN -> new Vec3d(p.x,-p.z,p.y);
        };
    }

    private OreInclusions() { }
}
