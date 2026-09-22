package net.askcraft.justifylasers.printing;

import com.google.gson.JsonObject;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Placement coordinates are relative to the assembly's north-facing, bottom corner. */
public record PrintPart(String project, int index, int count, BlockPos offset, BlockPos size) {
    public PrintPart {
        if (project == null || !project.matches("[a-f0-9]{64}") || count < 1 || count > PrintAssembly.MAX_PARTS || index < 0 || index >= count)
            throw PrintDesign.invalid("assembly");
        for (Direction.Axis axis : Direction.Axis.values()) {
            int n = size.getComponentAlongAxis(axis), at = offset.getComponentAlongAxis(axis);
            if (n < 1 || n > 16 || at < 0 || at >= n) throw PrintDesign.invalid("assembly");
        }
        offset = offset.toImmutable(); size = size.toImmutable();
    }
    JsonObject json() {
        var root = new JsonObject(); root.addProperty("project", project); root.addProperty("index", index); root.addProperty("count", count);
        root.addProperty("x", offset.getX()); root.addProperty("y", offset.getY()); root.addProperty("z", offset.getZ());
        root.addProperty("sx", size.getX()); root.addProperty("sy", size.getY()); root.addProperty("sz", size.getZ()); return root;
    }
    static PrintPart parse(JsonObject root) {
        try { return new PrintPart(root.get("project").getAsString(), integer(root,"index"), integer(root,"count"),
                new BlockPos(integer(root,"x"),integer(root,"y"),integer(root,"z")), new BlockPos(integer(root,"sx"),integer(root,"sy"),integer(root,"sz"))); }
        catch (RuntimeException failure) { throw PrintDesign.invalid("assembly"); }
    }
    private static int integer(JsonObject root,String key) {
        double n=root.get(key).getAsDouble(); if(!Double.isFinite(n)||n!=Math.rint(n)||n<0||n>512)throw PrintDesign.invalid("assembly");return(int)n;
    }
    public static BlockPos rotate(BlockPos p, Direction facing) {
        return switch(facing) {
            case EAST -> new BlockPos(-p.getZ(),p.getY(),p.getX());
            case SOUTH -> new BlockPos(-p.getX(),p.getY(),-p.getZ());
            case WEST -> new BlockPos(p.getZ(),p.getY(),-p.getX());
            default -> p;
        };
    }
}
