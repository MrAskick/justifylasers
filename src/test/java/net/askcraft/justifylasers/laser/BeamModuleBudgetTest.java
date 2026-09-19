package net.askcraft.justifylasers.laser;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeamModuleBudgetTest {
    private static final Vec3d AXIS = new Vec3d(1, 0, 0);

    @Test void combinedSourcesPayOneCostNotEightCopies() {
        var budget = new BeamModuleBudget();
        var rays = new ArrayList<LaserBeamTrace>();
        for (int i = 0; i < 8; i++) {
            var route = budget.pass(BlockPos.ORIGIN, Vec3d.ZERO, AXIS, 608_000, 4_600_000 / 8d, List.of());
            var ray = ray(); rays.add(ray); budget.remember(ray, route);
        }
        budget.solve();
        double delivered = budget.apply(new LaserBeamPath(rays)).segments().stream().mapToDouble(r -> r.power() * 4_600_000 / 8d).sum();
        assertEquals(3_992_000, delivered, .001);
    }

    @Test void chainedModulesChargeTheRemainingCombinedFlux() {
        var budget = new BeamModuleBudget();
        var rays = new ArrayList<LaserBeamTrace>();
        for (int i = 0; i < 4; i++) {
            var first = budget.pass(BlockPos.ORIGIN, Vec3d.ZERO, AXIS, 608_000, 1_150_000, List.of());
            var second = budget.pass(BlockPos.ORIGIN.east(), AXIS, AXIS, 400_000, 1_150_000, first);
            var ray = ray(); rays.add(ray); budget.remember(ray, second);
        }
        budget.solve();
        assertEquals(3_592_000, budget.apply(new LaserBeamPath(rays)).segments().stream().mapToDouble(r -> r.power() * 1_150_000).sum(), .001);
    }

    @Test void oppositeInputsAndLowPowerCannotSubsidizeEachOther() {
        var budget = new BeamModuleBudget();
        var a = ray(); var b = ray();
        budget.remember(a, budget.pass(BlockPos.ORIGIN, Vec3d.ZERO, AXIS, 100, 90, List.of()));
        budget.remember(b, budget.pass(BlockPos.ORIGIN, AXIS, AXIS.negate(), 100, 200, List.of()));
        budget.solve();
        var result = budget.apply(new LaserBeamPath(List.of(a, b)));
        assertEquals(1, result.segments().size());
        assertEquals(.5, result.last().power(), 1e-12);
    }

    @Test void noFreeEnergyWhenUpstreamModuleExhaustsTheBeam() {
        var budget = new BeamModuleBudget();
        var route = budget.pass(BlockPos.ORIGIN, Vec3d.ZERO, AXIS, 100, 99, List.of());
        route = budget.pass(BlockPos.ORIGIN.east(), AXIS, AXIS, 1, 99, route);
        var ray = ray(); budget.remember(ray, route); budget.solve();
        assertTrue(budget.apply(new LaserBeamPath(List.of(ray))).segments().isEmpty());
    }

    @Test void creativeFluxIsBoundedMonotonicAndDefaultsToOneMegalumen() {
        assertEquals(0, CreativeFlux.lumens(-10)); assertEquals(1_000_000_000_000L, CreativeFlux.lumens(10000));
        assertEquals(1_000_000, CreativeFlux.lumens(CreativeFlux.migrate(667)));
        assertEquals(1_000_000_000L, CreativeFlux.lumens(CreativeFlux.migrate(1000)));
        assertEquals(1_000_000, CreativeFlux.lumens(CreativeFlux.DEFAULT_STEP));
        for (int i = 1; i <= CreativeFlux.STEPS; i++) assertTrue(CreativeFlux.lumens(i) >= CreativeFlux.lumens(i - 1));
    }

    private static LaserBeamTrace ray() { return new LaserBeamTrace(Vec3d.ZERO, AXIS.multiply(8), Direction.EAST, null); }
}
