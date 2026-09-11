package net.askcraft.justifylasers.client.screen;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static net.askcraft.justifylasers.client.screen.TechGui.State.*;
import static org.junit.jupiter.api.Assertions.*;

class TechGuiTest {
    @Test
    void disabledAndPressedStatesTakePriorityOverNavigationHighlights() {
        assertEquals(DISABLED, TechGui.State.of(false, true, true, true, true));
        assertEquals(PRESSED, TechGui.State.of(true, true, true, true, true));
        assertEquals(HOVERED, TechGui.State.of(true, false, true, false, true));
        assertEquals(HOVERED, TechGui.State.of(true, false, false, true, false));
        assertEquals(SELECTED, TechGui.State.of(true, false, false, false, true));
        assertEquals(NORMAL, TechGui.State.of(true, false, false, false, false));
        var accents = new HashSet<Integer>();
        for (var state : TechGui.State.values()) assertTrue(accents.add(state.accent()));
    }

    @Test
    void everyIconHasDistinctBoundedGeometryFacingTheGuiCamera() {
        var meshes = new HashSet<>();
        assertEquals(9, TechGui.Icon.values().length);
        for (var icon : TechGui.Icon.values()) {
            assertTrue(meshes.add(icon.mesh()), icon.name());
            assertFalse(icon.mesh().isEmpty(), icon.name());
            for (var quad : icon.mesh()) {
                for (var point : new TechGui.Point[]{quad.a(), quad.b(), quad.c(), quad.d()}) {
                    assertTrue(Float.isFinite(point.x()) && Float.isFinite(point.y()), icon.name());
                    assertTrue(point.x() >= 0 && point.x() <= 24 && point.y() >= 0 && point.y() <= 24,
                            icon + ": " + point);
                }
                double first = cross(quad.a(), quad.b(), quad.c());
                double second = cross(quad.a(), quad.c(), quad.d());
                assertTrue(first <= 0 && second <= 0 && first + second < 0,
                        icon + " would be culled by the GUI render layer: " + quad);
            }
        }
    }

    @Test
    void beveledFramesRemainConvexAndInsideAllExistingButtonBounds() {
        for (int[] size : new int[][]{{18, 18}, {41, 28}, {40, 44}, {78, 14}, {56, 16}, {126, 16}, {189, 18}, {189, 20}}) {
            for (float inset : new float[]{0.8F, 1, 1.9F, 3.1F}) {
                var points = TechGui.contour(20 + inset, 30 + inset, size[0] - inset * 2, size[1] - inset * 2, 4.5F);
                assertEquals(8, points.length);
                for (int i = 0; i < points.length; i++) {
                    var point = points[i];
                    assertTrue(point.x() >= 20 && point.x() <= 20 + size[0]);
                    assertTrue(point.y() >= 30 && point.y() <= 30 + size[1]);
                    assertTrue(cross(point, points[(i + 1) % points.length], points[(i + 2) % points.length]) >= -1e-4);
                }
            }
        }
    }

    private static double cross(TechGui.Point a, TechGui.Point b, TechGui.Point c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }
}
