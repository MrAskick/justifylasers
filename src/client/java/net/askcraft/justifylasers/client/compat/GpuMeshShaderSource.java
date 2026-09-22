package net.askcraft.justifylasers.client.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Transforms local mesh attributes before Iris' own material, lighting and shadow code runs. */
public final class GpuMeshShaderSource {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Map<String, String> ATTRIBUTES = new LinkedHashMap<>();
    static {
        ATTRIBUTES.put("iris_Position", "(jl_ModelMatrix * vec4(iris_Position, 1.0)).xyz");
        ATTRIBUTES.put("iris_Normal", "jl_NormalMatrix * iris_Normal");
        ATTRIBUTES.put("iris_Color", "iris_Color * jl_Tint");
        ATTRIBUTES.put("iris_UV1", "jl_OverlayUV");
        ATTRIBUTES.put("iris_UV2", "jl_LightUV");
        ATTRIBUTES.put("iris_Entity", "jl_Entity");
        ATTRIBUTES.put("at_tangent", "vec4(normalize(mat3(jl_ModelMatrix) * at_tangent.xyz), at_tangent.w * sign(determinant(mat3(jl_ModelMatrix))))");
    }

    public static String vertex(String source) {
        if (source == null || source.contains("jl_ModelActive") || !MAIN.matcher(source).find()) return source;
        String transformed = source;
        StringBuilder initialize = new StringBuilder();
        boolean position = false;
        for (var attribute : ATTRIBUTES.entrySet()) {
            String name = attribute.getKey(), alias = "jl_Local_" + name;
            var declaration = Pattern.compile("\\b(?:in|attribute)\\s+([iu]?vec[234])\\s+" + name + "\\s*;");
            var found = declaration.matcher(transformed);
            if (!found.find()) continue;
            String type = found.group(1);
            String original = found.group();
            transformed = transformed.replaceAll("\\b" + name + "\\b", alias);
            transformed = transformed.replace(original.replace(name, alias), original + "\n" + type + " " + alias + ";");
            initialize.append(alias).append(" = jl_ModelActive != 0 ? ").append(type).append('(')
                    .append(attribute.getValue()).append(") : ").append(name).append(";\n");
            position |= name.equals("iris_Position");
        }
        if (!position) return source;
        transformed = MAIN.matcher(transformed).replaceFirst("void jl_meshOriginalMain()");
        return transformed + """

                uniform int jl_ModelActive;
                uniform mat4 jl_ModelMatrix;
                uniform mat3 jl_NormalMatrix;
                uniform vec4 jl_Tint;
                uniform ivec2 jl_LightUV;
                uniform ivec2 jl_OverlayUV;
                uniform ivec3 jl_Entity;
                void main() {
                """ + initialize + "jl_meshOriginalMain();\n}\n";
    }

    public static Map<Object, String> patch(Map<?, String> original) {
        var result = new LinkedHashMap<Object, String>();
        original.forEach((type, source) -> result.put(type, type.toString().equals("VERTEX") ? vertex(source) : source));
        return result;
    }

    private GpuMeshShaderSource() { }
}
