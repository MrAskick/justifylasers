package net.askcraft.justifylasers.client;

import net.askcraft.justifylasers.client.compat.GpuMeshShaderSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GpuMeshShaderSourceTest {
    private static final String SOURCE = """
            #version 150
            in vec3 iris_Position;
            in vec3 iris_Normal;
            in vec4 iris_Color;
            in ivec2 iris_UV1;
            in ivec2 iris_UV2;
            in ivec3 iris_Entity;
            in vec4 at_tangent;
            void main() {
                gl_Position = vec4(iris_Position + iris_Normal, 1.0);
            }
            """;

    @Test void transformsOnlyOptedInLocalMeshesWithoutChangingTheMaterialPipeline() {
        String result = GpuMeshShaderSource.vertex(SOURCE);
        for (String declaration : SOURCE.split("\n")) if (declaration.startsWith("in ")) assertTrue(result.contains(declaration));
        assertTrue(result.contains("gl_Position = vec4(jl_Local_iris_Position + jl_Local_iris_Normal, 1.0)"));
        assertTrue(result.contains("jl_ModelActive != 0 ? vec3((jl_ModelMatrix * vec4(iris_Position, 1.0)).xyz) : iris_Position"));
        assertTrue(result.contains("jl_Local_iris_Entity = jl_ModelActive != 0 ? ivec3(jl_Entity) : iris_Entity"));
        assertTrue(result.contains("sign(determinant(mat3(jl_ModelMatrix)))"), "Reflected transforms keep tangent handedness");
        assertTrue(result.indexOf("#version") < result.indexOf("uniform"));
        assertEquals(result, GpuMeshShaderSource.vertex(result), "Composes with reloads without a second wrapper");
    }

    @Test void leavesOtherProgramsAndUnknownVertexLayoutsUntouched() {
        assertNull(GpuMeshShaderSource.vertex(null));
        String unfamiliar = "#version 150\nin vec3 Position;\nvoid main() { gl_Position = vec4(Position,1.0); }";
        assertEquals(unfamiliar, GpuMeshShaderSource.vertex(unfamiliar));
        var patched = GpuMeshShaderSource.patch(Map.of("VERTEX", SOURCE, "FRAGMENT", "void main() {}"));
        assertEquals("void main() {}", patched.get("FRAGMENT"));
        assertTrue(patched.get("VERTEX").contains("jl_ModelActive"));
        assertFalse(GpuMeshShaderSource.vertex(SOURCE.replace("in vec4 at_tangent;", "")).contains("jl_Local_at_tangent"));
    }
}
