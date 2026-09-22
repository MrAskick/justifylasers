package net.askcraft.justifylasers.block.entity;

import net.askcraft.justifylasers.platform.InventoryNbt;
import net.askcraft.justifylasers.platform.LaserBlockEntity;
import net.askcraft.justifylasers.printing.PrintData;
import net.askcraft.justifylasers.printing.PrintDesign;
import net.askcraft.justifylasers.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.EnumMap;

public final class PrintedModelBlockEntity extends LaserBlockEntity {
    private PrintDesign design;
    private final EnumMap<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    public PrintedModelBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.PRINTED_MODEL, pos, state); }
    public PrintDesign design() { return design; }
    public void setDesign(PrintDesign design) {
        this.design = design!=null&&design.assembly()!=null?null:design; shapes.clear(); markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
    }
    public VoxelShape shape(Direction facing) { return shapes.computeIfAbsent(facing, this::makeShape); }
    private VoxelShape makeShape(Direction facing) {
        if (design == null) return VoxelShapes.fullCube();
        VoxelShape result = VoxelShapes.empty();
        var cells=collisionCells();
        // A fixed lattice bounds VoxelShape complexity; arbitrary imported coordinates can otherwise
        // form millions of axis intersections even when the input JSON itself is small.
        for (int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            if (!cells.get(x+16*(z+16*y))) continue;
            int end=x+1; while(end<16 && cells.get(end+16*(z+16*y))) end++;
            result=VoxelShapes.combine(result,box(new Vec3d(x,y,z),new Vec3d(end,y+1,z+1),facing),BooleanBiFunction.OR);
            x=end-1;
        }
        return result.simplify();
    }
    private java.util.BitSet collisionCells() {
        var cells=new java.util.BitSet(4096);
        for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(design.occupied(x,y,z))cells.set(x+16*(z+16*y));
        for(var cube:design.elements())if(!cube.blockState().isEmpty()) {
            final java.util.List<net.minecraft.util.math.Box> boxes;
            try {
                var state=net.askcraft.justifylasers.printing.PrintBlockState.resolve(cube.blockState());
                boxes=state.getOutlineShape(net.minecraft.world.EmptyBlockView.INSTANCE,BlockPos.ORIGIN).getBoundingBoxes();
                if(boxes.isEmpty()||boxes.size()>128)continue;
            } catch(RuntimeException missingBlock) { continue; }
            for(int y=(int)cube.from().y;y<cube.to().y;y++)for(int z=(int)cube.from().z;z<cube.to().z;z++)for(int x=(int)cube.from().x;x<cube.to().x;x++)cells.clear(x+16*(z+16*y));
            var size=cube.to().subtract(cube.from());
            for(var box:boxes) {
                int x0=Math.max(0,(int)Math.floor(cube.from().x+box.minX*size.x)),x1=Math.min(16,(int)Math.ceil(cube.from().x+box.maxX*size.x));
                int y0=Math.max(0,(int)Math.floor(cube.from().y+box.minY*size.y)),y1=Math.min(16,(int)Math.ceil(cube.from().y+box.maxY*size.y));
                int z0=Math.max(0,(int)Math.floor(cube.from().z+box.minZ*size.z)),z1=Math.min(16,(int)Math.ceil(cube.from().z+box.maxZ*size.z));
                for(int y=y0;y<y1;y++)for(int z=z0;z<z1;z++)for(int x=x0;x<x1;x++)cells.set(x+16*(z+16*y));
            }
        }
        return cells;
    }
    private static VoxelShape box(Vec3d a, Vec3d b, Direction side) {
        a = orient(a, side); b = orient(b, side);
        return VoxelShapes.cuboid(Math.min(a.x,b.x)/16,Math.min(a.y,b.y)/16,Math.min(a.z,b.z)/16,
                Math.max(a.x,b.x)/16,Math.max(a.y,b.y)/16,Math.max(a.z,b.z)/16);
    }
    public static Vec3d orient(Vec3d point, Direction side) {
        return switch (side) {
            case EAST -> new Vec3d(16-point.z,point.y,point.x);
            case SOUTH -> new Vec3d(16-point.x,point.y,16-point.z);
            case WEST -> new Vec3d(point.z,point.y,16-point.x);
            default -> point;
        };
    }
    @Override protected void writeLaserNbt(NbtCompound nbt, InventoryNbt inventory) { if (design != null) PrintData.putText(nbt,PrintData.KEY, design.json()); }
    @Override protected void readLaserNbt(NbtCompound nbt, InventoryNbt inventory) { design = PrintData.read(PrintData.getText(nbt,PrintData.KEY,PrintDesign.MAX_DOCUMENT_JSON));if(design!=null&&design.assembly()!=null)design=null;shapes.clear(); }
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
}
