package net.askcraft.justifylasers.laser;

import io.netty.buffer.Unpooled;
import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class LaserDamageSettingsGameTests implements FabricGameTest {
    private static final BlockPos EMITTER_POS = new BlockPos(1, 2, 2);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void settingRangesKeepPreviousDefaults(TestContext context) {
        close(context, 0.5D, LaserDamage.damageForStep(LaserDamage.DEFAULT_DAMAGE_STEP), "Default damage");
        close(context, 1.0D, LaserDamage.knockbackForStep(LaserDamage.DEFAULT_KNOCKBACK_STEP), "Default knockback");
        close(context, 20, LaserDamage.MAX_HITS_PER_SECOND, "Default hit rate");
        close(context, 0.1D, LaserDamage.damageForStep(Integer.MIN_VALUE), "Minimum damage");
        close(context, 20.0D, LaserDamage.damageForStep(Integer.MAX_VALUE), "Maximum damage");
        close(context, 0.0D, LaserDamage.knockbackForStep(Integer.MIN_VALUE), "Minimum knockback");
        close(context, 10.0D, LaserDamage.knockbackForStep(Integer.MAX_VALUE), "Maximum knockback");
        close(context, 1, LaserDamage.clampHitsPerSecond(Integer.MIN_VALUE), "Minimum hit rate");
        close(context, 20, LaserDamage.clampHitsPerSecond(Integer.MAX_VALUE), "Maximum hit rate");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void allHitRatesAreEvenlyDistributed(TestContext context) {
        for (int rate = 1; rate <= 20; rate++) {
            int previousHit = -1;
            int hits = 0;
            context.assertTrue(LaserDamage.isHitTick(0L, rate), "First tick in the hit cycle must hit");
            for (int tick = 0; tick < 200; tick++) {
                if (LaserDamage.isHitTick(tick, rate)) {
                    hits++;
                    if (previousHit >= 0) {
                        int gap = tick - previousHit;
                        context.assertTrue(gap == 20 / rate || gap == (20 + rate - 1) / rate,
                                "Uneven interval for " + rate + " hits/s");
                    }
                    previousHit = tick;
                }
                if (tick % 20 == 19) {
                    close(context, rate * (tick + 1) / 20, hits, "Hit count for " + rate + " hits/s");
                }
            }
            for (long tick : new long[]{0L, 19L, 20L, 10_000_003L, Long.MAX_VALUE}) {
                context.assertTrue(LaserDamage.isHitTick(tick % 20L, rate) == LaserDamage.isHitTick(tick, rate),
                        "Hit schedule must remain stable after long uptime");
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void settingsSurviveSavingAndStayPerEmitter(TestContext context) {
        LaserEmitterBlockEntity original = freshEmitter();
        PropertyDelegate settings = original.getPropertyDelegate();
        settings.set(2, LaserColor.CYAN.ordinal());
        settings.set(4, 1);
        settings.set(6, 155);
        settings.set(9, 37);
        settings.set(10, 0);
        settings.set(11, 7);

        LaserEmitterBlockEntity restored = freshEmitter();
        restored.readNbt(original.createNbt());
        for (int index = 0; index < LaserEmitterBlockEntity.PROPERTY_COUNT; index++) {
            close(context, settings.get(index), restored.getPropertyDelegate().get(index), "Saved property " + index);
        }
        restored.getPropertyDelegate().set(9, 11);
        close(context, 37, settings.get(9), "Changing one emitter must not affect another");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldSavesUseDefaultsAndInvalidValuesAreClamped(TestContext context) {
        LaserEmitterBlockEntity emitter = freshEmitter();
        NbtCompound legacy = new NbtCompound();
        legacy.putBoolean("DamageEntities", true);
        legacy.putInt("BeamWidthStep", 200);
        legacy.putInt("Color", LaserColor.BLUE.ordinal());
        emitter.readNbt(legacy);
        PropertyDelegate settings = emitter.getPropertyDelegate();
        close(context, 5, settings.get(9), "Legacy damage");
        close(context, 10, settings.get(10), "Legacy knockback");
        close(context, 20, settings.get(11), "Legacy hit rate");
        close(context, 1, settings.get(4), "Existing damage toggle");
        close(context, 200, settings.get(6), "Existing beam width");
        close(context, LaserColor.BLUE.ordinal(), settings.get(2), "Existing color");

        NbtCompound invalid = legacy.copy();
        invalid.putInt("DamageStep", Integer.MIN_VALUE);
        invalid.putInt("KnockbackStep", Integer.MAX_VALUE);
        invalid.putInt("HitsPerSecond", 0);
        emitter.readNbt(invalid);
        close(context, 1, settings.get(9), "Clamped saved damage");
        close(context, 100, settings.get(10), "Clamped saved knockback");
        close(context, 1, settings.get(11), "Clamped saved rate");
        settings.set(9, Integer.MAX_VALUE);
        settings.set(10, Integer.MIN_VALUE);
        settings.set(11, Integer.MAX_VALUE);
        close(context, 200, settings.get(9), "Clamped property damage");
        close(context, 0, settings.get(10), "Clamped property knockback");
        close(context, 20, settings.get(11), "Clamped property rate");
        emitter.readNbt(legacy);
        close(context, 5, settings.get(9), "Reading an old save must restore defaults, not retain stale settings");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void packetsPreserveFullSliderValues(TestContext context) {
        context.assertTrue(ServerPlayNetworking.getGlobalReceivers().contains(LaserSettingsPacket.ID),
                "The settings packet receiver must be registered");
        for (int id : new int[]{1000, 1100, 1200, 2001, 2005, 2200, 3000, 3010, 3100, 4001, 4007, 4020, 5001, 5064, 5256, 5512}) {
            LaserSettingsPacket packet = new LaserSettingsPacket(42, id);
            PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
            try {
                packet.write(buffer);
                LaserSettingsPacket decoded = new LaserSettingsPacket(buffer);
                context.assertTrue(packet.equals(decoded), "Packet must preserve slider ID " + id);
                context.assertFalse(buffer.isReadable(), "Packet decoder must consume the payload");
            } finally {
                buffer.release();
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void menuChangesAreValidatedAndSynchronized(TestContext context) {
        LaserEmitterBlockEntity emitter = placeEmitter(context);
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.setPosition(context.getAbsolute(Vec3d.ofCenter(EMITTER_POS)));
        LaserEmitterScreenHandler serverMenu = new LaserEmitterScreenHandler(42, player.getInventory(), emitter);
        player.currentScreenHandler = serverMenu;
        PacketByteBuf openingData = new PacketByteBuf(Unpooled.buffer());
        LaserEmitterScreenHandler clientMenu;
        try {
            openingData.writeBlockPos(emitter.getPos());
            clientMenu = new LaserEmitterScreenHandler(42, player.getInventory(), openingData.readBlockPos());
        } finally {
            openingData.release();
        }
        serverMenu.addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {
                clientMenu.setProperty(property, value);
            }
        });
        for (int id : new int[]{2025, 3000, 4007, 1175, 5512}) {
            context.assertTrue(new LaserSettingsPacket(42, id).apply(player), "Valid slider setting " + id);
        }
        close(context, 25, clientMenu.getDamageStep(), "Synchronized damage");
        close(context, 0, clientMenu.getKnockbackStep(), "Synchronized zero knockback");
        close(context, 7, clientMenu.getHitsPerSecond(), "Synchronized hit rate");
        close(context, 175, clientMenu.getBeamWidthStep(), "Existing thickness slider");
        close(context, 512, clientMenu.getBeamRange(), "Synchronized full-range value");
        context.assertTrue(clientMenu.damagesEntities(), "Editing values must not change the damage toggle");

        NbtCompound beforeInvalidPackets = emitter.createNbt();
        for (int id : new int[]{-1, 13, 999, 1201, 2000, 2201, 2999, 3101, 4000, 4021, 5000, 5513, 5999, 6101, Integer.MAX_VALUE}) {
            context.assertFalse(new LaserSettingsPacket(42, id).apply(player), "Reject invalid setting " + id);
        }
        context.assertFalse(new LaserSettingsPacket(41, 2005).apply(player), "Reject stale menu ID");
        player.currentScreenHandler = player.playerScreenHandler;
        context.assertFalse(new LaserSettingsPacket(42, 2005).apply(player), "Reject packets after closing the menu");
        player.currentScreenHandler = serverMenu;
        player.setPosition(player.getPos().add(20.0D, 0.0D, 0.0D));
        context.assertFalse(new LaserSettingsPacket(42, 2005).apply(player), "Reject out-of-reach changes");
        context.assertTrue(beforeInvalidPackets.equals(emitter.createNbt()), "Rejected packets must not mutate settings");
        context.removeBlock(EMITTER_POS);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void configuredRatesControlActualEmitterDamage(TestContext context) {
        LaserEmitterBlockEntity emitter = placeEmitter(context);
        PropertyDelegate settings = emitter.getPropertyDelegate();
        settings.set(9, 1);
        settings.set(10, 0);
        VillagerEntity target = target(context);
        for (int rate = 1; rate <= 20; rate++) {
            settings.set(11, rate);
            target.setHealth(20.0F);
            for (int tick = 1; tick <= 60; tick++) {
                LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
                if (tick % 20 == 0) {
                    close(context, 20.0D - rate * 0.1D * tick / 20, target.getHealth(), "Actual damage at " + rate + " hits/s");
                }
            }
        }
        close(context, 0.0D, target.getVelocity().lengthSquared(), "Zero knockback must leave damage enabled");
        settings.set(4, 0);
        float health = target.getHealth();
        for (int tick = 0; tick < 20; tick++) {
            LaserEmitterBlockEntity.serverTick(context.getWorld(), emitter.getPos(), emitter.getCachedState(), emitter);
        }
        close(context, health, target.getHealth(), "Damage toggle must still disable configured hits");
        context.removeBlock(EMITTER_POS);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void configuredRateWorksWithRealServerTicks(TestContext context) {
        LaserEmitterBlockEntity emitter = placeEmitter(context);
        emitter.getPropertyDelegate().set(9, 5);
        emitter.getPropertyDelegate().set(10, 0);
        emitter.getPropertyDelegate().set(11, 7);
        VillagerEntity target = target(context);
        float[] initialHealth = new float[1];
        context.runAtTick(2, () -> initialHealth[0] = target.getHealth());
        context.runAtTick(22, () -> {
            close(context, 3.5D, initialHealth[0] - target.getHealth(), "Seven half-HP hits over twenty real ticks");
            context.removeBlock(EMITTER_POS);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void configuredDamageAndKnockbackKeepVanillaDefenses(TestContext context) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(20.0D);
        player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).setBaseValue(8.0D);
        player.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(0.5D);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
        float damage = 3.7F;
        float reducedDamage = DamageUtil.getDamageLeft(damage, 20.0F, 8.0F) * 0.8F;
        for (double multiplier : new double[]{0.0D, 0.5D, 1.0D, 2.0D, 10.0D}) {
            player.setHealth(20.0F);
            player.setVelocity(Vec3d.ZERO);
            LaserDamage.hit(player, LaserDamage.source(context.getWorld(), Vec3d.ZERO), Direction.EAST, damage, multiplier);
            close(context, 20.0D - reducedDamage, player.getHealth(), "Configured damage must respect defenses");
            close(context, 0.025D * multiplier * 0.5D, player.getVelocity().x, "Configured push must respect resistance");
        }
        context.complete();
    }

    private static LaserEmitterBlockEntity freshEmitter() {
        return new LaserEmitterBlockEntity(EMITTER_POS, ModBlocks.LASER_EMITTER.getDefaultState());
    }

    private static LaserEmitterBlockEntity placeEmitter(TestContext context) {
        context.setBlockState(EMITTER_POS, ModBlocks.LASER_EMITTER.getDefaultState()
                .with(LaserEmitterBlock.FACING, Direction.EAST));
        context.setBlockState(6, 2, 2, Blocks.OBSIDIAN);
        LaserEmitterBlockEntity emitter = (LaserEmitterBlockEntity) context.getBlockEntity(EMITTER_POS);
        emitter.getPropertyDelegate().set(4, 1);
        return emitter;
    }

    private static VillagerEntity target(TestContext context) {
        VillagerEntity target = context.spawnMob(EntityType.VILLAGER, new Vec3d(3.5D, 2.0D, 2.5D));
        target.setAiDisabled(true);
        target.setNoGravity(true);
        return target;
    }

    private static void close(TestContext context, double expected, double actual, String message) {
        context.assertTrue(Math.abs(expected - actual) < 0.0001D,
                message + ": expected " + expected + ", got " + actual);
    }
}
