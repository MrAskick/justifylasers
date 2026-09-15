package net.askcraft.justifylasers.client.render;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LaserScopeRendererTest {
    @Test
    void cameraFocusPreservesTheConfiguredFovAndUsesPerspectiveMagnification() {
        for (double fov : new double[]{30, 70, 90, 110}) {
            double zoomed = LaserScopeRenderer.magnifiedFov(fov, 1);
            assertEquals(1.5, Math.tan(Math.toRadians(fov) / 2) / Math.tan(Math.toRadians(zoomed) / 2), 1e-9);
            assertEquals(fov, LaserScopeRenderer.magnifiedFov(fov, 0), 1e-9);
        }
    }

    @Test
    void offAxisLensIsClippedToBothEndsOfTheSight() {
        var result = LaserScopeRenderer.clip(rectangle(-1, -1, 1, 1), rectangle(0.25F, -2, 2, 2));
        assertEquals(4, result.size());
        for (Vector2f p : result) assertTrue(p.x >= 0.25F && p.x <= 1 && p.y >= -1 && p.y <= 1);
    }

    @Test
    void closedViewCannotPaintOverItsFrame() {
        assertTrue(LaserScopeRenderer.clip(rectangle(-1, -1, 1, 1), rectangle(2, 2, 3, 3)).isEmpty());
        assertTrue(LaserScopeRenderer.clip(rectangle(-1, -1, 1, 1), List.of()).isEmpty());
    }

    @Test
    void leftHandMirroringKeepsTheSameAperture() {
        var window = new ArrayList<>(rectangle(-0.5F, -0.5F, 0.5F, 0.5F));
        Collections.reverse(window);
        var result = LaserScopeRenderer.clip(rectangle(-1, -1, 1, 1), window);
        assertEquals(4, result.size());
        for (Vector2f p : result) assertEquals(0.5F, Math.abs(p.x), 0.0001F);
    }

    private static List<Vector2f> rectangle(float x1, float y1, float x2, float y2) {
        return List.of(new Vector2f(x1, y1), new Vector2f(x2, y1), new Vector2f(x2, y2), new Vector2f(x1, y2));
    }
}
