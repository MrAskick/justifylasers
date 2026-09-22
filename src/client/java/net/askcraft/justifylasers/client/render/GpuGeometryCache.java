package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.minecraft.client.gl.VertexBuffer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** One residency budget for geometry, independent of Minecraft's textures and shader render targets. */
public final class GpuGeometryCache {
    private record Entry(VertexBuffer buffer, long bytes) { }
    private static final Map<Object, Entry> ENTRIES = new LinkedHashMap<>(64, .75F, true);
    private static long bytes, uploaded;
    private static boolean extended;
    private static net.minecraft.client.world.ClientWorld world;

    public static void beginFrame() {
        boolean shaders = IrisCompatibility.isShaderPackInUse();
        var currentWorld = net.minecraft.client.MinecraftClient.getInstance().world;
        if (extended != shaders || world != currentWorld) { clear(); extended = shaders; world = currentWorld; }
        uploaded = 0;
        trim(0);
    }

    static VertexBuffer get(Object key, long estimate, Supplier<VertexBuffer> create) {
        var cached = ENTRIES.get(key);
        if (cached != null) return cached.buffer;
        if (estimate > limit() || uploaded > 0 && uploaded + estimate > ClientSettings.get().gpuUploadMiB * 1048576L) return null;
        trim(estimate);
        VertexBuffer buffer = create.get();
        ENTRIES.put(key, new Entry(buffer, estimate));
        bytes += estimate;
        uploaded += estimate;
        return buffer;
    }

    private static long limit() { return ClientSettings.get().gpuCacheMiB * 1048576L; }

    private static void trim(long incoming) {
        var oldest = ENTRIES.values().iterator();
        while ((bytes + incoming > limit() || ENTRIES.size() >= 2048) && oldest.hasNext()) {
            var entry = oldest.next();
            entry.buffer.close();
            bytes -= entry.bytes;
            oldest.remove();
        }
    }

    public static long residentBytes() { return bytes; }
    public static int meshCount() { return ENTRIES.size(); }

    public static void clear() {
        RenderSystem.assertOnRenderThread();
        ENTRIES.values().forEach(entry -> entry.buffer.close());
        ENTRIES.clear();
        bytes = uploaded = 0;
    }

    private GpuGeometryCache() { }
}
