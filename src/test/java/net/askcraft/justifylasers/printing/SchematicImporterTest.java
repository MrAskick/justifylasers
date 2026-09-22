package net.askcraft.justifylasers.printing;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SchematicImporterTest {
    private static NbtCompound schematic(int version, int width, int height, int length, byte[] data) {
        var root = new NbtCompound(); root.putInt("Version", version); root.putInt("DataVersion", 3465);
        root.putShort("Width", (short) width); root.putShort("Height", (short) height); root.putShort("Length", (short) length);
        var palette = new NbtCompound(); palette.putInt("minecraft:air", 0); palette.putInt("minecraft:oak_log[axis=x]", 1);
        palette.putInt("testmod:marble[polished=true]", 300);
        var container = version == 3 ? new NbtCompound() : root;
        container.put("Palette", palette); container.putByteArray(version == 3 ? "Data" : "BlockData", data);
        if (version == 3) { root.put("Blocks", container); var wrapper = new NbtCompound(); wrapper.put("Schematic", root); return wrapper; }
        return root;
    }
    private static byte[] file(NbtCompound root, boolean compressed) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(compressed ? new GZIPOutputStream(output) : output)) { NbtIo.write(root, data); }
        return output.toByteArray();
    }
    private static VoxelGrid.Material material(String state) {
        return state.equals("minecraft:air") ? null : new VoxelGrid.Material("minecraft:block/oak_log", 0xFFFFFF, state, 1);
    }

    @Test void spongeVersionsAndSparseVarintsPreserveXYZAndBlockProperties() throws Exception {
        for (int version = 1; version <= 3; version++) for (boolean gzip : new boolean[]{false, true}) {
            byte[] data = {0, 1, 0, 0, 0, 0, (byte) 172, 2, 0};
            var source = SchematicImporter.read(file(schematic(version, 2, 2, 2, data), gzip));
            var model = source.model("World", 1, SchematicImporterTest::material);
            assertEquals(2, model.occupiedVoxels());
            assertEquals("minecraft:oak_log[axis=x]", model.voxelCells()[1].blockState());
            assertEquals("testmod:marble[polished=true]", model.voxelCells()[16 + 256].blockState());
            assertEquals(model.json(), PrintDesign.parse(model.json()).json());
            assertEquals(2, model.elements().size());
        }
    }
    @Test void fullScaleMakesSeparatePartsWithoutChangingTexturesOrState() throws Exception {
        var source = SchematicImporter.read(file(schematic(3, 2, 1, 1, new byte[]{1, 1}), true));
        var miniature = source.model("Mini", 1, SchematicImporterTest::material);
        assertEquals(2, miniature.elements().size(), "Identical source blocks must not merge and stretch their UVs");
        var full = source.model("Full", 16, SchematicImporterTest::material);
        assertEquals(2, full.partCount()); assertEquals(8192, full.occupiedVoxels());
        assertEquals(new BlockPos(1, 0, 0), full.assembly().offset(1));
        assertEquals(16, full.part(1).elements().get(0).to().x);
        assertEquals(1, full.part(1).elements().size());
        assertEquals(full.json(), PrintDesign.parse(full.json()).json());
        assertEquals(full.part(1).json(), PrintDesign.parse(full.part(1).json()).json());
    }
    @Test void largeMiniatureSplitsWithoutCroppingItsEmptyMargins() throws Exception {
        byte[] data = new byte[33]; data[0] = 1; data[32] = 1;
        var source = SchematicImporter.read(file(schematic(2, 33, 1, 1, data), true));
        var model = source.model("Long", 1, SchematicImporterTest::material);
        assertEquals(2, model.partCount()); assertEquals(new BlockPos(2, 0, 0), model.assembly().offset(1));
        assertEquals(model.assembly().palette(), PrintDesign.parse(model.json()).assembly().palette());
        assertThrows(IllegalArgumentException.class, () -> source.model("", 16, SchematicImporterTest::material));
    }
    @Test void unsupportedStatesAreSkippedOnceAndCountedWithoutMovingSupportedBlocks() throws Exception {
        var source = SchematicImporter.read(file(schematic(3, 5, 1, 1, new byte[]{(byte)172, 2, 1, (byte)172, 2, 0, 1}), true));
        for (String reason : new String[]{"schematic_missing_block", "schematic_property", "schematic_model", "schematic_missing_texture"}) {
            var calls = new java.util.HashMap<String, Integer>();
            var resolved = source.resolveMaterials(state -> {
                calls.merge(state, 1, Integer::sum);
                if (state.startsWith("testmod:")) throw new PrintBlockState.ImportFailure(reason, state);
                return material(state);
            });
            assertEquals(3, calls.size());
            assertTrue(calls.values().stream().allMatch(count -> count == 1));
            assertEquals(java.util.List.of(new SchematicImporter.SkippedBlock("testmod:marble[polished=true]", reason, 2)), resolved.skipped());
            assertFalse(resolved.materials().containsKey("minecraft:air"));
            var miniature = source.model("Partial", 1, resolved.materials()::get);
            assertEquals(2, miniature.occupiedVoxels());
            assertNull(miniature.voxelCells()[0]); assertNull(miniature.voxelCells()[2]); assertNull(miniature.voxelCells()[3]);
            assertEquals("minecraft:oak_log[axis=x]", miniature.voxelCells()[1].blockState());
            assertEquals(miniature.voxelCells()[1], miniature.voxelCells()[4]);
            var full = source.model("Partial", 16, resolved.materials()::get);
            assertEquals(2, full.partCount());
            assertEquals(new BlockPos(1, 0, 0), full.assembly().offset(0));
            assertEquals(new BlockPos(4, 0, 0), full.assembly().offset(1));
            assertEquals(new BlockPos(80, 16, 16), full.assembly().size());
            assertFalse(full.json().contains("testmod:"));
            assertEquals(full.json(), PrintDesign.parse(full.json()).json());
        }
    }
    @Test void skippedWholePartsKeepTheirOffsetsAndUnusedStatesAreNotResolved() throws Exception {
        byte[] data = new byte[34]; data[0] = (byte)172; data[1] = 2; data[33] = 1;
        var source = SchematicImporter.read(file(schematic(2, 33, 1, 1, data), false));
        var resolved = source.resolveMaterials(state -> {
            if (state.startsWith("testmod:")) throw new PrintBlockState.ImportFailure("schematic_model", state);
            return material(state);
        });
        var model = source.model("Gap", 1, resolved.materials()::get);
        assertEquals(1, model.partCount());
        assertEquals(new BlockPos(2, 0, 0), model.assembly().offset(0));
        assertEquals(new BlockPos(33, 1, 1), model.assembly().size());
        var onlyLog = SchematicImporter.read(file(schematic(2, 1, 1, 1, new byte[]{1}), true));
        var valid = onlyLog.resolveMaterials(state -> {
            assertEquals("minecraft:oak_log[axis=x]", state);
            return material(state);
        });
        assertTrue(valid.skipped().isEmpty());
    }
    @Test void anEntirelyUnsupportedSchematicCannotProduceAnEmptyPrintAndOtherFailuresStillSurface() throws Exception {
        var source = SchematicImporter.read(file(schematic(3, 1, 1, 1, new byte[]{1}), true));
        var resolved = source.resolveMaterials(state -> { throw new PrintBlockState.ImportFailure("schematic_model", state); });
        assertEquals(1, resolved.skipped().get(0).count());
        assertEquals("schematic_empty", assertThrows(IllegalArgumentException.class,
                () -> source.model("", 1, resolved.materials()::get)).getMessage());
        var failure = new IllegalStateException("Unexpected resource failure");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> source.resolveMaterials(state -> { throw failure; })));
    }
    @Test void malformedDataNeverTurnsIntoAirOrAChangedBlock() throws Exception {
        for (byte[] data : new byte[][]{{}, {1, 1}, {2}, {(byte)128}, {(byte)255,(byte)255,(byte)255,(byte)255,15}, {(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,0}}) {
            byte[] bytes = file(schematic(3, 1, 1, 1, data), true);
            assertThrows(IllegalArgumentException.class, () -> SchematicImporter.read(bytes));
        }
        var bad = schematic(2, 1, 1, 1, new byte[]{1}); bad.getCompound("Palette").putInt("minecraft:stone", 1);
        byte[] duplicate = file(bad, false);
        assertThrows(IllegalArgumentException.class, () -> SchematicImporter.read(duplicate));
        assertThrows(IllegalArgumentException.class, () -> PrintBlockState.canonical("stone[axis=x,axis=y]"));
        assertThrows(IllegalArgumentException.class, () -> PrintBlockState.canonical("stone[axis=x]junk"));
        assertEquals("minecraft:oak_stairs[facing=east,half=top]", PrintBlockState.canonical("oak_stairs[half=top,facing=east]"));
    }
    @Test void fileDimensionVersionAndDecompressionLimitsAreEnforced() throws Exception {
        for (int version : new int[]{0, 4}) {
            byte[] data = file(schematic(version, 1, 1, 1, new byte[]{1}), true);
            assertThrows(IllegalArgumentException.class, () -> SchematicImporter.read(data));
        }
        for (int width : new int[]{0, 257, 65535}) {
            byte[] data = file(schematic(2, width, 1, 1, new byte[]{1}), true);
            assertThrows(IllegalArgumentException.class, () -> SchematicImporter.read(data));
        }
        byte[] valid = file(schematic(3, 1, 1, 1, new byte[]{1}), true);
        assertThrows(IllegalArgumentException.class, () -> SchematicImporter.read(Arrays.copyOf(valid, valid.length / 2)));
        var bomb = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bomb)) { byte[] chunk = new byte[1024 * 1024]; for (int i = 0; i < 65; i++) gzip.write(chunk); }
        assertThrows(IllegalArgumentException.class, () -> SchematicImporter.read(bomb.toByteArray()));
    }
    @Test void emptySchematicsAndIncompleteFullScaleCellsAreRejected() throws Exception {
        var source = SchematicImporter.read(file(schematic(2, 1, 1, 1, new byte[]{0}), true));
        assertThrows(IllegalArgumentException.class, () -> source.model("", 1, SchematicImporterTest::material));
        var cells = new VoxelGrid.Material[4096]; cells[0] = material("minecraft:oak_log[axis=x]").withModelSize(16);
        assertThrows(IllegalArgumentException.class, () -> PrintDesign.voxels("", cells));
        assertThrows(IllegalArgumentException.class, () -> PrintAssembly.of(new BlockPos(16,16,16), Map.of(BlockPos.ORIGIN, cells)));
    }
    @Test void entitiesAndInventoriesAreNeverCopiedIntoThePrint() throws Exception {
        var root = schematic(2, 1, 1, 1, new byte[]{1});
        var secret = new NbtCompound(); secret.putString("Command", "NOT_PRINTABLE"); secret.putString("Items", "NOT_PRINTABLE");
        root.put("BlockEntities", secret); root.put("Entities", secret.copy());
        var result = SchematicImporter.read(file(root, true)).model("", 1, SchematicImporterTest::material);
        assertFalse(result.json().contains("NOT_PRINTABLE"));
        assertFalse(result.json().contains("Command"));
    }
}
