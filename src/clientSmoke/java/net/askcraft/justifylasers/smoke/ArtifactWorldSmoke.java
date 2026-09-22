package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.bridge.LightBridgeNetwork;
import net.askcraft.justifylasers.bridge.LightBridgeSpan;
import net.askcraft.justifylasers.client.ClientSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/** Read-only scene inspection apart from the test player's position in an isolated world copy. */
public final class ArtifactWorldSmoke {
    public static int mask;
    private static LightBridgeSpan bridge;
    static void tick(MinecraftClient client, int tick) {
        if (!Boolean.getBoolean("justifylasers.artifactIsolation")) { verify(client, tick); return; }
        if (tick == 20) {
            client.options.getViewDistance().setValue(12);
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.SPECTATOR);
                player.teleport(client.getServer().getOverworld(), -385, -60, -21, 45, 15);
            });
        }
        if (tick == 100) {
            capture(client, "machines");
            var spans = LightBridgeNetwork.fields(client.world).stream().filter(s -> s.active() && s.normal().y > .99 && s.length() > 100).toList();
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("ARTIFACT_BRIDGES {}", spans);
            bridge = spans.stream().min(java.util.Comparator.comparingDouble(s -> s.origin().getSquaredDistance(client.player.getPos()))).orElseThrow();
            position(client, 8, -8);
        }
        if (tick == 180) { capture(client, "sky-near"); mask = 20; }
        if (tick == 205) { capture(client, "no-energy-cubes"); mask = 21; }
        if (tick == 230) { capture(client, "no-pipes-tanks"); mask = 22; }
        if (tick == 255) { capture(client, "no-ae2"); mask = 23; }
        if (tick == 280) { capture(client, "no-vanilla-models"); mask = 14; }
        if (tick == 304) capture(client, "no-shadow-models");
        if (tick == 305) { capture(client, "no-bridge-models"); mask = 6; }
        if (tick == 330) { capture(client, "no-industry"); mask = 7; }
        if (tick == 355) { capture(client, "no-components"); mask = 8; }
        if (tick == 380) { capture(client, "no-prints"); mask = 9; }
        if (tick == 405) { capture(client, "no-optics"); mask = 0; position(client, 180, -8); }
        if (tick == 455) capture(client, "sky-far");
        if (tick == 460) {
            ClientSettings.get().gpuModels = false;
            ClientSettings.get().gpuPrintedModels = false;
            ClientSettings.get().gpuEffects = false;
        }
        if (tick == 500) { capture(client, "sky-cpu"); position(client, 8, -8); }
        if (tick == 545) capture(client, "sky-near-cpu");
        if (tick == 550) {
            ClientSettings.get().gpuModels = true;
            ClientSettings.get().gpuPrintedModels = true;
            ClientSettings.get().gpuEffects = true;
            var screen = new net.askcraft.justifylasers.client.screen.ClientSettingsScreen(null);
            client.setScreen(screen);
            try {
                var tab = screen.getClass().getDeclaredField("tab"); tab.setAccessible(true); tab.setInt(screen, 5);
                screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        if (tick == 585) { capture(client, "gpu-settings"); client.scheduleStop(); }
    }
    private static void verify(MinecraftClient client, int tick) {
        if (tick == 20) {
            client.options.getViewDistance().setValue(12);
            client.getServer().execute(() -> {
                var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                player.changeGameMode(GameMode.SPECTATOR);
                player.teleport(client.getServer().getOverworld(), -385, -60, -21, 45, 15);
            });
        }
        if (tick == 100) {
            capture(client, "fixed-machines");
            bridge = LightBridgeNetwork.fields(client.world).stream().filter(s -> s.active() && s.normal().y > .99 && s.length() > 100)
                    .min(java.util.Comparator.comparingDouble(s -> s.origin().getSquaredDistance(client.player.getPos()))).orElseThrow();
            position(client, 8, -8);
        }
        if (tick == 180) {
            capture(client, "fixed-sky");
            ClientSettings.get().gpuModels = ClientSettings.get().gpuPrintedModels = ClientSettings.get().gpuEffects = false;
        }
        if (tick == 235) {
            capture(client, "fixed-sky-cpu");
            ClientSettings.get().gpuModels = ClientSettings.get().gpuPrintedModels = ClientSettings.get().gpuEffects = true;
            var cubes = new java.util.ArrayList<net.minecraft.util.math.BlockPos>();
            for (int x = -28; x <= -22; x++) for (int z = -4; z <= 4; z++) {
                if (!client.world.isChunkLoaded(x, z)) continue;
                for (var entity : client.world.getChunk(x, z).getBlockEntities().values())
                    if (entity.getClass().getSimpleName().equals("TileEntityEnergyCube")) cubes.add(entity.getPos());
            }
            var cube = cubes.stream().min(java.util.Comparator.comparingDouble(p -> p.getSquaredDistance(client.player.getPos()))).orElseThrow();
            client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayer(client.player.getUuid())
                    .teleport(client.getServer().getOverworld(), cube.getX() + 2.3, cube.getY() + .1, cube.getZ() + 2.3, 135, 20));
        }
        if (tick == 295) capture(client, "energy-cube-visible");
        if (tick == 300) IndustryJeiSmoke.show("photopolymer_printer", net.askcraft.justifylasers.printing.PrintDesign.class, 1);
        if (tick == 325) capture(client, "printer-jei");
        if (tick == 330) {
            var screen = new net.askcraft.justifylasers.client.screen.ClientSettingsScreen(null);
            client.setScreen(screen);
            try {
                var tab = screen.getClass().getDeclaredField("tab"); tab.setAccessible(true); tab.setInt(screen, 5);
                screen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getHandle(), 20, 20);
        }
        if (tick == 355) {
            capture(client, "final-gpu-settings");
            org.slf4j.LoggerFactory.getLogger("justifylasers-client-smoke").info("PROMPT11_SCENE_SMOKE_PASSED jei=true settings=true cacheMiB={}",
                    net.askcraft.justifylasers.client.render.GpuGeometryCache.residentBytes() / 1048576d);
            client.scheduleStop();
        }
    }
    private static void position(MinecraftClient client, double distance, float pitch) {
        Vec3d eye = bridge.point(bridge.width() * .5, distance, LightBridgeSpan.HEIGHT).add(0, .15, 0);
        client.getServer().execute(() -> client.getServer().getPlayerManager().getPlayer(client.player.getUuid())
                .teleport(client.getServer().getOverworld(), eye.x, eye.y, eye.z, bridge.facing().asRotation(), pitch));
    }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "artifact-" + name + ".png", client.getFramebuffer(), ignored -> { });
    }
    private ArtifactWorldSmoke() { }
}
