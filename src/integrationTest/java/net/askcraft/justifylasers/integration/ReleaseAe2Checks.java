package net.askcraft.justifylasers.integration;

import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.platform.GameVersion;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Uses the optional mod's actual storage bus, not a replacement item-handler implementation. */
final class ReleaseAe2Checks {
    static void schedule(TestContext context) {
        try {
            var world = context.getWorld();
            var pos = context.getAbsolutePos(new BlockPos(4, 3, 3));
            world.setBlockState(pos, ModBlocks.POWERED_LASER_EMITTER.getDefaultState());
            var emitter = (LaserEmitterBlockEntity) world.getBlockEntity(pos);
            emitter.getPropertyDelegate().set(0, 0);
            var owner = IntegrationEnergy.serverPlayer(context);
            owner.setPosition(Vec3d.ofCenter(pos));
            emitter.initializeOwner(owner);
            var helper = Class.forName("appeng.api.parts.PartHelper");
            Method setPart = Arrays.stream(helper.getMethods()).filter(m -> m.getName().equals("setPart")).findFirst().orElseThrow();
            Object cable = Registries.ITEM.get(GameVersion.id("ae2", "fluix_glass_cable"));
            Object busItem = Registries.ITEM.get(GameVersion.id("ae2", "storage_bus"));
            Object center = setPart.invoke(null, world, pos.east(), null, owner, cable);
            Object bus = setPart.invoke(null, world, pos.east(), Direction.WEST, owner, busItem);
            context.assertTrue(center != null && bus != null, "Actual AE2 cable and storage bus placed");
            world.setBlockState(pos.east().south(), Registries.BLOCK.get(GameVersion.id("ae2", "creative_energy_cell")).getDefaultState());
            context.runAtTick(100, () -> {
                try {
                    Object storage = bus.getClass().getMethod("getInternalHandler").invoke(bus);
                    Class<?> storageType = Class.forName("appeng.api.storage.MEStorage");
                    Class<?> keyType = Class.forName("appeng.api.stacks.AEKey");
                    Class<?> actionType = Class.forName("appeng.api.config.Actionable");
                    Class<?> sourceType = Class.forName("appeng.api.networking.security.IActionSource");
                    Object key = Class.forName("appeng.api.stacks.AEItemKey").getMethod("of", ItemStack.class).invoke(null, new ItemStack(Items.DIAMOND));
                    Object source = sourceType.getMethod("empty").invoke(null);
                    Object simulate = actionType.getField("SIMULATE").get(null);
                    Object execute = actionType.getField("MODULATE").get(null);
                    Method insert = storageType.getMethod("insert", keyType, long.class, actionType, sourceType);
                    Method extract = storageType.getMethod("extract", keyType, long.class, actionType, sourceType);
                    long offered = (long) insert.invoke(storage, key, 8L, simulate, source);
                    Object node = bus.getClass().getMethod("getMainNode").invoke(bus);
                    Object online = Class.forName("appeng.api.networking.IManagedGridNode").getMethod("isOnline").invoke(node);
                    context.assertTrue(offered == 8 && diamonds(emitter) == 0,
                            "AE2 simulated insertion: accepted=" + offered + ", items=" + diamonds(emitter)
                                    + ", storage=" + storage.getClass().getName() + ", online=" + online);
                    context.assertTrue((long) insert.invoke(storage, key, 8L, execute, source) == 8 && diamonds(emitter) == 8,
                            "AE2 storage bus inserts into the emitter inventory");
                    context.assertTrue((long) extract.invoke(storage, key, 3L, simulate, source) == 3 && diamonds(emitter) == 8,
                            "AE2 extraction simulation is non-mutating");
                    context.assertTrue((long) extract.invoke(storage, key, 3L, execute, source) == 3 && diamonds(emitter) == 5,
                            "AE2 storage bus extracts exactly once");
                    context.assertTrue(emitter.togglePrivacy(owner), "Owner closes inventory access");
                    context.assertTrue((long) insert.invoke(storage, key, 1L, execute, source) == 0
                                    && (long) extract.invoke(storage, key, 1L, execute, source) == 0 && diamonds(emitter) == 5,
                            "Already-connected AE2 storage bus cannot bypass privacy");
                    emitter.togglePrivacy(owner);
                    context.assertTrue((long) extract.invoke(storage, key, 5L, execute, source) == 5 && diamonds(emitter) == 0,
                            "Public access resumes without recreating the storage bus");
                    org.slf4j.LoggerFactory.getLogger("justifylasers-release-audit").info(
                            "RELEASE_AE2_BUS_PASSED actual-storage-bus/insert/extract/simulate/cached-privacy/reopen");
                    owner.discard();
                    context.complete();
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError("AE2 storage bus audit could not call its runtime API", failure);
                }
            });
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("AE2 storage bus fixture could not be assembled", failure);
        }
    }

    private static int diamonds(LaserEmitterBlockEntity emitter) {
        int result = 0;
        for (int slot = 0; slot < emitter.size(); slot++) if (emitter.getStack(slot).isOf(Items.DIAMOND)) result += emitter.getStack(slot).getCount();
        return result;
    }
    private ReleaseAe2Checks() { }
}
