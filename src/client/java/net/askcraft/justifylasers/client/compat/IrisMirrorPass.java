package net.askcraft.justifylasers.client.compat;

import net.askcraft.justifylasers.JustifyLasers;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolates secondary cameras from a shader pack's temporal history and main-view G-buffers. */
public final class IrisMirrorPass implements AutoCloseable {
    private static Object vanilla;
    private static boolean unavailable;
    private static boolean active;
    private static boolean shaderActive;
    private static Object mainPipeline;
    private static final Map<BlockPos, ShaderView> SHADER_VIEWS = new LinkedHashMap<>();
    private static ShaderView view;
    private static Object failedPipeline;
    private record ShaderView(Object pipeline, Object terrain) { }
    private final Object manager, dimension, previous;
    private final Map<Object,Object> pipelines;
    private final Object original;
    private final Field current;
    private final Map<Field,Object> counter, timer;
    private final Object counterObject, timerObject;
    private final Object capturedObject;
    private final Map<Field, Object> captured;

    @SuppressWarnings("unchecked")
    private IrisMirrorPass(BlockPos position, boolean shaders) throws ReflectiveOperationException {
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
        manager=iris.getMethod("getPipelineManager").invoke(null);
        dimension=iris.getMethod("getCurrentDimension").invoke(null);
        current=field(manager.getClass(),"pipeline"); previous=current.get(manager);
        pipelines=(Map<Object,Object>)field(manager.getClass(),"pipelinesPerDimension").get(manager);
        original=pipelines.get(dimension);
        if (previous != mainPipeline || !shaders) {
            clearShaders();
            mainPipeline = previous;
        }
        Object selected = null;
        if (shaders && failedPipeline != previous) {
            view = SHADER_VIEWS.get(position);
            if (view == null) {
                Object settings=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings").getField("INSTANCE").get(null);
                var saved=snapshot(settings);
                Object candidate = null;
                try {
                    var pack = (java.util.Optional<?>) iris.getMethod("getCurrentPack").invoke(null);
                    Object programSet = pack.orElseThrow().getClass().getMethod("getProgramSet", dimension.getClass()).invoke(pack.get(), dimension);
                    Object pipeline = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline")
                            .getConstructor(programSet.getClass()).newInstance(programSet);
                    candidate = pipeline;
                    // Same pack and dimension: reuse the main view's material IDs. Initializing them
                    // again reloads every chunk and recursively invalidates the mirror being captured.
                    try { field(pipeline.getClass(), "initializedBlockIds").setBoolean(pipeline, true); }
                    catch (NoSuchFieldException ignored) { /* Oculus 1.8 initializes IDs in its constructor. */ }
                    Object terrain = null;
                    try { terrain = Class.forName("net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides").getConstructor().newInstance(); }
                    catch (ClassNotFoundException ignored) { /* Sodium 0.6 stores programs in the pipeline itself. */ }
                    view = new ShaderView(pipeline, terrain);
                    SHADER_VIEWS.put(position.toImmutable(), view);
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    if (candidate != null) destroy(new ShaderView(candidate, null));
                    failedPipeline = previous;
                    JustifyLasers.LOGGER.warn("Shader reflections unavailable for this Iris/Oculus pipeline; using vanilla mirror lighting", exception);
                } finally { restore(settings,saved); }
            }
            if (view != null) selected = view.pipeline;
        }
        if(vanilla==null) {
            Object settings=Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings").getField("INSTANCE").get(null);
            var saved=snapshot(settings);
            try { vanilla=Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline").getConstructor().newInstance(); }
            finally { restore(settings,saved); }
        }
        Class<?> time=Class.forName("net.irisshaders.iris.uniforms.SystemTimeUniforms");
        counterObject=time.getField("COUNTER").get(null); timerObject=time.getField("TIMER").get(null);
        counter=snapshot(counterObject); timer=snapshot(timerObject);
        capturedObject=Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState").getField("INSTANCE").get(null);
        captured=snapshot(capturedObject);
        shaderActive = selected != null;
        pipelines.put(dimension,shaderActive ? selected : vanilla); current.set(manager,shaderActive ? selected : vanilla);
        active=true;
    }

    public static boolean active() { return active; }
    public static boolean shaders() { return active && shaderActive; }

    public static boolean ownsTerrain(Object terrain) { return shaders() && view != null && view.terrain != null && terrain != view.terrain; }

    public static Object terrain(String method, Object... arguments) {
        try {
            for (var candidate : view.terrain.getClass().getMethods())
                if (candidate.getName().equals(method) && candidate.getParameterCount() == arguments.length)
                    return candidate.invoke(view.terrain, arguments);
            throw new NoSuchMethodException(method);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Unable to render mirror terrain", exception); }
    }

    public static void retain(java.util.Set<BlockPos> positions) {
        SHADER_VIEWS.entrySet().removeIf(entry -> {
            if (positions.contains(entry.getKey())) return false;
            destroy(entry.getValue());
            return true;
        });
    }

    public static void clearShaders() {
        SHADER_VIEWS.values().forEach(IrisMirrorPass::destroy);
        SHADER_VIEWS.clear(); view = null;
    }

    private static void destroy(ShaderView target) {
        try {
            if (target.terrain != null) target.terrain.getClass().getMethod("deleteShaders").invoke(target.terrain);
            target.pipeline.getClass().getMethod("destroy").invoke(target.pipeline);
        } catch (ReflectiveOperationException exception) { JustifyLasers.LOGGER.warn("Cannot release mirror shader resources", exception); }
    }

    public static IrisMirrorPass begin(BlockPos position, boolean shaders) {
        if(unavailable) return null;
        try { return new IrisMirrorPass(position, shaders); }
        catch(ReflectiveOperationException exception) {
            unavailable=true;
            JustifyLasers.LOGGER.warn("Mirror capture disabled: this Iris/Oculus version cannot isolate secondary cameras",exception);
            return null;
        }
    }

    @Override public void close() {
        active=false;
        shaderActive=false;
        try {
            if(original==null) pipelines.remove(dimension); else pipelines.put(dimension,original);
            current.set(manager,previous);
            restore(counterObject,counter); restore(timerObject,timer);
            restore(capturedObject,captured);
        } catch(ReflectiveOperationException exception) { throw new IllegalStateException("Unable to restore the main Iris camera",exception); }
    }
    private static Field field(Class<?> type,String name) throws NoSuchFieldException {
        Field field=type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Map<Field,Object> snapshot(Object object) throws IllegalAccessException {
        var values=new LinkedHashMap<Field,Object>();
        for(Field field:object.getClass().getDeclaredFields()) if(!Modifier.isStatic(field.getModifiers())&&!Modifier.isFinal(field.getModifiers())) {
            field.setAccessible(true); values.put(field,field.get(object));
        }
        return values;
    }
    private static void restore(Object object,Map<Field,Object> values) throws IllegalAccessException {
        for(var entry:values.entrySet()) entry.getKey().set(object,entry.getValue());
    }
}
