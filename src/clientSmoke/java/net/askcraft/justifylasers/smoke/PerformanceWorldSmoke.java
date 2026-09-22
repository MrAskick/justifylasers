package net.askcraft.justifylasers.smoke;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.askcraft.justifylasers.block.entity.PrintedModelBlockEntity;
import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;

/** Profiles an isolated copy of a real world; never creates or removes scene blocks. */
public final class PerformanceWorldSmoke {
    private static boolean started, positioned, finished;
    private static long readyAt, measurementAt, lastFrame;
    private static Recording recording;
    private static final ArrayList<Double> FRAMES = new ArrayList<>();

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null || finished) return;
        if (!started) {
            started = true;
            if (!client.getWindow().isFullscreen()) client.getWindow().toggleFullscreen();
            client.options.getFullscreen().setValue(true);
            client.options.getViewDistance().setValue(12);
            client.options.getSimulationDistance().setValue(12);
            client.options.pauseOnLostFocus = false;
            client.options.getEnableVsync().setValue(false);
            client.options.getMaxFps().setValue(260);
            SmokeWorldAccess.start(client);
            return;
        }
        if (client.world == null || client.player == null) return;
        if (!client.getWindow().isFullscreen()) {
            client.getWindow().toggleFullscreen();
            client.options.getFullscreen().setValue(true);
            readyAt = System.nanoTime() + 20_000_000_000L;
            return;
        }
        if (!positioned) {
            positioned = true;
            GLFW.glfwFocusWindow(client.getWindow().getHandle());
            client.getServer().execute(() -> {
                var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.CREATIVE);
                if (Boolean.getBoolean("justifylasers.smokeShaderPerformance"))
                    player.teleport(client.getServer().getOverworld(), -385, -60, -21, 45, 15);
                else if (Boolean.getBoolean("justifylasers.smokeBridgePerformance"))
                    player.teleport(client.getServer().getOverworld(), -405, -60, 18, 50, 15);
                else player.teleport(client.getServer().getOverworld(), -355, -60, -24, 65, 9);
            });
            readyAt = System.nanoTime() + 20_000_000_000L;
            return;
        }
        long now = System.nanoTime();
        if (measurementAt == 0 && now >= readyAt) {
            int width = 1920, height = 1080;
            if (client.getFramebuffer().textureWidth != width || client.getFramebuffer().textureHeight != height)
                throw new AssertionError("Performance comparison requires a " + width + "x" + height + " framebuffer, got "
                        + client.getFramebuffer().textureWidth + "x" + client.getFramebuffer().textureHeight);
            inspect(client);
            ScreenshotRecorder.saveScreenshot(client.runDirectory, "performance-scene.png", client.getFramebuffer(), text -> {});
            try {
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.setDestination(client.runDirectory.toPath().resolve("performance.jfr"));
                recording.start();
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            FRAMES.clear(); lastFrame = 0; measurementAt = System.nanoTime();
            LoggerFactory.getLogger("justifylasers-performance").info("PERFORMANCE_MEASUREMENT_STARTED");
        } else if (measurementAt != 0 && now - measurementAt >= 30_000_000_000L) {
            finished = true;
            recording.stop(); recording.close();
            var sorted = FRAMES.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double seconds = (lastFrame - measurementAt) / 1e9;
            String result = String.format(Locale.ROOT,
                    "resolution=%dx%d frames=%d seconds=%.3f fps=%.2f medianMs=%.3f p95Ms=%.3f p99Ms=%.3f focused=%s iconified=%d%n",
                    client.getFramebuffer().textureWidth, client.getFramebuffer().textureHeight,
                    sorted.length, seconds, sorted.length / seconds, percentile(sorted,.5), percentile(sorted,.95), percentile(sorted,.99),
                    client.isWindowFocused(), GLFW.glfwGetWindowAttrib(client.getWindow().getHandle(), GLFW.GLFW_ICONIFIED));
            try { Files.writeString(client.runDirectory.toPath().resolve("performance.txt"), result); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            LoggerFactory.getLogger("justifylasers-performance").info("PERFORMANCE_RESULT {}", result.trim());
            client.scheduleStop();
        }
    }

    public static void frame() {
        if (measurementAt == 0 || finished) return;
        long now = System.nanoTime();
        if (lastFrame != 0) FRAMES.add((now-lastFrame)/1e6);
        lastFrame = now;
    }

    private static double percentile(double[] values, double q) {
        return values.length == 0 ? 0 : values[Math.min(values.length-1,(int)(values.length*q))];
    }

    private static void inspect(MinecraftClient client) {
        var example = client.runDirectory.toPath().resolve("printer-preview.schem");
        if (Files.exists(example)) try {
            var schematic = net.askcraft.justifylasers.printing.SchematicImporter.read(Files.readAllBytes(example));
            var materials = schematic.resolveMaterials(net.askcraft.justifylasers.client.render.SchematicBlockModels::material);
            var design = schematic.model("", 1, materials.materials()::get);
            if (!materials.skipped().isEmpty() || design.partCount() != 1) throw new AssertionError("JEI preview is not a supported one-block design");
            Files.writeString(client.runDirectory.toPath().resolve("printer-preview.json"), design.json());
            LoggerFactory.getLogger("justifylasers-performance").info("PRINTER_PREVIEW_EXPORTED dimensions={}x{}x{} elements={} textures={}",
                    schematic.width(), schematic.height(), schematic.length(), design.elements().size(), design.textures());
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        LoggerFactory.getLogger("justifylasers-performance").info("PERFORMANCE_RENDERER shaders={} mirrors={} mirrorShaders={} gpu={}",
                net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse(),
                net.askcraft.justifylasers.client.ClientSettings.get().mirrorReflections,
                net.askcraft.justifylasers.client.ClientSettings.get().mirrorShaders,
                org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));
        var center = new ChunkPos(client.player.getBlockPos());
        var counts = new TreeMap<String,Integer>();
        int elements = 0;
        for (int z=center.z-5;z<=center.z+5;z++) for(int x=center.x-5;x<=center.x+5;x++) {
            var chunk=client.world.getChunkManager().getChunk(x,z,ChunkStatus.FULL,false);
            if(chunk==null)continue;
            for(var block:chunk.getBlockEntities().values()) {
                counts.merge(Registries.BLOCK_ENTITY_TYPE.getId(block.getType()).toString(),1,Integer::sum);
                if(block instanceof PrintedModelBlockEntity print && print.design()!=null) elements+=print.design().preview().elements().size();
            }
        }
        LoggerFactory.getLogger("justifylasers-performance").info("PERFORMANCE_SCENE pos={} yaw={} pitch={} view={} entities={} printElements={}",
                client.player.getPos(),client.player.getYaw(),client.player.getPitch(),client.options.getViewDistance().getValue(),counts,elements);
        var bridges = LightBridgeNetwork.fields(client.world).stream().filter(span -> span.active()).toList();
        if (Boolean.getBoolean("justifylasers.smokeBridgePerformance") && bridges.isEmpty())
            throw new AssertionError("Bridge benchmark requires powered bridges in the saved scene");
        LoggerFactory.getLogger("justifylasers-performance").info("PERFORMANCE_BRIDGES count={} totalLength={} widths={}",
                bridges.size(), bridges.stream().mapToDouble(span -> span.length()).sum(),
                bridges.stream().map(span -> span.width()).toList());
    }
    private PerformanceWorldSmoke() { }
}
