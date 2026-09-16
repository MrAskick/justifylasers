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

public final class LaserConfiguratorItem extends Item {
    public LaserConfiguratorItem(Settings settings) { super(settings); }

    public static int mode(ItemStack stack) { return Math.floorMod(GameVersion.itemData(stack).getInt("Mode"), 4); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && !player.isSpectator()) {
            NbtCompound data = GameVersion.itemData(stack);
            int mode = (mode(stack) + 1) % 4;
            data.putInt("Mode", mode);
            data.remove("Mirror");
            GameVersion.setItemData(stack, data);
            player.sendMessage(Text.translatable("message.justifylasers.configurator_mode", Text.translatable("item.justifylasers.configurator.mode." + mode)), true);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (player == null || !player.isAlive() || !player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos)) return ActionResult.FAIL;
        if (world.isClient) return ActionResult.SUCCESS;
        var entity = world.getBlockEntity(pos);
        if (entity instanceof LaserEmitterBlockEntity emitter && !emitter.canAccess(player)) return fail(player, "access_denied");
        if (entity instanceof net.askcraft.justifylasers.block.entity.LaserComponentBlockEntity component && !component.canAccess(player))
            return fail(player, "access_denied");
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
                if (!(entity instanceof LaserEmitterBlockEntity emitter)) return ActionResult.PASS;
                data.put("Settings", emitter.copySettings());
                data.putLong("Preview", pos.asLong());
                data.putString("Dimension", world.getRegistryKey().getValue().toString());
                GameVersion.setItemData(stack, data);
                player.sendMessage(Text.translatable("message.justifylasers.settings_copied"), true);
            }
            case 2 -> {
                if (!(entity instanceof LaserEmitterBlockEntity emitter)) return ActionResult.PASS;
                if (!emitter.pasteSettings(player, data.getCompound("Settings"))) return fail(player, "no_copied_settings");
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
                    Vec3d center = Vec3d.ofCenter(mirrorPos);
                    var incoming = LaserBeamNetwork.paths(world, 1).values().stream().flatMap(path -> path.segments().stream())
                            .filter(ray -> ray.end().squaredDistanceTo(center) <= 0.361 * 0.361).findFirst();
                    if (incoming.isEmpty()) return fail(player, "mirror_needs_beam");
                    Vec3d normal = OpticalGeometry.aimMirror(center, incoming.get().start(), incoming.get().axis(), context.getHitPos(), 0.36);
                    if (normal == null) return fail(player, "mirror_straight");
                    mirror.aim(normal);
                    data.remove("Mirror");
                    GameVersion.setItemData(stack, data);
                    player.sendMessage(Text.translatable("message.justifylasers.mirror_aimed"), true);
                }
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
