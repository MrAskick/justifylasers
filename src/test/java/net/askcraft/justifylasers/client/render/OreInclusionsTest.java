package net.askcraft.justifylasers.client.render;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OreInclusionsTest {
    @Test void silhouettesStaySeparatedIncludingAfterRotation() {
        for (String mineral : List.of("photonite", "wolframite")) for (int variant = 0; variant < 4; variant++) {
            var mesh = OreInclusions.build(mineral, variant);
            assertEquals(72, mesh.size(), "Three large minerals and nine chips on each of six faces");
            for (Direction side : Direction.values()) {
                var shards = mesh.stream().filter(s -> s.face() == side).toList();
                assertEquals(12, shards.size());
                assertEquals(3, shards.stream().filter(s -> s.height() > 1).count());
                for (var shard : shards) for (var p : shard.footprint()) {
                    assertTrue(p.x >= OreInclusions.EDGE_CLEARANCE && p.x <= 16 - OreInclusions.EDGE_CLEARANCE);
                    assertTrue(p.y >= OreInclusions.EDGE_CLEARANCE && p.y <= 16 - OreInclusions.EDGE_CLEARANCE);
                }
                for (int a = 0; a < shards.size(); a++) for (int b = a + 1; b < shards.size(); b++) {
                    var first = shards.get(a).footprint(); var second = shards.get(b).footprint();
                    var overlap = area(first); overlap.intersect(area(second));
                    assertTrue(overlap.isEmpty(), mineral + "/" + variant + "/" + side + ": overlapping rotated footprints");
                    for (var point : first) for (int edge = 0; edge < second.size(); edge++) {
                        var from = second.get(edge); var to = second.get((edge + 1) % second.size());
                        assertTrue(Line2D.ptSegDist(from.x, from.y, to.x, to.y, point.x, point.y) >= OreInclusions.SHARD_CLEARANCE - 1e-8);
                    }
                }
            }
        }
    }

    @Test void everyInclusionIsAClosedConvexSolidWithPlanarOutwardFaces() {
        for (String mineral : List.of("photonite", "wolframite")) for (int variant = 0; variant < 4; variant++)
            for (var shard : OreInclusions.build(mineral, variant)) {
                var vertices = shard.facets().stream().flatMap(f -> f.vertices().stream()).distinct().toList();
                var center = vertices.stream().reduce(Vec3d.ZERO, Vec3d::add).multiply(1d / vertices.size());
                var edges = new HashMap<List<Vec3d>, Integer>();
                for (var facet : shard.facets()) {
                    var points = facet.vertices();
                    var normal = points.get(1).subtract(points.get(0)).crossProduct(points.get(2).subtract(points.get(0))).normalize();
                    assertTrue(normal.lengthSquared() > .99, "No degenerate faces");
                    assertTrue(normal.dotProduct(center.subtract(points.get(0))) < -1e-8, "Face must point outwards");
                    for (var point : points) assertEquals(0, normal.dotProduct(point.subtract(points.get(0))), 1e-8, "No twisted quads");
                    for (var point : vertices) assertTrue(normal.dotProduct(point.subtract(points.get(0))) < 1e-8, "Convex shell cannot fold into itself");
                    for (int i = 0; i < points.size(); i++) edges.merge(List.of(points.get(i), points.get((i + 1) % points.size())), 1, Integer::sum);
                }
                for (var edge : edges.entrySet()) {
                    assertEquals(1, edge.getValue());
                    assertEquals(1, edges.getOrDefault(List.of(edge.getKey().get(1), edge.getKey().get(0)), 0), "Every seam has one oppositely wound neighbor");
                }
            }
    }

    @Test void allVariantsAreStableAndPhotonicCrystalsHaveDifferentCutsFromMetal() {
        for (String mineral : List.of("photonite", "wolframite")) {
            var variants = new HashSet<List<OreInclusions.Shard>>();
            for (int variant = 0; variant < 4; variant++) {
                var first = OreInclusions.build(mineral, variant);
                assertEquals(first, OreInclusions.build(mineral, variant), "Reloading must not move the mineral deposits");
                variants.add(first);
            }
            assertEquals(4, variants.size());
        }
        assertNotEquals(OreInclusions.build("photonite", 0), OreInclusions.build("wolframite", 0));
    }

    @Test void completeSolidsStayInsideTheirFootprintsAndClearOfPerpendicularFaces() {
        for (String mineral : List.of("photonite", "wolframite")) for (int variant = 0; variant < 4; variant++)
            for (var shard : OreInclusions.build(mineral, variant)) for (var facet : shard.facets()) for (var p : facet.vertices()) {
                var local = switch (shard.face()) {
                    case NORTH -> new Vec3d(16-p.x,p.y,-p.z);
                    case SOUTH -> new Vec3d(p.x,p.y,p.z-16);
                    case WEST -> new Vec3d(p.z,p.y,-p.x);
                    case EAST -> new Vec3d(16-p.z,p.y,p.x-16);
                    case UP -> new Vec3d(p.x,16-p.z,p.y-16);
                    case DOWN -> new Vec3d(p.x,p.z,-p.y);
                };
                assertTrue(local.x >= OreInclusions.EDGE_CLEARANCE && local.x <= 16-OreInclusions.EDGE_CLEARANCE);
                assertTrue(local.y >= OreInclusions.EDGE_CLEARANCE && local.y <= 16-OreInclusions.EDGE_CLEARANCE);
                assertTrue(local.z >= -.04 && local.z <= shard.height() + 1e-8);
                for (int edge = 0; edge < shard.footprint().size(); edge++) {
                    var from = shard.footprint().get(edge); var to = shard.footprint().get((edge+1) % shard.footprint().size());
                    assertTrue((to.x-from.x)*(local.y-from.y) - (to.y-from.y)*(local.x-from.x) >= -1e-8,
                            "Every surface vertex, including the leaning tip, must fit inside the checked silhouette");
                }
            }
    }

    private static Area area(List<Vec3d> polygon) {
        var path = new Path2D.Double(); path.moveTo(polygon.get(0).x, polygon.get(0).y);
        for (int i = 1; i < polygon.size(); i++) path.lineTo(polygon.get(i).x, polygon.get(i).y);
        path.closePath(); return new Area(path);
    }
}
