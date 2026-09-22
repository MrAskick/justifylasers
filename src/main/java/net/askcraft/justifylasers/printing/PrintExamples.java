package net.askcraft.justifylasers.printing;

/** A small stepped pedestal, also useful for checking each stage of the printing pipeline. */
public final class PrintExamples {
    private static PrintDesign pedestal;
    public static PrintDesign pedestal() {
        if (pedestal == null) {
            String[] cells = new String[4096];
            for (int y=0; y<12; y++) for (int z=2; z<14; z++) for (int x=2; x<14; x++)
                if (y<2 || y>=10 || x>=6 && x<10 && z>=6 && z<10)
                    cells[x+16*(z+16*y)] = y>=10 ? "minecraft:block/polished_deepslate" : "minecraft:block/quartz_block_side";
            pedestal = PrintDesign.voxels("", cells);
        }
        return pedestal;
    }
    private PrintExamples() { }
}
