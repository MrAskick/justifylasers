package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.OpticPortMode;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

final class ReceiverWorldSmoke {
    private static final BlockPos RECEIVER = new BlockPos(350, 3, 350);
    private static final BlockPos SOURCE = RECEIVER.south(4);
    private static int loading, ticks;
    private static boolean started;

    static void tick(MinecraftClient client) {
        if (client.getOverlay() != null) return;
        if (!started && ++loading >= 30) {
            started = true;
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1440, 1000);
            client.options.pauseOnLostFocus = false;
            client.options.hudHidden = true;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.getViewDistance().setValue(5);
            client.options.getFov().setValue(55);
            SmokeWorldAccess.start(client);
            return;
        }
        if (client.world == null || client.player == null) {
            if (started && client.currentScreen != null) client.currentScreen.children().stream()
                    .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button -> button.getMessage().getString().equals(Text.translatable("selectWorld.backupWarning.proceed").getString())
                            || button.getMessage().getString().equals(Text.translatable("selectWorld.backupJoinSkipButton").getString()))
                    .findFirst().ifPresent(ButtonWidget::onPress);
            if (++loading > 1200) throw new AssertionError("Receiver scene did not load");
            return;
        }
        int frame = ++ticks;
        if (frame == 50) client.getServer().execute(() -> scene(client));
        if (frame >= 90) {
            client.player.setYaw(34);
            client.player.prevYaw = 34;
            client.player.setPitch(19);
            client.player.prevPitch = 19;
        }
        if (frame >= 130 && frame <= 370 && (frame - 130) % 30 == 0) {
            int index = (frame - 130) / 30;
            var receiver = (LaserOpticBlockEntity) client.world.getBlockEntity(RECEIVER);
            if (receiver.rgb() != LaserColor.byIndex(index).rgb() || !receiver.emitsShaderLight())
                throw new AssertionError("Receiver failed to synchronize color " + index);
            capture(client, LaserColor.byIndex(index).asString());
            client.getServer().execute(() -> {
                var emitter = (LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(SOURCE);
                if (index < 8) emitter.getPropertyDelegate().set(2, index + 1);
                else emitter.getPropertyDelegate().set(0, 0);
            });
        }
        if (frame == 410) {
            var receiver = (LaserOpticBlockEntity) client.world.getBlockEntity(RECEIVER);
            if (receiver.getCachedState().get(LaserOpticBlock.LIT) || receiver.emitsShaderLight())
                throw new AssertionError("Idle receiver still emits light");
            capture(client, "off");
            client.getServer().execute(() -> {
                var optic = (LaserOpticBlockEntity) client.getServer().getOverworld().getBlockEntity(RECEIVER);
                optic.setPortMode(Direction.NORTH, OpticPortMode.DISABLED);
                optic.setPortMode(Direction.EAST, OpticPortMode.INPUT);
                var emitter = (LaserEmitterBlockEntity) client.getServer().getOverworld().getBlockEntity(SOURCE);
                emitter.getPropertyDelegate().set(2, LaserColor.CYAN.ordinal());
                emitter.getPropertyDelegate().set(0, 1);
            });
        }
        if (frame == 450) {
            capture(client, "configured");
            client.getServer().execute(() -> client.getServer().getOverworld().setTimeOfDay(18000));
            client.reloadResources();
        }
        if (frame == 500) {
            capture(client, "reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("RECEIVER_MODEL_SMOKE_PASSED colors=9 shader={}", IrisCompatibility.isShaderPackInUse());
            client.scheduleStop();
        }
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(9000);
        for (int x = 343; x <= 358; x++) for (int z = 342; z <= 357; z++) {
            world.setBlockState(new BlockPos(x, 2, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 3; y < 10; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        world.setBlockState(RECEIVER, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(LaserOpticBlock.FACING, Direction.NORTH));
        ((LaserOpticBlockEntity) world.getBlockEntity(RECEIVER)).setPortMode(Direction.SOUTH, OpticPortMode.INPUT);
        world.setBlockState(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.NORTH));
        var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(SOURCE);
        emitter.getPropertyDelegate().set(3, 0);
        emitter.getPropertyDelegate().set(4, 0);
        emitter.getPropertyDelegate().set(8, 0);
        emitter.getPropertyDelegate().set(6, 0);
        emitter.getPropertyDelegate().set(13, 8);
        ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.teleport(world, 353.6, 3.9, 345.9, 34, 19);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
    }

    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "receiver-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-")
                + name + ".png", client.getFramebuffer(), text -> { });
    }
}
