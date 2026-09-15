package net.askcraft.justifylasers.smoke;

import net.askcraft.justifylasers.block.entity.LaserTurretBlockEntity;
import net.askcraft.justifylasers.client.compat.IrisCompatibility;
import net.askcraft.justifylasers.client.screen.LaserTurretScreen;
import net.askcraft.justifylasers.config.LaserConfig;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.platform.Platform;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.screen.LaserTurretScreenHandler;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class WeaponWorldSmoke {
    private static final BlockPos TURRET = new BlockPos(260, 2, 264);
    private static int loading, ticks;
    private static boolean started;
    private static org.joml.Matrix4f standingAim;

    public static void tick(MinecraftClient client) {
        if (client.getOverlay() != null) return;
        if (!started && ++loading >= 30) {
            started = true;
            net.askcraft.justifylasers.client.ClientSettings.reset();
            GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1440, 900);
            client.options.pauseOnLostFocus = false;
            client.options.hudHidden = false;
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.options.getGuiScale().setValue(3);
            client.options.getViewDistance().setValue(5);
            client.options.getFov().setValue(70);
            SmokeWorldAccess.start(client);
            return;
        }
        if (client.world == null || client.player == null) {
            if (started && client.currentScreen != null) client.currentScreen.children().stream()
                    .filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button -> button.getMessage().getString().equals(Text.translatable("selectWorld.backupWarning.proceed").getString())
                            || button.getMessage().getString().equals(Text.translatable("selectWorld.backupJoinSkipButton").getString()))
                    .findFirst().ifPresent(ButtonWidget::onPress);
            if (++loading == 150 && client.currentScreen != null)
                LoggerFactory.getLogger("justifylasers-client-smoke").info("WEAPON_FIXTURE_LOADING screen={} title={} buttons={}",
                        client.currentScreen.getClass().getName(), client.currentScreen.getTitle().getString(),
                        client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance)
                                .map(ButtonWidget.class::cast).map(button -> button.getMessage().getString()).toList());
            if (loading > 1200) throw new AssertionError("Weapon fixture did not load");
            return;
        }
        int frame = ++ticks;
        if (frame == 50) client.getServer().execute(() -> scene(client));
        if (frame == 110) {
            var turret = (LaserTurretBlockEntity) client.world.getBlockEntity(TURRET);
            if (turret == null || !turret.firing()) throw new AssertionError("Turret failed to acquire and fire on hostile target");
            capture(client, "turret-firing");
            client.getServer().execute(() -> {
                ((LaserTurretBlockEntity) client.getServer().getOverworld().getBlockEntity(TURRET)).toggle(0);
                player(client).teleport(client.getServer().getOverworld(), 260.5, 2, 253, 0, 0);
                player(client).getInventory().setStack(0, new ItemStack(ModBlocks.LASER_GUN));
                player(client).getInventory().selectedSlot = 0;
            });
        }
        if (frame == 145) {
            capture(client, "first-person-idle");
            client.options.attackKey.setPressed(true);
        }
        if (frame >= 145 && frame < 255) client.options.attackKey.setPressed(true);
        if (frame >= 150 && frame <= 170 && frame % 10 == 0)
            LoggerFactory.getLogger("justifylasers-client-smoke").info("GUN_INPUT frame={} pressed={} eligible={} clientUsing={} serverUsing={} screen={}",
                    frame, client.options.attackKey.isPressed(), net.askcraft.justifylasers.client.LaserGunControls.firing(client),
                    client.player.isUsingItem(), player(client).isUsingItem(), client.currentScreen);
        if (frame >= 175 && frame < 205) client.options.useKey.setPressed(true);
        if (frame == 175) {
            if (!client.player.isUsingItem()) throw new AssertionError("Left-click gun control did not start firing");
            capture(client, "first-person-firing");
            client.getServer().execute(() -> client.getServer().getOverworld().getEntitiesByClass(ZombieEntity.class,
                    new Box(250, 0, 248, 275, 12, 275), entity -> true).forEach(net.minecraft.entity.Entity::discard));
        }
        if (frame == 205) {
            if (net.askcraft.justifylasers.client.render.LaserGunRenderer.aim(1) < 0.9F) throw new AssertionError("ADS failed to reach its pose");
            capture(client, "aimed-firing");
            client.options.useKey.setPressed(false);
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        if (frame == 230) { capture(client, "third-person-back"); client.options.setPerspective(Perspective.THIRD_PERSON_FRONT); }
        if (frame == 255) {
            capture(client, "third-person-front");
            client.options.attackKey.setPressed(false);
            client.player.stopUsingItem();
            client.getServer().execute(() -> player(client).stopUsingItem());
            client.options.setPerspective(Perspective.FIRST_PERSON);
            client.setScreen(new Gallery());
        }
        if (frame == 285) {
            TextureModelSmoke.verifyParticleSprites(client);
            capture(client, "item-gallery");
            client.setScreen(null);
            client.getServer().execute(() -> {
                player(client).teleport(client.getServer().getOverworld(), 261.5, 2, 264.5, 90, 0);
                Platform.openScreen(player(client), (LaserTurretBlockEntity) client.getServer().getOverworld().getBlockEntity(TURRET));
            });
        }
        if (frame == 315) {
            if (!(client.currentScreen instanceof LaserTurretScreen)) throw new AssertionError("Turret screen did not open");
            client.interactionManager.clickButton(client.player.currentScreenHandler.syncId, 2);
            client.interactionManager.clickButton(client.player.currentScreenHandler.syncId, 5);
            ClientPlatform.sendSettings(new LaserSettingsPacket(client.player.currentScreenHandler.syncId, 6, client.player.getGameProfile().getName()));
        }
        if (frame == 340) {
            var turret = (LaserTurretBlockEntity) client.world.getBlockEntity(TURRET);
            if ((turret.filter().flags() & 2) == 0 || turret.color() == 0xFF1717) throw new AssertionError("Turret settings did not synchronize");
            var handler = (LaserTurretScreenHandler) client.player.currentScreenHandler;
            if (handler.exclusionCount() != 1 || !handler.ownerName().equals(client.player.getGameProfile().getName()))
                throw new AssertionError("Turret owner / player exclusions did not round-trip");
            capture(client, "turret-menu");
            client.interactionManager.clickSlot(handler.syncId, 0, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
        }
        if (frame == 348) {
            var turret = (LaserTurretBlockEntity) client.world.getBlockEntity(TURRET);
            if (turret.hasGun() || !client.player.currentScreenHandler.getCursorStack().isOf(ModBlocks.LASER_GUN))
                throw new AssertionError("GUI gun removal left a stale client turret model");
            capture(client, "turret-gun-removed");
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 0, 0,
                    net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
        }
        if (frame == 356) {
            var turret = (LaserTurretBlockEntity) client.world.getBlockEntity(TURRET);
            if (!turret.hasGun() || !client.player.currentScreenHandler.getCursorStack().isEmpty())
                throw new AssertionError("GUI reinsertion did not restore the client turret model");
            capture(client, "turret-gun-reinserted");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("TURRET_INVENTORY_SYNC_SMOKE_PASSED");
            client.player.closeHandledScreen();
            client.reloadResources();
        }
        if (frame == 375) {
            LoggerFactory.getLogger("justifylasers-client-smoke").info("WEAPON_WORLD_SMOKE_PASSED shader={}", IrisCompatibility.isShaderPackInUse());
            client.options.hudHidden = false;
            client.getServer().execute(() -> scopeScene(client));
        }
        if (frame >= 390) {
            if (client.currentScreen != null) client.setScreen(null);
            client.player.setYaw(0);
            client.player.prevYaw = 0;
            client.player.setPitch(0);
            client.player.prevPitch = 0;
            client.options.attackKey.setPressed(false);
            client.options.useKey.setPressed(frame >= 440 && frame < 605 || frame >= 640 && frame < 710 || frame >= 775 && frame < 885);
        }
        if (frame == 435) capture(client, "scope-color-reference");
        if (frame == 480) {
            if (client.options.hudHidden || net.askcraft.justifylasers.client.render.LaserGunRenderer.aim(1) < 0.9F)
                throw new AssertionError("Scope color scene must render the aimed weapon");
            capture(client, "scope-color-day");
            client.getServer().execute(() -> {
                client.getServer().getOverworld().setTimeOfDay(18000);
                client.getServer().getOverworld().setBlockState(new BlockPos(260, 4, 277), Blocks.SEA_LANTERN.getDefaultState());
            });
        }
        if (frame == 530) {
            capture(client, "scope-color-night");
            client.reloadResources();
        }
        if (frame == 600) {
            capture(client, "scope-color-reloaded");
        }
        if (frame == 635) {
            capture(client, "scope-color-night-reference");
            verifyScopeExposure(client);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SCOPE_COLOR_SMOKE_CAPTURED shader={}", IrisCompatibility.isShaderPackInUse());
            client.getServer().execute(() -> {
                player(client).getAbilities().flying = false;
                player(client).sendAbilitiesUpdate();
            });
        }
        if (frame == 675) {
            standingAim = aimedPose(client);
            capture(client, "scope-standing");
        }
        if (frame >= 680 && frame < 706) client.options.forwardKey.setPressed(true);
        if (frame == 700) {
            LoggerFactory.getLogger("justifylasers-client-smoke").info("ADS_WALK_STATE aim={} aiming={} mounting={} sprinting={} use={} position={} target={}",
                    net.askcraft.justifylasers.client.render.LaserGunRenderer.aim(1),
                    net.askcraft.justifylasers.client.LaserGunControls.aiming(client),
                    net.askcraft.justifylasers.client.LaserGunControls.mounting(client),client.player.isSprinting(),
                    client.options.useKey.isPressed(),client.player.getPos(),client.crosshairTarget);
            if (client.player.getVelocity().horizontalLength() < 0.02) throw new AssertionError("ADS walking test did not move the player");
            var walkingAim = aimedPose(client);
            float error = 0;
            for (int row=0; row<4; row++) for (int col=0; col<4; col++)
                error=Math.max(error,Math.abs(standingAim.get(col,row)-walkingAim.get(col,row)));
            if(error>0.00001) throw new AssertionError("Walking changed the settled ADS pose: " + error);
            capture(client, "scope-walking");
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SCOPE_STABLE_ADS_SMOKE_PASSED matrixError={}",error);
        }
        if(frame==710) {
            client.options.forwardKey.setPressed(false);
            client.getServer().execute(() -> scopeScene(client));
            net.askcraft.justifylasers.client.ClientSettings.get().scopeLens = false;
        }
        if (frame == 770) capture(client, "scope-setting-reference");
        if (frame == 820) {
            if (net.askcraft.justifylasers.client.render.LaserGunRenderer.aim(1) < 0.99F)
                throw new AssertionError("Disabling the scope lens also disabled aiming");
            capture(client, "scope-lens-disabled");
        }
        if (frame == 835) {
            verifyDisabledScope(client);
            net.askcraft.justifylasers.client.ClientSettings.get().scopeLens = true;
        }
        if (frame == 870) capture(client, "scope-lens-reenabled");
        if (frame == 885) {
            client.options.useKey.setPressed(false);
            client.scheduleStop();
        }
    }

    private static void scopeScene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        var colors = new net.minecraft.block.Block[]{Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE,
                Blocks.GREEN_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.WHITE_CONCRETE};
        for (int x = 0; x < colors.length; x++) for (int y = 2; y < 20; y++)
            world.setBlockState(new BlockPos(256 + x, y, 278), colors[x].getDefaultState());
        for (int x=256;x<=265;x++) for(int y=2;y<=6;y++) world.setBlockState(new BlockPos(x,y,270),Blocks.AIR.getDefaultState());
        world.setBlockState(new BlockPos(260, 4, 277), Blocks.AIR.getDefaultState());
        world.setTimeOfDay(9000);
        player(client).teleport(world, 260.0, 2, 253.5, 0, 0);
        player(client).getInventory().setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        player(client).getInventory().selectedSlot = 0;
    }

    private static void scene(MinecraftClient client) {
        var world = client.getServer().getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, client.getServer());
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, client.getServer());
        world.setTimeOfDay(9000);
        for (int x = 251; x <= 273; x++) for (int z = 249; z <= 273; z++) {
            world.setBlockState(new BlockPos(x, 1, z), Blocks.POLISHED_DEEPSLATE.getDefaultState());
            for (int y = 2; y <= 9; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
        }
        world.getOtherEntities(null, new Box(250, 0, 248, 275, 12, 275), entity -> !(entity instanceof net.minecraft.entity.player.PlayerEntity))
                .forEach(net.minecraft.entity.Entity::discard);
        for (int x = 256; x <= 265; x++) for (int y = 2; y <= 6; y++) world.setBlockState(new BlockPos(x, y, 270), Blocks.IRON_BLOCK.getDefaultState());
        world.setBlockState(TURRET, ModBlocks.LASER_TURRET.getDefaultState());
        var turret = (LaserTurretBlockEntity) world.getBlockEntity(TURRET);
        turret.setOwner(player(client));
        turret.setStack(0, new ItemStack(ModBlocks.LASER_GUN));
        turret.setStack(1, new ItemStack(ModLaserParts.MODULES.get(LaserModule.TARGET_FILTER)));
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        zombie.setAiDisabled(true);
        zombie.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
        zombie.setPersistent();
        zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
        zombie.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        zombie.setHealth(1000);
        zombie.refreshPositionAndAngles(260.5, 2, 258.5, 0, 0);
        world.spawnEntity(zombie);
        var player = player(client);
        player.changeGameMode(GameMode.CREATIVE);
        player.getInventory().clear();
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
        player.teleport(world, 264.3, 3.0, 261.1, 48, 12);
        LaserConfig.get().laserVolume = 0.2;
    }

    private static ServerPlayerEntity player(MinecraftClient client) { return client.getServer().getPlayerManager().getPlayer(client.player.getUuid()); }
    private static void capture(MinecraftClient client, String name) {
        ScreenshotRecorder.saveScreenshot(client.runDirectory, "weapon-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-")
                + name + ".png", client.getFramebuffer(), text -> { });
    }

    private static void verifyScopeExposure(MinecraftClient client) {
        String prefix = "screenshots/weapon-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-") + "scope-color-";
        try {
            var reference = javax.imageio.ImageIO.read(new java.io.File(client.runDirectory, prefix + "reference.png"));
            var scoped = javax.imageio.ImageIO.read(new java.io.File(client.runDirectory, prefix + "day.png"));
            int error = 0;
            // Two flat concrete fields on either side of the reticle; the lens magnifies the same frame by 2.5x.
            for (int sign : new int[]{-1, 1}) {
                int x = scoped.getWidth() / 2 + sign * scoped.getWidth() / 36;
                int sourceX = scoped.getWidth() / 2 + Math.round((x - scoped.getWidth() / 2F) / 2.5F);
                int actual = scoped.getRGB(x, scoped.getHeight() / 2);
                int expected = reference.getRGB(sourceX, reference.getHeight() / 2);
                for (int shift : new int[]{0, 8, 16}) error = Math.max(error, Math.abs((actual >> shift & 255) - (expected >> shift & 255)));
            }
            // Temporal AA and exposure may vary slightly when the gun moves into view.
            if (error > 18) throw new AssertionError("Scope changed scene colors: maximum channel error " + error);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SCOPE_EXPOSURE_SMOKE_PASSED maxChannelError={}", error);
            double expectedZoom=zoomError(reference,scoped,1.5), noZoom=zoomError(reference,scoped,1);
            if(expectedZoom>20 || noZoom<15 || expectedZoom>=noZoom*0.45)
                throw new AssertionError("ADS did not focus the world 1.5x: zoom error="+expectedZoom+", unzoomed error="+noZoom);
            double lensZoom=lensZoomError(reference,scoped,2.5), cameraOnly=lensZoomError(reference,scoped,1.5);
            if(lensZoom>20 || cameraOnly<10 || lensZoom>=cameraOnly*0.45)
                throw new AssertionError("Lens did not magnify the target 2.5x: zoom error="+lensZoom+", camera-only error="+cameraOnly);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SCOPE_MAGNIFICATION_SMOKE_PASSED camera=1.5 lens=2.5 cameraPixelError={} lensPixelError={} cameraOnlyError={}",expectedZoom,lensZoom,cameraOnly);
        } catch (java.io.IOException e) {
            throw new AssertionError("Scope comparison captures are missing", e);
        }
    }

    private static double zoomError(java.awt.image.BufferedImage reference,java.awt.image.BufferedImage scoped,double scale) {
        int w=reference.getWidth(),h=reference.getHeight(), y=h/4;
        int sy=(int)Math.round(h/2d+(y-h/2d)/scale);
        long total=0; int count=0;
        // Above the weapon, compare the positions of several colored wall edges, not just flat swatches.
        for(int x=w/6;x<w*5/6;x+=3) {
            int sx=(int)Math.round(w/2d+(x-w/2d)/scale);
            int actual=scoped.getRGB(x,y), expected=reference.getRGB(sx,sy);
            for(int shift:new int[]{0,8,16}) {total+=Math.abs((actual>>shift&255)-(expected>>shift&255));count++;}
        }
        return (double)total/count;
    }

    private static void verifyDisabledScope(MinecraftClient client) {
        String prefix = "screenshots/weapon-" + (IrisCompatibility.isShaderPackInUse() ? "kappa-" : "vanilla-");
        try {
            var reference = javax.imageio.ImageIO.read(new java.io.File(client.runDirectory, prefix + "scope-setting-reference.png"));
            var disabled = javax.imageio.ImageIO.read(new java.io.File(client.runDirectory, prefix + "scope-lens-disabled.png"));
            double maximumError = 0;
            // Compare chart-edge positions, not moving clouds or auto-exposure between hip-fire and ADS.
            // The lower row is inside the lens, above the reticle and foreground turret.
            for (int row : new int[]{-30, -reference.getHeight() / 4}) for (int side : new int[]{-1, 1}) {
                int original = chartEdge(reference, reference.getHeight() / 2 + Math.round(row / 1.5F), side, 12, 40);
                int actual = chartEdge(disabled, disabled.getHeight() / 2 + row, side, 18, 70);
                maximumError = Math.max(maximumError, Math.abs(actual - original * 1.5));
            }
            if (maximumError > 3)
                throw new AssertionError("Disabled lens must keep exactly 1.5x ADS: chart edge error=" + maximumError);
            LoggerFactory.getLogger("justifylasers-client-smoke").info("SCOPE_LENS_SETTING_SMOKE_PASSED camera=1.5 aperture=1.5 maxEdgeError={}", maximumError);
        } catch (java.io.IOException exception) { throw new AssertionError("Missing disabled-scope capture", exception); }
    }

    private static int chartEdge(java.awt.image.BufferedImage image, int row, int side, int min, int max) {
        int strongest = 0, distance = 0, center = image.getWidth() / 2;
        for (int offset = min; offset <= max; offset++) {
            int a = image.getRGB(center + side * (offset - 1), row);
            int b = image.getRGB(center + side * (offset + 1), row);
            int contrast = 0;
            for (int channel : new int[]{0, 8, 16}) contrast += Math.abs((a >> channel & 255) - (b >> channel & 255));
            if (contrast > strongest) { strongest = contrast; distance = offset; }
        }
        if (strongest < 12) throw new AssertionError("Missing colored calibration edge");
        return distance;
    }

    private static org.joml.Matrix4f aimedPose(MinecraftClient client) {
        try {
            var method=net.askcraft.justifylasers.client.render.LaserGunRenderer.class.getDeclaredMethod("firstPersonPose",
                    net.minecraft.client.network.AbstractClientPlayerEntity.class,Hand.class,float.class,float.class,float.class,net.minecraft.client.util.math.MatrixStack.class);
            method.setAccessible(true);
            var matrices=new net.minecraft.client.util.math.MatrixStack();
            method.invoke(null,client.player,Hand.MAIN_HAND,1F,0F,0F,matrices);
            return new org.joml.Matrix4f(matrices.peek().getPositionMatrix());
        } catch(ReflectiveOperationException e) {throw new AssertionError("Cannot inspect ADS pose",e);}
    }

    private static double lensZoomError(java.awt.image.BufferedImage reference,java.awt.image.BufferedImage scoped,double scale) {
        int cx=reference.getWidth()/2,cy=reference.getHeight()/2;
        int total=0,count=0;
        for(int offset=-64;offset<=64;offset++) {
            // Exclude both reticles: the reference crosshair also grows when mapped into the 2.5x lens.
            if(Math.abs(offset)<38) continue;
            int actual=scoped.getRGB(cx+offset,cy),expected=reference.getRGB(cx+(int)Math.round(offset/scale),cy);
            for(int shift:new int[]{0,8,16}) {total+=Math.abs((actual>>shift&255)-(expected>>shift&255));count++;}
        }
        return (double)total/count;
    }

    private static final class Gallery extends Screen {
        private final List<ItemStack> items = new ArrayList<>();
        Gallery() {
            super(Text.literal("Model gallery"));
            ModLaserParts.CRYSTALS.values().forEach(item -> items.add(new ItemStack(item)));
            ModLaserParts.MODULES.values().forEach(item -> items.add(new ItemStack(item)));
            for (var item : new net.minecraft.item.Item[]{ModBlocks.CONFIGURATOR, ModBlocks.LASER_MIRROR.asItem(), ModBlocks.BEAM_SPLITTER.asItem(),
                    ModBlocks.ENERGY_RECEIVER.asItem(), ModBlocks.LASER_GUN, ModBlocks.LASER_TURRET.asItem()}) items.add(new ItemStack(item));
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xFF1E242A);
            for (int i = 0; i < items.size(); i++) {
                int x = 40 + i % 8 * 48, y = 44 + i / 8 * 56;
                context.fill(x - 1, y - 1, x + 17, y + 17, 0xFF6D777F);
                context.fill(x, y, x + 16, y + 16, 0xFF303840);
                context.drawItem(items.get(i), x, y);
            }
            context.getMatrices().push();
            context.getMatrices().translate(width / 2.0 - 48, height - 93, 0);
            context.getMatrices().scale(6, 6, 1);
            context.drawItem(new ItemStack(ModBlocks.LASER_GUN), 0, 0);
            context.getMatrices().pop();
        }
        @Override public boolean shouldPause() { return false; }
    }
    private WeaponWorldSmoke() { }
}
