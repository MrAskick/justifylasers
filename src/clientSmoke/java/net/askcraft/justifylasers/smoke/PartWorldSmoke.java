package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class PartWorldSmoke {
    private static int loadingTicks;
    private static int ticks;
    private static boolean started;

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null) return;
        if (!started && ++loadingTicks >= 30) {
            started = true;
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1440, 900);
            client.options.pauseOnLostFocus = false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.hudHidden = true;
            client.options.getViewDistance().setValue(6);
            client.options.getSimulationDistance().setValue(5);
            client.options.getFov().setValue(65);
            SmokeWorldAccess.start(client);
            return;
        }
        if (client.world == null || client.player == null) return;
        int frame = ++ticks;
        if (frame == 50) client.getServer().execute(() -> scene(client));
        if (frame == 130) {
            for (int i = 0; i < 19; i++) {
                var entity = client.world.getBlockEntity(position(i));
                if (!(entity instanceof LaserPartBlockEntity) || client.getBlockEntityRenderDispatcher().get(entity) == null)
                    throw new AssertionError("Decoration / renderer did not synchronize: " + i);
            }
            capture(client, "crystals-day");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(18000));
        }
        if (frame == 175) {
            capture(client, "crystals-night");
            client.getServer().execute(() -> player(client).teleport(client.getServer().getOverworld(), 207.5, 6, 205.5, 0, 23));
        }
        if (frame == 215) {
            capture(client, "modules-night");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(9000));
        }
        if (frame == 245) {
            capture(client, "modules-day");
            client.options.hudHidden = false;
            client.getServer().execute(() -> {
                var player = player(client);
                player.getInventory().setStack(0, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)));
                player.getInventory().setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.THICKNESS)));
                player.getInventory().selectedSlot = 0;
            });
            client.player.getInventory().selectedSlot = 0;
        }
        if (frame == 280) {
            capture(client, "module-hand");
            client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        if (frame == 310) {
            capture(client, "module-third-person");
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.reloadResources();
        }
        if (frame == 350) {
            capture(client, "modules-reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("DECORATION_WORLD_SMOKE_PASSED shader={}", IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static BlockPos position(int index) {
        if (index < 9) return new BlockPos(200 + index * 2, 2, 205);
        int module = index - 9;
        return new BlockPos(201 + (module % 5) * 3, 2, 213 + (module / 5) * 3);
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(9000);
        world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class, new net.minecraft.util.math.Box(195, 0, 190, 220, 15, 220), entity -> true)
                .forEach(net.minecraft.entity.ItemEntity::discard);
        world.getEntitiesByClass(net.minecraft.entity.decoration.ItemFrameEntity.class, new net.minecraft.util.math.Box(195, 0, 190, 220, 15, 220), entity -> true)
                .forEach(net.minecraft.entity.decoration.ItemFrameEntity::discard);
        world.getEntitiesByClass(net.askcraft.justifylasers.entity.RefocusingCubeEntity.class, new net.minecraft.util.math.Box(195, 0, 190, 220, 15, 220), entity -> true)
                .forEach(net.askcraft.justifylasers.entity.RefocusingCubeEntity::discard);
        for (int x = 198; x <= 218; x++) for (int z = 195; z <= 219; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_ANDESITE.getDefaultState());
            for (int y = 2; y <= 9; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        int index = 0;
        for (var block : ModLaserParts.DECORATIONS.values()) {
            BlockPos pos = position(index++);
            world.setBlockState(pos.down(), Blocks.IRON_BLOCK.getDefaultState());
            world.setBlockState(pos, block.getDefaultState());
        }
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE);
        player.teleport(world, 208.5, 4, 192, 0, 12);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
    }

    private static ServerPlayerEntity player(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static void capture(MinecraftClient client, String name) {
        String prefix = IrisCompatibility.isShaderPackInUse() ? "decor-kappa-" : "decor-vanilla-";
        ScreenshotRecorder.saveScreenshot(client.runDirectory, prefix + name + ".png", client.getFramebuffer(), text -> { });
    }

    private PartWorldSmoke() { }
}
