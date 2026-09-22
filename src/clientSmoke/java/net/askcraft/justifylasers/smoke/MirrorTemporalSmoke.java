package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.render.MirrorRenderer;
import net.askcraft.justifylasers.laser.MirrorGeometry;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compares the aperture with its captured image on consecutive frames, including oblique TAA views. */
public final class MirrorTemporalSmoke {
    private static final BlockPos MIRROR = new BlockPos(706, 4, 704);
    private static final Map<String, Stats> RESULTS = new LinkedHashMap<>();
    private static String phase;
    private static final class Stats {
        int frames;
        double worst;
        double total;
        float depthPixelScale;
    }

    public static void tick(MinecraftClient client, int tick) {
        if (tick <= 110) Prompt5WorldSmoke.tick(client, tick);
        if (tick == 115) ClientSettings.get().gpuModels = true;
        if (tick == 130) phase = "front-gpu";
        if (tick == 160) { phase = null; move(client, 707, 3.2, 702.8, 20, 8); }
        if (tick == 185) phase = "oblique-gpu";
        if (tick == 220) { phase = null; move(client, 708, 2.88, 703.7, 62, 0); }
        if (tick == 245) phase = "grazing-gpu";
        if (tick == 280) { phase = null; ClientSettings.get().gpuModels = false; }
        if (tick == 305) phase = "grazing-cpu";
        if (tick == 340) { phase = null; move(client, 707, 3.2, 702.8, 20, 8); }
        if (tick == 365) phase = "oblique-cpu";
        if (tick == 400) {
            phase = null;
            client.getServer().execute(() -> client.getServer().getOverworld().setBlockState(MIRROR.north(), Blocks.STONE.getDefaultState()));
        }
        if (tick == 430) MirrorDepthSmoke.assertComposite(client, true);
        if (tick == 435) {
            client.getServer().execute(() -> client.getServer().getOverworld().setBlockState(MIRROR.north(), Blocks.AIR.getDefaultState()));
            move(client, 706.5, 2.88, 702.5, 0, 0);
            ClientSettings.get().gpuModels = true;
        }
        if (tick == 455) {
            MirrorDepthSmoke.inspect(client, MIRROR, "temporal-before-reload");
            client.reloadResources();
        }
        if (tick == 510) phase = "reloaded-gpu";
        if (tick == 550) {
            phase = null;
            boolean passed = RESULTS.size() == 6;
            for (var entry : RESULTS.entrySet()) {
                var s = entry.getValue();
                LoggerFactory.getLogger("justifylasers-client-smoke").info(
                        "MIRROR_TEMPORAL phase={} frames={} worstMismatch={} meanMismatch={} depthPixelScale={}",
                        entry.getKey(), s.frames, s.worst, s.total / Math.max(1, s.frames), s.depthPixelScale);
                passed &= s.frames >= 32 && s.worst < .05;
            }
            if (!passed) throw new AssertionError("Mirror aperture did not consistently display this frame's reflection: " + RESULTS.keySet());
            LoggerFactory.getLogger("justifylasers-client-smoke").info("MIRROR_TEMPORAL_SMOKE_PASSED");
            client.scheduleStop();
        }
    }

    private static void move(MinecraftClient client, double x, double y, double z, float yaw, float pitch) {
        client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayer(client.player.getUuid())
                .teleport(client.getServer().getOverworld(), x, y, z, yaw, pitch));
    }

    public static void sample(Camera camera, MatrixStack matrices, Matrix4f projection) {
        if (phase == null || MirrorRenderer.rendering()) return;
        var stats = RESULTS.computeIfAbsent(phase, key -> new Stats());
        if (stats.frames >= 48) return;
        var client = MinecraftClient.getInstance();
        try {
            var field = MirrorRenderer.class.getDeclaredField("CAPTURES");
            field.setAccessible(true);
            Object capture = ((Map<?, ?>) field.get(null)).get(MIRROR);
            if (capture == null) throw new AssertionError("Mirror capture disappeared during " + phase);
            var method = capture.getClass().getDeclaredMethod("target");
            method.setAccessible(true);
            var target = (Framebuffer) method.invoke(capture);
            var optic = (LaserOpticBlockEntity) client.world.getBlockEntity(MIRROR);
            var basis = MirrorGeometry.frame(optic.normal(), optic.facing());
            var transform = new Matrix4f(projection).mul(matrices.peek().getPositionMatrix());
            int samples = 0, wrong = 0;
            try (var scene = ScreenshotRecorder.takeScreenshot(target);
                 var main = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                for (int iy = -12; iy <= 12; iy++) for (int ix = -12; ix <= 12; ix++) {
                    Vec3d point = Vec3d.ofCenter(MIRROR).add(basis.right().multiply(ix * .017))
                            .add(basis.up().multiply(iy * .017)).subtract(camera.getPos());
                    var clip = transform.transform(new Vector4f((float) point.x, (float) point.y, (float) point.z, 1));
                    int x = (int) ((clip.x / clip.w * .5 + .5) * main.getWidth());
                    int y = (int) ((.5 - clip.y / clip.w * .5) * main.getHeight());
                    if (x < 0 || y < 0 || x >= main.getWidth() || y >= main.getHeight()) continue;
                    int a = scene.getColor(x, y), b = main.getColor(x, y);
                    int difference = Math.abs((a & 255) - (b & 255))
                            + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                            + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255));
                    samples++;
                    if (difference > 12) wrong++;
                }
            }
            if (samples < 100) throw new AssertionError("Mirror temporal fixture is out of view");
            double mismatch = (double) wrong / samples;
            if (stats.frames == 0 || mismatch > stats.worst) {
                ScreenshotRecorder.saveScreenshot(client.runDirectory, "mirror-temporal-" + phase + ".png", client.getFramebuffer(), text -> { });
                ScreenshotRecorder.saveScreenshot(client.runDirectory, "mirror-temporal-capture-" + phase + ".png", target, text -> { });
            }
            stats.frames++;
            stats.depthPixelScale = net.askcraft.justifylasers.client.compat.IrisCompatibility.finalDepthPixelScale();
            stats.worst = Math.max(stats.worst, mismatch);
            stats.total += mismatch;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect temporal mirror capture", exception);
        }
    }

    private MirrorTemporalSmoke() { }
}
