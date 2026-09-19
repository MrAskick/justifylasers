package net.askcraft.justifylasers.item;

import net.askcraft.justifylasers.block.LaserEmitterBlock;
import net.askcraft.justifylasers.block.LaserOpticBlock;
import net.askcraft.justifylasers.block.LaserReceiverBlock;
import net.askcraft.justifylasers.block.entity.LaserEmitterBlockEntity;
import net.askcraft.justifylasers.block.entity.LaserOpticBlockEntity;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.OpticalGeometry;
import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class LaserConfiguratorItem extends Item implements net.askcraft.justifylasers.energy.RechargeableItem {
    public static final int MODE_COUNT = 6, CAPACITY = 20_000, USE_COST = 20;
    public LaserConfiguratorItem(Settings settings) { super(settings); }

    public static int mode(ItemStack stack) { return Math.floorMod(GameVersion.itemData(stack).getInt("Mode"), MODE_COUNT); }
    public static int color(int mode) {
        return switch (mode) {
            case 1 -> 0x639DFF;
            case 2 -> 0x66ED9C;
            case 3 -> 0xFFD264;
            case 4 -> 0xCB91FF;
            case 5 -> 0xFF9E58;
            default -> 0x36DDEB;
        };
    }
    public static void select(ItemStack stack, int mode) {
        if (!(stack.getItem() instanceof LaserConfiguratorItem) || mode < 0 || mode >= MODE_COUNT) return;
        var data = GameVersion.itemData(stack);
        data.putInt("Mode", mode);
        data.remove("Mirror");
        GameVersion.setItemData(stack, data);
    }

    @Override public int energyCapacity() { return CAPACITY; }
    @Override public int energyTransfer() { return 256; }
    @Override public int readEnergy(ItemStack stack) { return net.minecraft.util.math.MathHelper.clamp(GameVersion.itemData(stack).getInt("ConfiguratorEnergy"), 0, CAPACITY); }
    @Override public void writeEnergy(ItemStack stack, int amount) {
        var data = GameVersion.itemData(stack);
        data.putInt("ConfiguratorEnergy", net.minecraft.util.math.MathHelper.clamp(amount, 0, CAPACITY));
        GameVersion.setItemData(stack, data);
    }
    @Override public boolean isItemBarVisible(ItemStack stack) { return true; }
    @Override public int getItemBarStep(ItemStack stack) { return Math.round(readEnergy(stack) * 13F / CAPACITY); }
    @Override public int getItemBarColor(ItemStack stack) { return readEnergy(stack) >= USE_COST ? color(mode(stack)) : 0xD75D58; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && !player.isSpectator()) player.sendMessage(Text.translatable("message.justifylasers.configurator_help"), true);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (player == null || !player.isAlive() || player.isSpectator() || !player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos)) return ActionResult.FAIL;
        if (world.isClient) return ActionResult.SUCCESS;
        var entity = world.getBlockEntity(pos);
        if (entity instanceof LaserEmitterBlockEntity emitter && !emitter.canAccess(player)) return fail(player, "access_denied");
        if (entity instanceof net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity component && !component.canAccess(player))
            return fail(player, "access_denied");
        if (entity instanceof net.askcraft.justifylasers.block.entity.IndustrialMachineBlockEntity machine && !machine.canAccess(player))
            return fail(player, "access_denied");
        ItemStack stack = context.getStack();
        if (mode(stack) == 5) {
            if (!(entity instanceof net.askcraft.justifylasers.energy.LaserEnergyHost host)) return ActionResult.PASS;
            int amount = Math.min(CAPACITY - readEnergy(stack), host.energy().stored());
            if (amount > 0 && host.energy().consume(amount)) {
                writeEnergy(stack, readEnergy(stack) + amount);
                entity.markDirty(); player.getInventory().markDirty();
            }
            player.sendMessage(Text.translatable("message.justifylasers.configurator_charge", readEnergy(stack), CAPACITY), true);
            return ActionResult.SUCCESS;
        }
        if (!player.getAbilities().creativeMode && readEnergy(stack) < USE_COST) return fail(player, "configurator_empty");
        ActionResult result = configure(context);
        if (result == ActionResult.SUCCESS && !player.getAbilities().creativeMode) {
            writeEnergy(stack, readEnergy(stack) - USE_COST);
            player.getInventory().markDirty();
        }
        return result;
    }

    private ActionResult configure(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        var entity = world.getBlockEntity(pos);
        ItemStack stack = context.getStack();
        NbtCompound data = GameVersion.itemData(stack);
        switch (mode(stack)) {
            case 0 -> {
                if (entity instanceof net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity part) {
                    var solar = part instanceof net.askcraft.justifylasers.block.entity.SolarConcentratorBlockEntity small && small.small() && !small.formed()
                            ? small : part.controller();
                    if (solar != null && context.getSide().getAxis().isHorizontal()
                            && (solar.small() || pos.getY() == net.askcraft.justifylasers.industry.SolarStructure.origin(solar).getY())) {
                        if (!solar.canAccess(player)) return fail(player, "access_denied");
                        solar.selectOutput(context.getSide());
                        player.sendMessage(Text.translatable("message.justifylasers.solar_output", Text.translatable("direction.justifylasers." + context.getSide().getName())), true);
                        return ActionResult.SUCCESS;
                    }
                    if (solar != null) return ActionResult.PASS;
                }
                if (entity instanceof LaserOpticBlockEntity optic && optic.hasConfigurablePorts()) {
                    optic.cyclePort(context.getSide(), player);
                    return ActionResult.SUCCESS;
                }
                var state = world.getBlockState(pos);
                Direction direction = player.isSneaking() ? context.getSide().getOpposite() : context.getSide();
                if (state.getBlock() instanceof net.askcraft.justifylasers.block.LightBridgeBlock) {
                    var facing = net.askcraft.justifylasers.block.LightBridgeBlock.FACING;
                    if (state.get(facing) == direction) return ActionResult.PASS;
                    world.setBlockState(pos, state.with(facing, direction), Block.NOTIFY_ALL);
                    return ActionResult.SUCCESS;
                }
                if (state.getBlock() instanceof net.askcraft.justifylasers.block.LaserComponentBlock) {
                    if (!direction.getAxis().isHorizontal()) direction = state.get(net.askcraft.justifylasers.block.LaserComponentBlock.FACING).rotateYClockwise();
                    if (entity instanceof net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity part)
                        net.askcraft.justifylasers.industry.SolarStructure.dismantle(part);
                    world.setBlockState(pos, state.with(net.askcraft.justifylasers.block.LaserComponentBlock.FORMED, false)
                            .with(net.askcraft.justifylasers.block.LaserComponentBlock.FACING, direction), Block.NOTIFY_ALL);
                    net.askcraft.justifylasers.industry.SolarStructure.formNearby(world, pos);
                    return ActionResult.SUCCESS;
                }
                if (state.getBlock() instanceof LaserEmitterBlock || state.getBlock() instanceof LaserReceiverBlock
                        || state.getBlock() instanceof LaserOpticBlock) {
                    world.setBlockState(pos, state.with(LaserEmitterBlock.FACING, direction), Block.NOTIFY_ALL);
                    if (entity instanceof LaserOpticBlockEntity optic && optic.kind() == LaserOpticBlock.Kind.MIRROR) optic.aim(Vec3d.of(direction.getVector()));
                    LaserBeamNetwork.invalidate(world);
                } else return ActionResult.PASS;
            }
            case 1 -> {
                if (entity instanceof LaserEmitterBlockEntity emitter) data.put("Settings", emitter.copySettings());
                else if (entity instanceof LaserOpticBlockEntity optic && optic.hasConfigurablePorts()) data.put("Settings", optic.copySettings());
                else return ActionResult.PASS;
                data.putLong("Preview", pos.asLong());
                data.putString("Dimension", world.getRegistryKey().getValue().toString());
                GameVersion.setItemData(stack, data);
                player.sendMessage(Text.translatable("message.justifylasers.settings_copied"), true);
            }
            case 2 -> {
                var settings = data.getCompound("Settings");
                boolean pasted;
                if (entity instanceof LaserEmitterBlockEntity emitter) pasted = !settings.contains("OpticKind") && emitter.pasteSettings(player, settings);
                else if (entity instanceof LaserOpticBlockEntity optic && optic.hasConfigurablePorts()) pasted = optic.pasteSettings(settings);
                else return ActionResult.PASS;
                if (!pasted) return fail(player, "no_copied_settings");
                player.sendMessage(Text.translatable("message.justifylasers.settings_pasted"), true);
            }
            case 3 -> {
                if (!data.contains("Mirror") || player.isSneaking()) {
                    if (!(entity instanceof LaserOpticBlockEntity optic) || optic.kind() != LaserOpticBlock.Kind.MIRROR) return fail(player, "select_mirror");
                    data.putLong("Mirror", pos.asLong());
                    data.putString("Dimension", world.getRegistryKey().getValue().toString());
                    GameVersion.setItemData(stack, data);
                    player.sendMessage(Text.translatable("message.justifylasers.select_mirror_target"), true);
                } else {
                    BlockPos mirrorPos = BlockPos.fromLong(data.getLong("Mirror"));
                    if (!data.getString("Dimension").equals(world.getRegistryKey().getValue().toString())
                            || !world.isChunkLoaded(mirrorPos) || player.squaredDistanceTo(Vec3d.ofCenter(mirrorPos)) > 64 * 64
                            || !world.canPlayerModifyAt(player, mirrorPos)
                            || !(world.getBlockEntity(mirrorPos) instanceof LaserOpticBlockEntity mirror)
                            || mirror.kind() != LaserOpticBlock.Kind.MIRROR) return fail(player, "select_mirror");
                    if (!(entity instanceof LaserOpticBlockEntity target) || target.kind() != LaserOpticBlock.Kind.MIRROR
                            || mirrorPos.equals(pos) || mirrorPos.getSquaredDistance(pos) > 64 * 64) return fail(player, "select_mirror");
                    mirror.linkTo(pos);
                    data.remove("Mirror");
                    GameVersion.setItemData(stack, data);
                    player.sendMessage(Text.translatable("message.justifylasers.mirror_aimed"), true);
                }
            }
            case 4 -> {
                var state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof net.askcraft.justifylasers.block.LightBridgeBlock)) return ActionResult.PASS;
                var rotation = net.askcraft.justifylasers.block.LightBridgeBlock.ROTATION;
                var next = state.cycle(rotation);
                world.setBlockState(pos, next, Block.NOTIFY_ALL);
                player.sendMessage(Text.translatable("message.justifylasers.bridge_rotation", next.get(rotation) * 45), true);
            }
            default -> throw new IllegalStateException("Unexpected configurator mode");
        }
        return ActionResult.SUCCESS;
    }

    private static ActionResult fail(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable("message.justifylasers." + key), true);
        return ActionResult.FAIL;
    }
}
