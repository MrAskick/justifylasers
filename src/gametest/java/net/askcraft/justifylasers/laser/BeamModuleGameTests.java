package net.askcraft.justifylasers.laser;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserPartBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.registry.ModBlocks;
import net.askcraft.justifylasers.registry.ModLaserParts;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.askcraft.justifylasers.screen.LaserModuleScreenHandler;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;

public class BeamModuleGameTests implements FabricGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 3, 3), MODULE = new BlockPos(3, 3, 3), TARGET = new BlockPos(6, 3, 3);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void installedAndWorldEffectsComposeWithoutReplacingEachOther(TestContext context) {
        var emitter = emitter(context);
        module(context, MODULE, LaserModule.ENTITY_HEAL);
        install(emitter, LaserModule.ENTITY_DAMAGE);
        var source = new Source(context, emitter.beamBehavior());
        var path = trace(source);
        context.assertTrue(path.segments().size() == 2 && path.last().behavior().entities().size() == 2,
                "Installed damage and placed healing coexist downstream");
        context.assertTrue(path.last().power() < 1, "Added healing consumes light");
        emitter.getPropertyDelegate().set(4, 0);
        context.assertTrue(trace(new Source(context, emitter.beamBehavior())).last().behavior().entities().size() == 1,
                "Disabling internal damage does not disable independent world healing");
        emitter.removeStack(LaserModule.ENTITY_DAMAGE.slot());
        path = trace(new Source(context, emitter.beamBehavior()));
        context.assertTrue(path.segments().get(0).behavior().entity().mode() == LaserEntityMode.NONE
                && path.last().behavior().entity().mode() == LaserEntityMode.HEAL, "World module changes only the downstream ray");
        context.assertTrue(path.last().power() > 0 && path.last().power() < 1, "An applied world mode consumes light");
        context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void worldModulesChainInOrderAndPreserveInternalSilkAndFilter(TestContext context) {
        var emitter = emitter(context);
        install(emitter, LaserModule.SILK_TOUCH); install(emitter, LaserModule.TARGET_FILTER);
        emitter.targetFilter().setFlags(LaserTargetFilter.PLAYERS);
        var mining = module(context, MODULE, LaserModule.BLOCK_DESTRUCTION);
        module(context, MODULE.east(), LaserModule.ENTITY_HEAL);
        module(context, MODULE.east(2), LaserModule.ENTITY_LIFT);
        var filter = module(context, MODULE.east(3), LaserModule.TARGET_FILTER);
        filter.filter().setFlags(LaserTargetFilter.HOSTILE);
        var behavior = trace(new Source(context, emitter.beamBehavior())).last().behavior();
        context.assertTrue(behavior.entities().size() == 2 && behavior.entities().get(1).mode() == LaserEntityMode.LIFT, "World healing and lifting compose");
        context.assertTrue(behavior.mining().enabled() && behavior.mining().silk(), "Placed mining retains the installed silk module");
        context.assertTrue(behavior.filter().flags() == LaserTargetFilter.HOSTILE, "World filter controls downstream effects");
        context.assertTrue(behavior.collector().equals(mining.getPos()), "Placed mining owns its loot destination");
        context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void insufficientLightStopsAfterModuleAndDisabledModulePassesThrough(TestContext context) {
        var module = module(context, MODULE, LaserModule.ENTITY_HEAL);
        var source = new Source(context, BeamBehavior.NONE);
        source.flux = module.operatingFlux() - 1;
        var path = trace(source);
        context.assertTrue(path.segments().size() == 1, "Cannot produce a free effect with insufficient light");
        NbtCompound data = module.createNbt(); data.putBoolean("Enabled", false); module.readNbt(data);
        path = trace(source);
        context.assertTrue(path.segments().size() == 2 && path.last().power() == 1 && !path.last().behavior().entity().active(),
                "Disabled modules neither change nor consume the beam");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placedModesHealRaiseAndLowerWithoutChangingHorizontalControl(TestContext context) {
        for (var mode : new LaserModule[]{LaserModule.ENTITY_HEAL, LaserModule.ENTITY_LIFT, LaserModule.ENTITY_LOWER}) {
            var mob = context.spawnMob(EntityType.VILLAGER, new Vec3d(5.5, 3, 3.5));
            mob.setAiDisabled(true); mob.setNoGravity(true); mob.setHealth(10);
            module(context, MODULE, mode);
            var source = new Source(context, BeamBehavior.NONE);
            mob.setVelocity(.04, 0, .03);
            new LaserBeamEffects().tick(source, trace(source));
            if (mode == LaserModule.ENTITY_HEAL) context.assertTrue(mob.getHealth() > 10 && mob.getHealth() < 10.5,
                    "Healing is real, scaled by the remaining light, and does not damage");
            else {
                context.assertTrue(mode == LaserModule.ENTITY_LIFT ? mob.getVelocity().y > 0 : mob.getVelocity().y < 0, "Correct vertical direction");
                context.assertTrue(mob.getVelocity().x == .04 && mob.getVelocity().z == .03 && mob.fallDistance == 0, "Retain movement and reset fall accumulation in the supported ray");
                context.assertTrue(LaserBeamEffects.supports(mob), "Server flight checks recognize actual beam support");
            }
            mob.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void registryBlacklistWhitelistAndNbtApplyToHealingToo(TestContext context) {
        var filter = new LaserTargetFilter();
        var cow = context.spawnMob(EntityType.COW, new Vec3d(5.5, 3, 3.5));
        cow.setAiDisabled(true); cow.setNoGravity(true); cow.setHealth(5);
        context.assertTrue(filter.toggleType("minecraft:cow") && !filter.allows(cow, null), "Blacklist excludes selected species");
        filter.toggleMode(); context.assertTrue(filter.allows(cow, null), "Whitelist permits selected species");
        context.assertFalse(filter.toggleType("missing:no_such_mob"), "Reject unregistered names");
        var copy = new LaserTargetFilter(); copy.read(filter.write());
        context.assertTrue(copy.whitelist() && copy.types().equals(filter.types()) && copy.allows(cow, null), "Filter selection survives save");
        copy.toggleMode();
        var source = new Source(context, BeamBehavior.NONE.withEntity(new BeamBehavior.EntityEffect(LaserEntityMode.HEAL, true, 5, 0, 20, false)).withFilter(copy, null));
        new LaserBeamEffects().tick(source, trace(source));
        context.assertTrue(cow.getHealth() == 5, "Blacklisted cows are not healed");
        cow.discard(); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void miningCollectsExactlyOnceAndOverflowIsStillLoot(TestContext context) {
        var emitter = emitter(context);
        for (var m : new LaserModule[]{LaserModule.BLOCK_DESTRUCTION, LaserModule.BLOCK_DROPS, LaserModule.SILK_TOUCH, LaserModule.BLOCK_COLLECTION}) install(emitter, m);
        emitter.getPropertyDelegate().set(3, 1); emitter.getPropertyDelegate().set(14, 100); emitter.getPropertyDelegate().set(53, 1);
        context.setBlockState(TARGET, Blocks.STONE);
        var source = new Source(context, emitter.beamBehavior());
        new LaserBeamEffects().tick(source, trace(source));
        context.assertTrue(context.getBlockState(TARGET).isAir() && emitter.getStack(10).isOf(Items.STONE) && emitter.getStack(10).getCount() == 1, "Collect the actual silk-touch loot");
        context.assertTrue(loot(context).isEmpty(), "Collected items are not duplicated on the ground");
        for (int slot = 10; slot < 19; slot++) emitter.setStack(slot, new ItemStack(Items.STONE, 64));
        context.setBlockState(TARGET, Blocks.STONE);
        context.assertTrue(MiningCollection.breakBlock(context.getWorld(), context.getAbsolutePos(TARGET), true, true, emitter), "Mine the overflow sample");
        context.assertTrue(loot(context).stream().mapToInt(item -> item.getStack().getCount()).sum() == 1, "Full storage leaves loot in the world");
        var restored = new LaserEmitterBlockEntity(emitter.getPos(), emitter.getCachedState()); restored.readNbt(emitter.createNbt());
        context.assertTrue(restored.size() == LaserEmitterBlockEntity.INVENTORY_SIZE && restored.getStack(18).getCount() == 64 && restored.collectsDrops(), "All nine storage slots and collection upgrade persist");
        loot(context).forEach(ItemEntity::discard); context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void collectionPreservesContainerContentsComponentsAndUnrelatedGroundItems(TestContext context) {
        var emitter = emitter(context);
        context.setBlockState(TARGET, Blocks.CHEST);
        var chest = (net.minecraft.block.entity.ChestBlockEntity) context.getBlockEntity(TARGET);
        ItemStack diamond = new ItemStack(Items.DIAMOND, 3); diamond.setCustomName(net.minecraft.text.Text.literal("Collector test"));
        chest.setStack(0, diamond);
        var oldItem = new ItemEntity(context.getWorld(), context.getAbsolutePos(TARGET).getX() + .5,
                context.getAbsolutePos(TARGET).getY() + .5, context.getAbsolutePos(TARGET).getZ() + .5, new ItemStack(Items.DIRT));
        context.getWorld().spawnEntity(oldItem);
        context.assertTrue(MiningCollection.breakBlock(context.getWorld(), context.getAbsolutePos(TARGET), true, false, emitter), "Break a container through vanilla loot");
        int diamonds = 0, chests = 0;
        for (int slot = 10; slot < 19; slot++) {
            ItemStack stack = emitter.getStack(slot);
            if (stack.isOf(Items.DIAMOND)) { diamonds += stack.getCount(); context.assertTrue(stack.hasCustomName(), "Item data preserved"); }
            if (stack.isOf(Items.CHEST)) chests += stack.getCount();
        }
        context.assertTrue(diamonds == 3 && chests == 1, "Container and contents collected once each");
        context.assertTrue(!oldItem.isRemoved() && oldItem.getStack().isOf(Items.DIRT), "Do not vacuum pre-existing ground items");
        oldItem.discard(); context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moduleMenuValidatesOwnershipOfOpenScreenAndPersistsSettings(TestContext context) {
        var block = module(context, MODULE, LaserModule.ENTITY_DAMAGE);
        var player = context.createMockSurvivalPlayer(); player.setPosition(Vec3d.ofCenter(block.getPos()));
        var handler = new LaserModuleScreenHandler(25, player.getInventory(), block); player.currentScreenHandler = handler;
        context.assertTrue(new LaserSettingsPacket(25, 2012).apply(player), "Set damage on the correct open module");
        context.assertFalse(new LaserSettingsPacket(24, 2100).apply(player), "Reject stale menu ID");
        context.assertFalse(new LaserSettingsPacket(25, 6000).apply(player), "Healing module cannot enable mining through a forged packet");
        var restored = new LaserPartBlockEntity(block.getPos(), block.getCachedState()); restored.readNbt(block.createNbt());
        context.assertTrue(restored.property(2) == 12, "World module settings persist");
        player.setPosition(player.getPos().add(20, 0, 0));
        context.assertFalse(new LaserSettingsPacket(25, 2005).apply(player), "Reject remote access beyond reach");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyModuleMountProcessesLightOnEveryAxis(TestContext context) {
        for (Direction mount : Direction.values()) {
            var state = ModLaserParts.DECORATIONS.get("silk_touch_module").getDefaultState().with(LaserPartBlock.MOUNT, mount);
            context.setBlockState(MODULE, state);
            var box = state.getCollisionShape(context.getWorld(), context.getAbsolutePos(MODULE)).getBoundingBox();
            for (Direction facing : Direction.values()) {
                Vec3d axis = Vec3d.of(facing.getVector());
                Vec3d center = Vec3d.of(context.getAbsolutePos(MODULE)).add(box.getCenter());
                var ray = LaserBeamTrace.traceFrom(context.getWorld(), center.subtract(axis.multiply(1.5)), axis, 3);
                context.assertTrue(ray.hasBlockHit() && ray.hitBlock().equals(context.getAbsolutePos(MODULE)), "Mounted hollow module intercepts its optical aperture: " + mount + "/" + facing);
            }
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void worldRangeCannotExtendPastTheTotalPathLimit(TestContext context) {
        for (int height : new int[]{2, 3}) context.setBlockState(SOURCE.up(height),
                ModLaserParts.DECORATIONS.get("advanced_range_module").getDefaultState());
        var source = new Source(context, BeamBehavior.NONE);
        source.axis = new Vec3d(0, 1, 0); source.range = 511;
        var path = trace(source);
        context.assertTrue(path.segments().size() == 3 && Math.abs(path.last().end().y - source.beamOrigin().y - 512) < 1e-5,
                "Two range modules share the remaining one-block extension, not two fresh range budgets");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void installedModeSurvivesMirrorSplitterAndCombiner(TestContext context) {
        var emitter = emitter(context); install(emitter, LaserModule.ENTITY_DAMAGE);
        context.setBlockState(MODULE, ModBlocks.LASER_MIRROR.getDefaultState().with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.UP));
        ((net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity) context.getBlockEntity(MODULE)).aim(new Vec3d(1, 0, -1));
        context.setBlockState(MODULE.south(2), ModBlocks.BEAM_SPLITTER.getDefaultState());
        var splitter = (net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity) context.getBlockEntity(MODULE.south(2));
        for (Direction side : Direction.values()) splitter.setPortMode(side, OpticPortMode.DISABLED);
        splitter.setPortMode(Direction.NORTH, OpticPortMode.INPUT); splitter.setPortMode(Direction.EAST, OpticPortMode.OUTPUT);
        context.setBlockState(MODULE.south(2).east(), ModBlocks.BEAM_COMBINER.getDefaultState()
                .with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.EAST));
        module(context, MODULE.south(2).east(2), LaserModule.ENTITY_HEAL);
        context.setBlockState(MODULE.south(2).east(4), Blocks.STONE);
        var path = trace(new Source(context, emitter.beamBehavior()));
        context.assertTrue(path.last().hasBlockHit() && path.last().hitBlock().equals(context.getAbsolutePos(MODULE.south(2).east(4))),
                "Route reaches the target through a mirror and directly touching splitter/combiner: " + path.segments());
        context.assertTrue(path.last().behavior().entity().mode() == LaserEntityMode.DAMAGE
                && path.last().behavior().entities().size() == 2, "Optics retain both installed and added effects");
        context.assertTrue(path.last().power() > 0 && path.last().power() < .95, "Combiner loss and added healing are paid");
        context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void liftAndHealComposeWhileEqualOpposingMovementCancels(TestContext context) {
        var lift = new BeamBehavior.EntityEffect(LaserEntityMode.LIFT, true, 10, 0, 20, false);
        var heal = new BeamBehavior.EntityEffect(LaserEntityMode.HEAL, true, 5, 0, 20, false);
        var lower = new BeamBehavior.EntityEffect(LaserEntityMode.LOWER, true, 10, 0, 20, false);
        for (boolean cancel : new boolean[]{false, true}) {
            var mob = context.spawnMob(EntityType.VILLAGER, new Vec3d(5.5, 3, 3.5));
            mob.setAiDisabled(true); mob.setNoGravity(true); mob.setHealth(10); mob.setVelocity(.04, 0, .03);
            var behavior = BeamBehavior.NONE.withEntity(lift).plusEntity(heal);
            if (cancel) behavior = behavior.plusEntity(lower);
            var source = new Source(context, behavior);
            new LaserBeamEffects().tick(source, trace(source));
            context.assertTrue(Math.abs(mob.getHealth() - 10.1) < .0001, "Both configurations heal exactly once at the fixed rate");
            context.assertTrue(cancel ? mob.getVelocity().y == 0 : mob.getVelocity().y > 0, "Opposing movement cancels, healing remains active");
            context.assertTrue(mob.getVelocity().x == .04 && mob.getVelocity().z == .03, "No horizontal steering");
            mob.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nestedAndPlacedIgnitionComposeWithDamageInEitherOrder(TestContext context) {
        var damage = module(context, MODULE, LaserModule.ENTITY_DAMAGE);
        long plainCost = damage.operatingFlux();
        context.assertFalse(damage.apply(BeamBehavior.NONE).entity().ignite(), "Damage without an upgrade does not ignite");
        damage.setStack(LaserPartBlockEntity.IGNITION_SLOT, new ItemStack(ModLaserParts.MODULES.get(LaserModule.IGNITION)));
        context.assertTrue(damage.apply(BeamBehavior.NONE).entity().ignite() && damage.operatingFlux() > plainCost,
                "Nested ignition enables fire and pays for it");
        damage.removeStack(LaserPartBlockEntity.IGNITION_SLOT);
        var ignition = module(context, MODULE.east(), LaserModule.IGNITION);
        var before = damage.apply(ignition.apply(BeamBehavior.NONE));
        var after = ignition.apply(damage.apply(BeamBehavior.NONE));
        context.assertTrue(before.entity().ignite() && after.entity().ignite(), "World ignition works before and after damage");
        context.assertTrue(before.mining().smelt() && after.mining().smelt() && !before.mining().enabled(),
                "Ignition preserves smelting without enabling mining on its own");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nestedMiningSlotsEnableLootSilkSmeltingAndCollection(TestContext context) {
        var mining = module(context, MODULE, LaserModule.BLOCK_DESTRUCTION);
        long plain = mining.operatingFlux();
        mining.setStack(LaserPartBlockEntity.DROPS_SLOT, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DROPS)));
        mining.setStack(LaserPartBlockEntity.SILK_SLOT, new ItemStack(ModLaserParts.MODULES.get(LaserModule.SILK_TOUCH)));
        mining.setStack(LaserPartBlockEntity.IGNITION_SLOT, new ItemStack(ModLaserParts.MODULES.get(LaserModule.IGNITION)));
        mining.setStack(LaserPartBlockEntity.COLLECTION_SLOT, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_COLLECTION)));
        var settings = mining.apply(BeamBehavior.NONE).mining();
        context.assertTrue(settings.drops() && settings.silk() && settings.smelt() && settings.collect() && mining.operatingFlux() > plain, "Upgrade slots affect the beam and its cost");
        context.assertFalse(mining.isValid(LaserPartBlockEntity.IGNITION_SLOT, new ItemStack(Items.DIRT)), "Upgrade slots do not accept arbitrary loot");
        context.setBlockState(TARGET, Blocks.IRON_ORE);
        context.assertTrue(MiningCollection.breakBlock(context.getWorld(), context.getAbsolutePos(TARGET), true, true, true, mining), "Silk ore can be smelted using its furnace recipe");
        context.assertTrue(mining.getStack(0).isOf(Items.IRON_INGOT) && mining.getStack(0).getCount() == 1 && loot(context).isEmpty(), "Smelted item collected once");
        var restored = new LaserPartBlockEntity(mining.getPos(), mining.getCachedState()); restored.readNbt(mining.createNbt());
        context.assertTrue(restored.getStack(0).isOf(Items.IRON_INGOT) && restored.apply(BeamBehavior.NONE).mining().smelt(), "Loot and nested upgrades survive NBT");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void smeltingDoesNotCookContainerContentsOrNamedItems(TestContext context) {
        var emitter = emitter(context);
        context.setBlockState(TARGET, Blocks.CHEST);
        var chest = (net.minecraft.block.entity.ChestBlockEntity) context.getBlockEntity(TARGET);
        var named = new ItemStack(Items.RAW_IRON, 3); named.setCustomName(net.minecraft.text.Text.literal("Keep this"));
        chest.setStack(0, named);
        MiningCollection.breakBlock(context.getWorld(), context.getAbsolutePos(TARGET), true, false, true, emitter);
        int raw = 0;
        for (int i = 10; i < 19; i++) if (emitter.getStack(i).isOf(Items.RAW_IRON)) {
            raw += emitter.getStack(i).getCount(); context.assertTrue(emitter.getStack(i).hasCustomName(), "Preserve custom item data");
        }
        context.assertTrue(raw == 3, "Container contents are not furnace inputs");
        context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyMiningSlotMigratesWithoutReplacingAnExistingEffect(TestContext context) {
        var emitter = emitter(context);
        for (boolean occupied : new boolean[]{false, true}) {
            if (occupied) install(emitter, LaserModule.ENTITY_HEAL);
            var saved = emitter.createNbt();
            var oldMining = new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION)).writeNbt(new NbtCompound());
            oldMining.putByte("Slot", (byte) 5);
            saved.getList("Items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE).add(oldMining);
            var restored = new LaserEmitterBlockEntity(emitter.getPos(), emitter.getCachedState()); restored.readNbt(saved);
            context.assertTrue(restored.hasModule(occupied ? LaserModule.ENTITY_HEAL : LaserModule.BLOCK_DESTRUCTION), "Preserve the active effect");
            context.assertTrue(occupied ? !restored.getStack(5).isEmpty() : restored.getStack(5).isEmpty(), "Keep a conflicting legacy module available for extraction");
            context.assertFalse(restored.isValid(5, new ItemStack(ModLaserParts.MODULES.get(LaserModule.BLOCK_DESTRUCTION))), "Old slot is extraction-only");
        }
        context.removeBlock(SOURCE); context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "module_budget", tickLimit = 20)
    public void realCombinedNetworkPaysMiningOnce(TestContext context) {
        BlockPos secondPos = MODULE.north(2);
        context.setBlockState(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        context.setBlockState(secondPos, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.SOUTH));
        context.setBlockState(MODULE, ModBlocks.BEAM_COMBINER.getDefaultState().with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.EAST));
        var mining = module(context, MODULE.east(), LaserModule.BLOCK_DESTRUCTION);
        var data = mining.createNbt(); data.putInt("MiningSpeed", 50); mining.readNbt(data);
        context.setBlockState(TARGET, ModBlocks.ENERGY_RECEIVER.getDefaultState().with(net.askcraft.justifylasers.block.LaserOpticBlock.FACING, Direction.WEST));
        context.runAtTick(4, () -> {
            double input = 0, output = 0;
            for (var pos : new BlockPos[]{SOURCE, secondPos}) {
                var source = (LaserEmitterBlockEntity) context.getBlockEntity(pos);
                var path = LaserBeamNetwork.path(source, 1);
                context.assertTrue(path.last().hitBlock().equals(context.getAbsolutePos(TARGET)), "Funded combined beam reaches the receiver");
                input += source.luminousFlux() * .95;
                output += source.luminousFlux() * path.last().power();
            }
            context.assertTrue(Math.abs(input - output - mining.operatingFlux()) < .001, "One mining charge for the whole incident beam, not one per source");
            context.removeBlock(SOURCE); context.removeBlock(secondPos); context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeFluxControlPersistsAndPoweredEmittersRejectIt(TestContext context) {
        var powered = emitter(context);
        context.assertFalse(powered.allowsSetting(LaserEmitterScreenHandler.FLUX_BUTTON_MAX), "Survival cannot request creative optical power");
        context.setBlockState(SOURCE, ModBlocks.LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        var creative = (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
        var player = context.createMockSurvivalPlayer(); player.setPosition(Vec3d.ofCenter(creative.getPos()));
        var menu = new LaserEmitterScreenHandler(31, player.getInventory(), creative); player.currentScreenHandler = menu;
        context.assertTrue(new LaserSettingsPacket(31, LaserEmitterScreenHandler.FLUX_BUTTON_MAX).apply(player), "Creative flux slider uses validated screen packets");
        context.assertFalse(new LaserSettingsPacket(31, LaserEmitterScreenHandler.FLUX_BUTTON_MAX + 1).apply(player), "Reject out-of-range flux requests");
        LaserEmitterBlockEntity.serverTick(context.getWorld(), creative.getPos(), creative.getCachedState(), creative);
        context.assertTrue(creative.luminousFlux() == 1_000_000_000_000L && creative.opticalBudget() == creative.luminousFlux(), "Creative light is transferable LM");
        var restored = new LaserEmitterBlockEntity(creative.getPos(), creative.getCachedState()); restored.readNbt(creative.createNbt());
        context.assertTrue(restored.getPropertyDelegate().get(54) == CreativeFlux.STEPS, "Flux selection survives NBT");
        context.removeBlock(SOURCE); context.complete();
    }

    private static List<ItemEntity> loot(TestContext context) { return context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(TARGET)).expand(1), item -> true); }
    private static LaserPartBlockEntity module(TestContext context, BlockPos pos, LaserModule kind) {
        context.setBlockState(pos, ModLaserParts.DECORATIONS.get(kind.id()).getDefaultState());
        return (LaserPartBlockEntity) context.getBlockEntity(pos);
    }
    private static LaserEmitterBlockEntity emitter(TestContext context) {
        context.setBlockState(SOURCE, ModBlocks.POWERED_LASER_EMITTER.getDefaultState().with(LaserEmitterBlock.FACING, Direction.EAST));
        return (LaserEmitterBlockEntity) context.getBlockEntity(SOURCE);
    }
    private static void install(LaserEmitterBlockEntity emitter, LaserModule module) { emitter.setStack(module.slot(), new ItemStack(ModLaserParts.MODULES.get(module))); }
    private static LaserBeamPath trace(Source source) { return LaserBeamPath.trace(source.world, source, 1, new HashMap<>(), new HashMap<>(), new HashMap<>()); }
    private static final class Source implements LaserBeamSource {
        final World world; final BlockPos pos; final BeamBehavior behavior;
        long flux = 2_000_000;
        int range = 16;
        Vec3d axis = new Vec3d(1, 0, 0);
        Source(TestContext context, BeamBehavior behavior) { world = context.getWorld(); pos = context.getAbsolutePos(SOURCE); this.behavior = behavior; }
        public World beamWorld() { return world; } public BlockPos beamPosition() { return pos; }
        public Vec3d beamOrigin() { return Vec3d.ofCenter(pos).add(axis.multiply(.499)); }
        public Vec3d beamDirection() { return axis; }
        public int beamRgb() { return 0x66DDFF; } public int getBeamRange() { return range; }
        public float getBeamWidthScale() { return 1; } public long getTicks() { return 1; }
        public boolean isBeamActive() { return true; } public boolean isLightEmissionEnabled() { return true; }
        public boolean showsScorchMarks() { return true; } public long luminousFlux() { return flux; }
        public long opticalBudget() { return flux; } public BeamBehavior beamBehavior() { return behavior; }
    }
}
