package net.askcraft.justifylasers.client.compat;

import org.joml.Vector4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Keep a conventional perspective matrix for packs that reconstruct depth from its diagonal. */
public final class MirrorClipPlane {
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Vector4f PLANE = new Vector4f();
    private static final String UNIFORM = "jl_MirrorClipPlane";

    public static void set(Vector4f plane) { PLANE.set(plane); }
    public static void clear() { PLANE.zero(); }

    public static String vertex(String source) {
        if (source == null || source.contains(UNIFORM)) return source;
        var main = MAIN.matcher(source);
        if (!main.find()) return source;
        return main.replaceFirst("void jl_mirrorOriginalMain()") + "\nuniform vec4 " + UNIFORM
                + ";\nvoid main() { jl_mirrorOriginalMain(); gl_ClipDistance[0] = dot(" + UNIFORM + ", gl_Position); }\n";
    }

    public static Map<Object, String> patch(Map<?, String> original) {
        var result = new LinkedHashMap<Object, String>();
        original.forEach((type, source) -> result.put(type, type.toString().equals("VERTEX") ? vertex(source) : source));
        return result;
    }

    public static void uniforms(Object holder) {
        try {
            Class<?> frequency = Class.forName("net.irisshaders.iris.gl.uniform.UniformUpdateFrequency");
            Class.forName("net.irisshaders.iris.gl.uniform.UniformHolder")
                    .getMethod("uniform4f", frequency, String.class, Supplier.class)
                    .invoke(holder, frequency.getField("PER_FRAME").get(null), UNIFORM, (Supplier<Vector4f>) () -> new Vector4f(PLANE));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot register the mirror clipping plane", exception);
        }
    }
    private MirrorClipPlane() { }
}
