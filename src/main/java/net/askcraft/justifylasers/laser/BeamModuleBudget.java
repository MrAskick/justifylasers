package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Charges one physical incident beam, not each original source contributing to it. */
final class BeamModuleBudget {
    record Port(BlockPos pos, long x, long y, long z, long dx, long dy, long dz) {
        Port(BlockPos pos, Vec3d entry, Vec3d axis) {
            this(pos.toImmutable(), Math.round(entry.x * 1e5), Math.round(entry.y * 1e5), Math.round(entry.z * 1e5),
                    Math.round(axis.x * 1e5), Math.round(axis.y * 1e5), Math.round(axis.z * 1e5));
        }
    }
    private record Input(Port port, double flux, List<Port> upstream) { }
    private final List<Input> inputs = new ArrayList<>();
    private final Map<Port, Long> costs = new LinkedHashMap<>();
    private final Map<LaserBeamTrace, List<Port>> routes = new IdentityHashMap<>();
    private Map<Port, Double> retention = Map.of();

    List<Port> pass(BlockPos pos, Vec3d entry, Vec3d axis, long cost, double flux, List<Port> upstream) {
        var port = new Port(pos, entry, axis);
        costs.put(port, cost);
        inputs.add(new Input(port, flux, upstream));
        var path = new ArrayList<>(upstream); path.add(port);
        return List.copyOf(path);
    }

    void remember(LaserBeamTrace ray, List<Port> route) { routes.put(ray, route); }

    void solve() {
        // Geometry is traced once. These passes propagate only scalar losses through finite routes.
        // Start with an upper bound and converge downward, including rejoined splitter branches.
        for (int iteration = 0; iteration < 128; iteration++) {
            var available = new HashMap<Port, Double>();
            for (var input : inputs) available.merge(input.port, input.flux * fraction(input.upstream), Double::sum);
            var next = new HashMap<Port, Double>();
            double delta = 0;
            for (var entry : costs.entrySet()) {
                double flux = available.getOrDefault(entry.getKey(), 0.0);
                double value = flux <= entry.getValue() ? 0 : 1 - entry.getValue() / flux;
                next.put(entry.getKey(), value);
                delta = Math.max(delta, Math.abs(value - retention.getOrDefault(entry.getKey(), 1.0)));
            }
            retention = next;
            if (delta < 1e-12) return;
        }
        // A pathological feedback graph must not obtain energy from an unconverged estimate.
        retention.replaceAll((port, value) -> 0.0);
    }

    private double fraction(List<Port> route) {
        double value = 1;
        for (var port : route) value *= retention.getOrDefault(port, 1.0);
        return value;
    }

    LaserBeamPath apply(LaserBeamPath path) {
        var rays = new ArrayList<LaserBeamTrace>();
        for (var ray : path.segments()) {
            var paid = ray.paid(fraction(routes.getOrDefault(ray, List.of())));
            if (paid.power() > 1e-12) rays.add(paid);
        }
        return LaserBeamPath.coalesce(rays);
    }
}
