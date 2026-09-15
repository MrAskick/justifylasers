package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.client.ClientSettings;
import net.askcraft.justifylasers.client.ClientSettingsKey;
import net.askcraft.justifylasers.client.screen.ClientSettingsScreen;
import net.askcraft.justifylasers.item.LaserSaberItem;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/** Real client input, server damage, saved preferences, resource reload and final-frame captures. */
final class SaberWorldSmoke {
    private static HuskEntity target;
    private static volatile float health;

    static void tick(MinecraftClient client, int tick) {
        if (tick == 30) {
            ClientSettings.reset();
            String language = System.getProperty("justifylasers.smokeLanguage", "en_us");
            if (!client.getLanguageManager().getLanguage().equals(language)) {
                client.getLanguageManager().setLanguage(language);
                client.options.language = language;
                client.reloadResources();
            }
            client.options.hudHidden = false;
            client.getServer().execute(() -> scene(client));
        }
        if (tick == 50) client.player.getInventory().selectedSlot = 0;
        if (tick == 80) capture(client, "hilt-off");
        if (tick == 90) client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        if (tick == 100) client.interactionManager.stopUsingItem(client.player);
        if (tick == 115) {
            if (!LaserSaberItem.active(client.player.getMainHandStack())) throw new AssertionError("Use did not ignite the saber through networking");
            capture(client, "single-red");
            client.getServer().execute(() -> GameVersion.setCubeColor(player(client).getMainHandStack(), LaserColor.CYAN.ordinal()));
        }
        if (tick == 150) {
            if (GameVersion.cubeColor(client.player.getMainHandStack()) != LaserColor.CYAN.ordinal()) throw new AssertionError("Saber color did not sync");
            capture(client, "single-cyan");
        }
        if (tick == 160) verifyCore(client, "single-cyan", true);
        if (tick >= 165 && tick < 202) client.options.attackKey.setPressed(true);
        if (tick == 180 || tick == 195) {
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_INPUT held={} active={} key={} age={} swing={} using={} cooldown={} paused={} screen={}",
                    net.askcraft.justifylasers.client.SaberControls.held(client), LaserSaberItem.active(client.player.getMainHandStack()),
                    client.options.attackKey.isPressed(), client.player.age, client.player.getHandSwingProgress(1), client.player.isUsingItem(),
                    client.player.getItemCooldownManager().isCoolingDown(client.player.getMainHandStack().getItem()), client.isPaused(), client.currentScreen);
            client.getServer().execute(() -> {
                var player = player(client);
                LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_SERVER targets={} state={} health={} active={} yaw={} pitch={}",
                        net.askcraft.justifylasers.laser.SaberCombat.targets(player, (LaserSaberItem) player.getMainHandStack().getItem()).size(),
                        net.askcraft.justifylasers.laser.SaberCombat.state(player), target.getHealth(), LaserSaberItem.active(player.getMainHandStack()), player.getYaw(), player.getPitch());
            });
        }
        if (tick == 202) {
            client.options.attackKey.setPressed(false);
            client.getServer().execute(() -> health = target.getHealth());
            capture(client, "single-slash");
        }
        if (tick == 215) {
            if (health >= 200 || health < 150) throw new AssertionError("Network saber attack damage/rate invalid: " + health);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_NETWORK_COMBAT_PASSED health={}", health);
            client.getServer().execute(() -> {
                target.discard();
                player(client).teleport(client.getServer().getOverworld(), 380.5, 2, 381.7, 0, 0);
            });
        }
        if (tick >= 230 && tick < 263) client.options.attackKey.setPressed(true);
        if (tick == 263) client.options.attackKey.setPressed(false);
        if (tick == 277) {
            if (marks() == 0) throw new AssertionError("Saber did not scorch the wall");
            capture(client, "wall-scorches");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_SCORCH_SMOKE_PASSED marks={}", marks());
            client.getServer().execute(() -> {
                ItemStack staff = new ItemStack(ModBlocks.LIGHT_STAFF);
                LaserSaberItem.setActive(staff, true);
                GameVersion.setCubeColor(staff, LaserColor.VIOLET.ordinal());
                player(client).getInventory().setStack(0, staff);
                player(client).teleport(client.getServer().getOverworld(), 380.5, 2, 379.5, 0, 0);
            });
        }
        if (tick == 306) capture(client, "staff-violet");
        if (tick >= 310 && tick < 338) client.options.attackKey.setPressed(true);
        if (tick == 322) capture(client, "staff-slash");
        if (tick == 338) {
            client.options.attackKey.setPressed(false);
            client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        }
        if (tick == 362) capture(client, "staff-third-person");
        if (tick == 363) { client.player.setPitch(-89); client.player.prevPitch = -89; }
        if (tick == 371) capture(client, "third-person-pitch-up");
        if (tick == 375) { client.player.setPitch(0); client.player.prevPitch = 0; }
        if (tick == 380) {
            client.options.setPerspective(Perspective.FIRST_PERSON);
            KeyBinding.onKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_J, 0));
        }
        if (tick == 392) {
            if (!(client.currentScreen instanceof ClientSettingsScreen)) throw new AssertionError("Registered settings key did not open the menu");
            if (ClientSettings.get().cubeMagnification != 1.8) throw new AssertionError("Wrong default cube zoom");
            capture(client, "settings-optics");
            button(client, "cube_lenses").onPress();
            SliderWidget zoom = client.currentScreen.children().stream().filter(SliderWidget.class::isInstance).map(SliderWidget.class::cast).findFirst().orElseThrow();
            zoom.mouseClicked(zoom.getX() + zoom.getWidth() - 4, zoom.getY() + 8, 0);
            zoom.mouseReleased(zoom.getX() + zoom.getWidth() - 4, zoom.getY() + 8, 0);
            client.currentScreen.close();
        }
        if (tick == 405) {
            ClientSettings.load(client.runDirectory.toPath().resolve("config/justifylasers-client.json"));
            if (ClientSettings.get().cubeLenses || ClientSettings.get().cubeMagnification != 3) throw new AssertionError("Optical settings did not save/reload");
            client.setScreen(new ClientSettingsScreen(null));
            button(client, "effects").onPress();
        }
        if (tick == 418) {
            capture(client, "settings-effects");
            button(client, "scorch_marks").onPress();
        }
        if (tick == 430) {
            if (marks() != 0) throw new AssertionError("Disabling decals did not release their geometry");
            button(client, "audio").onPress();
        }
        if (tick == 441) capture(client, "settings-audio");
        if (tick == 452) {
            button(client, "reset").onPress();
            client.currentScreen.close();
            if (ClientSettings.get().cubeMagnification != 1.8 || !ClientSettings.get().scorchMarks) throw new AssertionError("Reset failed");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("CLIENT_SETTINGS_SMOKE_PASSED key={} zoom={} persistence=true decals=true",
                    ClientSettingsKey.OPEN.getBoundKeyLocalizedText().getString(), ClientSettings.get().cubeMagnification);
            client.reloadResources();
        }
        if (tick == 485) {
            capture(client, "resource-reloaded");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_WORLD_SMOKE_PASSED shader={}",
                    net.askcraft.justifylasers.client.compat.IrisCompatibility.isShaderPackInUse());
        }
        if (tick == 500) {
            client.getServer().execute(() -> {
                var stack = new ItemStack(ModBlocks.LASER_SABER);
                LaserSaberItem.setActive(stack, true);
                GameVersion.setCubeColor(stack, LaserColor.CYAN.ordinal());
                player(client).getInventory().setStack(0, stack);
                player(client).teleport(client.getServer().getOverworld(), 380.5, 2, 383.65, 0, 0);
            });
        }
        if (tick == 525) capture(client, "native-hand-near-wall");
        if (tick == 528) verifyCore(client, "native-hand-near-wall", true);
        if (tick == 530) client.options.hudHidden = true;
        if (tick == 540) capture(client, "f1-hidden");
        if (tick == 546) verifyCore(client, "f1-hidden", false);
        if (tick == 548) {
            client.options.hudHidden = false;
            client.player.setPitch(90); client.player.prevPitch = 90;
        }
        if (tick == 565) capture(client, "pitch-down");
        if (tick == 570) { client.player.setPitch(-90); client.player.prevPitch = -90; }
        if (tick == 588) capture(client, "pitch-up");
        if (tick >= 590 && tick < 650) { client.player.setPitch(0); client.player.setYaw((tick - 590) * 6); }
        if (tick == 620) capture(client, "yaw-inertia");
        if (tick == 655) {
            client.player.setYaw(0); client.player.prevYaw = 0;
            client.getServer().execute(() -> player(client).setStackInHand(Hand.OFF_HAND, new ItemStack(Blocks.OBSIDIAN)));
        }
        if (tick == 680) capture(client, "native-hand-offhand");
        if (tick == 687 || tick == 698) KeyBinding.onKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_V, 0));
        if (tick == 695 && LaserSaberItem.active(client.player.getMainHandStack())) throw new AssertionError("V did not retract the blade through networking");
        if (tick == 705) {
            if (!LaserSaberItem.active(client.player.getMainHandStack())) throw new AssertionError("V did not reignite the blade through networking");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_NATIVE_HAND_SMOKE_CAPTURED f1=true poles=true yaw=true wall=true offhand=true");
            client.getServer().execute(() -> player(client).setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY));
        }
        if (tick >= 708 && tick <= 725) {
            client.player.getInventory().selectedSlot = tick % 3 == 0 ? 0 : 1;
        }
        if (tick == 727) {
            client.player.getInventory().selectedSlot = 0;
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_SLOT_SWITCH_REGRESSION_PASSED transitions=18 emptySlot=true");
        }
        if (tick >= 730) CubeLensSmoke.tick(client, tick - 730);
    }

    private static ButtonWidget button(MinecraftClient client, String key) {
        String label = Text.translatable("gui.justifylasers.client." + key).getString();
        return client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(button -> button.getMessage().getString().startsWith(label)).findFirst().orElseThrow();
    }

    private static void verifyCore(MinecraftClient client, String name, boolean expected) {
        try {
            var image = javax.imageio.ImageIO.read(new java.io.File(client.runDirectory, "screenshots/saber-" + name + ".png"));
            int white = 0;
            for (int y = (int)(image.getHeight() * .18); y < image.getHeight() * .5; y++)
                for (int x = (int)(image.getWidth() * .56); x < image.getWidth() * .7; x++) {
                    int pixel = image.getRGB(x, y);
                    if ((pixel >> 16 & 255) > 235 && (pixel >> 8 & 255) > 235 && (pixel & 255) > 235) white++;
                }
            if (expected ? white < 50 : white > 20)
                throw new AssertionError("Native hand core/F1 regression: " + name + ", white pixels=" + white);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SABER_CORE_VISIBILITY_PASSED frame={} expected={} whitePixels={}", name, expected, white);
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static int marks() {
        try {
            var field = net.askcraft.justifylasers.client.render.LaserScorchRenderer.class.getDeclaredField("MARKS");
            field.setAccessible(true);
            return ((net.askcraft.justifylasers.laser.LaserScorchMarks) field.get(null)).marks().size();
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.setTimeOfDay(9000);
        world.getGameRules().get(net.minecraft.world.GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, new Box(372, 0, 374, 390, 15, 390), entity -> true).forEach(net.minecraft.entity.Entity::discard);
        for (int x = 374; x <= 387; x++) for (int z = 375; z <= 387; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.STONE.getDefaultState());
            for (int y = 2; y <= 10; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        for (int x = 376; x <= 385; x++) for (int y = 2; y <= 6; y++)
            world.setBlockState(new BlockPos(x, y, 384), Blocks.SMOOTH_STONE.getDefaultState());
        var player = player(client);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.getInventory().clear();
        player.getInventory().setStack(0, new ItemStack(ModBlocks.LASER_SABER));
        player.getInventory().selectedSlot = 0;
        player.teleport(world, 380.5, 2, 380.5, 0, 0);
        target = new HuskEntity(EntityType.HUSK, world);
        target.setAiDisabled(true);
        target.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(200);
        target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.setHealth(200);
        target.setPosition(380.5, 2, 382.4);
        world.spawnEntity(target);
        health = 200;
    }

    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "saber-" + name + ".png", client.getFramebuffer(), text -> { });
    }
}
