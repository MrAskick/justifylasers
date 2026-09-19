package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.compat.MirrorClipPlane;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MirrorClipPlaneTest {
    @Test void clippingWrapsOnlyVertexMainAndNeverMutatesCachedShaderSources() {
        String vertex = "#version 330\nvoid main(void) { gl_Position = vec4(1.0); }";
        String fragment = "#version 330\nvoid main() { }";
        var original = Map.of("VERTEX", vertex, "FRAGMENT", fragment);
        var patched = MirrorClipPlane.patch(original);
        assertEquals(vertex, original.get("VERTEX"));
        assertEquals(fragment, patched.get("FRAGMENT"));
        String result = patched.get("VERTEX");
        assertTrue(result.startsWith("#version 330"));
        assertTrue(result.contains("gl_ClipDistance[0]"));
        assertEquals(result, MirrorClipPlane.vertex(result));
        assertEquals("no main here", MirrorClipPlane.vertex("no main here"));
    }
}
