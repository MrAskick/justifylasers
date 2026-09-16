package net.askcraft.justifylasers.client.compat;

import net.askcraft.justifylasers.industry.ChamberStructure;
import net.askcraft.justifylasers.industry.MachineKind;
import net.askcraft.justifylasers.industry.SolarStructure;
import net.askcraft.justifylasers.registry.ModIndustry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public record MultiblockConstruction(String id, int width, int layers, List<Cell> cells, ItemStack result) {
    public record Cell(BlockPos pos, Block block) { }

    public List<ItemStack> ingredients() {
        var counts = new LinkedHashMap<Block, Integer>();
        cells.forEach(cell -> counts.merge(cell.block, 1, Integer::sum));
        return counts.entrySet().stream().map(entry -> new ItemStack(entry.getKey(), entry.getValue())).toList();
    }

    public static List<MultiblockConstruction> all() {
        var recipes = new ArrayList<MultiblockConstruction>();
        for (var kind : MachineKind.values()) if (kind.multiblock()) {
            var block = ModIndustry.MACHINES.get(kind);
            recipes.add(new MultiblockConstruction(kind.id(), 2, 2, ChamberStructure.positions(BlockPos.ORIGIN).stream()
                    .map(pos -> new Cell(pos, block)).toList(), new ItemStack(block)));
        }
        var cells = new ArrayList<Cell>();
        SolarStructure.PARTS.forEach(cell -> cells.add(new Cell(cell.offset(), ModIndustry.COMPONENT_BLOCKS.get(cell.component()))));
        var output = ModIndustry.COMPONENT_BLOCKS.get("optical_resonator");
        recipes.add(new MultiblockConstruction("solar_concentrator", 3, 3, List.copyOf(cells), new ItemStack(output)));
        return List.copyOf(recipes);
    }
}
