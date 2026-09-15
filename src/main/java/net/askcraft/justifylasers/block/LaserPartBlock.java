package net.askcraft.justifylasers.block;

import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.laser.LaserBeamNetwork;
import net.askcraft.justifylasers.laser.LaserColor;
import net.askcraft.justifylasers.laser.OpticalGeometry;
import net.askcraft.justifylasers.platform.LaserBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

public final class LaserPartBlock extends LaserBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final DirectionProperty MOUNT = DirectionProperty.of("mount");
    public static final BooleanProperty MIXING = BooleanProperty.of("mixing");
    private final String partId;
    private final Map<Direction, Map<Direction, VoxelShape>> mountedShapes = new EnumMap<>(Direction.class);

    public LaserPartBlock(Settings settings, String partId) {
        super(settings, value -> new LaserPartBlock(value, partId));
        this.partId = partId;
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH)
                .with(MOUNT, Direction.UP).with(MIXING, false));
        VoxelShape north = shape(partId);
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            VoxelShape[] result = {VoxelShapes.empty()};
            north.forEachBox((x1, y1, z1, x2, y2, z2) -> {
                VoxelShape box = switch (direction) {
                    case EAST -> VoxelShapes.cuboid(1 - z2, y1, x1, 1 - z1, y2, x2);
                    case SOUTH -> VoxelShapes.cuboid(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                    case WEST -> VoxelShapes.cuboid(z1, y1, 1 - x2, z2, y2, 1 - x1);
                    default -> VoxelShapes.cuboid(x1, y1, z1, x2, y2, z2);
                };
                result[0] = VoxelShapes.union(result[0], box);
            });
            shapes.put(direction, result[0]);
        }
        for (Direction mount : Direction.values()) {
            Map<Direction, VoxelShape> rotated = new EnumMap<>(Direction.class);
            shapes.forEach((facing, shape) -> {
                VoxelShape[] result = {VoxelShapes.empty()};
                shape.forEachBox((x1, y1, z1, x2, y2, z2) -> result[0] = VoxelShapes.union(result[0],
                        VoxelShapes.cuboid(new Box(OpticalGeometry.mounted(new Vec3d(x1, y1, z1), mount),
                                OpticalGeometry.mounted(new Vec3d(x2, y2, z2), mount)))));
                rotated.put(facing, result[0]);
            });
            mountedShapes.put(mount, rotated);
        }
    }

    public String partId() {
        return partId;
    }

    public boolean isCrystal() {
        return partId.endsWith("_crystal");
    }

    public LaserColor crystalColor() {
        for (LaserColor color : LaserColor.values()) {
            if (partId.equals(color.asString() + "_crystal")) return color;
        }
        throw new IllegalStateException("Not a crystal: " + partId);
    }

    @Override
    public String getTranslationKey() {
        return "item.justifylasers." + partId;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT, MIXING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite())
                .with(MOUNT, isCrystal() ? context.getSide() : Direction.UP);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING))).with(MOUNT, rotation.rotate(state.get(MOUNT)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(FACING, mirror.getRotation(state.get(FACING)).rotate(state.get(FACING)))
                .with(MOUNT, mirror.getRotation(state.get(MOUNT)).rotate(state.get(MOUNT)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return mountedShapes.get(state.get(MOUNT)).get(state.get(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getOutlineShape(state, world, pos, context);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new LaserPartBlockEntity(pos, state);
    }

    @Override
    protected ActionResult useLaser(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!isCrystal() || player.isSneaking()) return ActionResult.PASS;
        if (!world.isClient && player.canModifyBlocks()) {
            boolean mixing = !state.get(MIXING);
            world.setBlockState(pos, state.with(MIXING, mixing), Block.NOTIFY_ALL);
            LaserBeamNetwork.invalidate(world);
            player.sendMessage(Text.translatable("message.justifylasers.crystal_mixing",
                    Text.translatable(mixing ? "gui.justifylasers.on" : "gui.justifylasers.off")), true);
        }
        return ActionResult.SUCCESS;
    }

    private static VoxelShape shape(String id) {
        if (id.endsWith("_crystal") || id.equals("crystal_mount")) {
            return VoxelShapes.union(createCuboidShape(2.7, 0, 2.7, 13.3, 3.7, 13.3),
                    createCuboidShape(5, 3.7, 5, 11, 15.75, 11),
                    createCuboidShape(3.4, 3.7, 6.8, 12.6, 10.05, 9.2),
                    createCuboidShape(6.8, 3.7, 3.4, 9.2, 10.05, 12.6));
        }
        return switch (id) {
            case "control_circuit" -> createCuboidShape(1, 0, 1, 15, 3, 15);
            case "range_module" -> VoxelShapes.union(createCuboidShape(3, 0, 1, 13, 8, 15),
                    createCuboidShape(7, 8, 11, 9, 14, 13));
            case "scorch_marks_module" -> createCuboidShape(2.8, 0, 2.8, 13.2, 16, 13.2);
            case "ignition_module" -> createCuboidShape(2.4, 0, 2.4, 13.6, 16, 13.6);
            case "silk_touch_module" -> VoxelShapes.union(createCuboidShape(1, 0, 1, 15, 3, 15),
                    createCuboidShape(1, 13, 1, 15, 16, 15), createCuboidShape(1, 3, 1, 4, 13, 4),
                    createCuboidShape(12, 3, 1, 15, 13, 4), createCuboidShape(1, 3, 12, 4, 13, 15),
                    createCuboidShape(12, 3, 12, 15, 13, 15), createCuboidShape(5, 5, 5, 11, 11, 11));
            case "block_destruction_module" -> VoxelShapes.union(createCuboidShape(2, 0, 7, 14, 12, 16),
                    createCuboidShape(3, 1, 4, 13, 11, 7), createCuboidShape(5, 3, 1, 11, 9, 4),
                    createCuboidShape(7, 5, 0, 9, 7, 1));
            default -> createCuboidShape(1, 0, 1, 15, 14, 15);
        };
    }
}
