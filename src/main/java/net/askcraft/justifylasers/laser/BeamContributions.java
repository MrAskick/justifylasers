package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coalesces coincident contributions without losing the individual server-side energy budgets. */
public final class BeamContributions {
    public record Beam(LaserBeamTrace trace, BlockPos source, float width, boolean emission, double flux, long ticks) { }

    public static List<Beam> merge(List<Beam> contributions) {
        Map<Line, List<Beam>> lines = new LinkedHashMap<>();
        for (var beam : contributions) {
            var ray = beam.trace;
            lines.computeIfAbsent(new Line(ray.start(), ray.axis()), ignored -> new ArrayList<>()).add(beam);
        }
        List<Beam> merged = new ArrayList<>();
        for (var beams : lines.values()) {
            beams.sort(Comparator.comparingDouble(beam -> beam.trace.length()));
            double from = 0;
            for (int i = 0; i < beams.size(); i++) {
                double to = beams.get(i).trace.length();
                if (to - from <= 1e-6) continue;
                var color = new LightMixture();
                float width = 0;
                boolean emission = false;
                for (int j = i; j < beams.size(); j++) {
                    var beam = beams.get(j);
                    color.add(beam.trace.rgb(), beam.flux);
                    width = Math.max(width, beam.width);
                    emission |= beam.emission;
                }
                var first = beams.get(0);
                var ending = beams.get(i).trace;
                Vec3d start = first.trace.start(), axis = first.trace.axis();
                var ray = new LaserBeamTrace(start.add(axis.multiply(from)), start.add(axis.multiply(to)), ending.direction(),
                        ending.hitBlock(), ending.hitSide(), color.rgb(), 1, first.trace.combinedBy());
                merged.add(new Beam(ray, first.source, width, emission, color.weight(), first.ticks));
                from = to;
            }
        }
        return List.copyOf(merged);
    }

    private record Line(long x, long y, long z, long dx, long dy, long dz) {
        Line(Vec3d start, Vec3d axis) {
            this(Math.round(start.x * 1e6), Math.round(start.y * 1e6), Math.round(start.z * 1e6),
                    Math.round(axis.x * 1e6), Math.round(axis.y * 1e6), Math.round(axis.z * 1e6));
        }
    }
    private BeamContributions() { }
}
