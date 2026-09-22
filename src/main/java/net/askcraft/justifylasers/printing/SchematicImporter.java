package net.askcraft.justifylasers.printing;

import net.askcraft.justifylasers.platform.GameVersion;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/** Sponge/WorldEdit v1–v3; the client supplies materials from its loaded block models. */
public final class SchematicImporter {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final int MAX_NBT = 64 * 1024 * 1024;

    public record SkippedBlock(String state, String reason, int count) { }
    public record ResolvedPalette(Map<String, VoxelGrid.Material> materials, List<SkippedBlock> skipped) {
        public ResolvedPalette { materials = Map.copyOf(materials); skipped = List.copyOf(skipped); }
    }

    public record Schematic(int width, int height, int length, List<String> palette, int[] blocks) {
        public ResolvedPalette resolveMaterials(Function<String, VoxelGrid.Material> resolver) {
            int[] counts = new int[palette.size()];
            for (int index : blocks) counts[index]++;
            var materials = new HashMap<String, VoxelGrid.Material>();
            var skipped = new ArrayList<SkippedBlock>();
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] == 0) continue;
                String state = palette.get(i);
                try {
                    var material = resolver.apply(state);
                    if (material != null) materials.put(state, material);
                } catch (PrintBlockState.ImportFailure unsupported) {
                    skipped.add(new SkippedBlock(state, unsupported.getMessage(), counts[i]));
                }
            }
            return new ResolvedPalette(materials, skipped);
        }

        public PrintDesign model(String name, int scale, Function<String, VoxelGrid.Material> resolver) {
            if (scale != 1 && scale != 16) throw PrintDesign.invalid("schematic_scale");
            if ((long) width * scale > PrintAssembly.MAX_AXIS || (long) height * scale > PrintAssembly.MAX_AXIS
                    || (long) length * scale > PrintAssembly.MAX_AXIS) throw PrintDesign.invalid("schematic_limits");
            var materials = new HashMap<Integer, VoxelGrid.Material>();
            var air = new java.util.HashSet<Integer>();
            var parts = new HashMap<BlockPos, VoxelGrid.Material[]>();
            for (int i = 0; i < blocks.length; i++) {
                int index = blocks[i];
                if (air.contains(index)) continue;
                if (!materials.containsKey(index)) {
                    var material = resolver.apply(palette.get(index));
                    if (material == null) { air.add(index); continue; }
                    materials.put(index, material.withModelSize(scale));
                }
                int x = i % width * scale, z = i / width % length * scale, y = i / (width * length) * scale;
                var pos = new BlockPos(x / 16, y / 16, z / 16);
                var cells = parts.computeIfAbsent(pos, key -> {
                    if (parts.size() >= PrintAssembly.MAX_PARTS) throw PrintDesign.invalid("schematic_limits");
                    return new VoxelGrid.Material[4096];
                });
                if (scale == 16) java.util.Arrays.fill(cells, materials.get(index));
                else cells[(x & 15) + 16 * ((z & 15) + 16 * (y & 15))] = materials.get(index);
            }
            if (parts.isEmpty()) throw PrintDesign.invalid("schematic_empty");
            // Keep the selected region's origin: empty margins are meaningful when joining parts.
            if (width * scale <= 16 && height * scale <= 16 && length * scale <= 16)
                return PrintDesign.voxels(name, parts.get(BlockPos.ORIGIN));
            return PrintDesign.assembly(name, PrintAssembly.of(new BlockPos(width * scale, height * scale, length * scale), parts));
        }
    }

    public static Schematic read(byte[] source) {
        if (source.length == 0 || source.length > MAX_BYTES) throw PrintDesign.invalid("schematic_size");
        try {
            byte[] bytes = source;
            if (source.length >= 2 && (source[0] & 255) == 31 && (source[1] & 255) == 139) {
                try (var gzip = new GZIPInputStream(new ByteArrayInputStream(source))) { bytes = gzip.readNBytes(MAX_NBT + 1); }
            }
            if (bytes.length > MAX_NBT) throw PrintDesign.invalid("schematic_size");
            var input = new DataInputStream(new ByteArrayInputStream(bytes));
            NbtCompound root = GameVersion.readBoundedNbt(input, MAX_NBT);
            if (root == null || input.available() != 0) throw PrintDesign.invalid("schematic_format");
            if (root.contains("Schematic", 10)) root = root.getCompound("Schematic");
            if (!root.contains("Version", 3)) {
                if (root.contains("Blocks", 7)) throw PrintDesign.invalid("schematic_legacy");
                throw PrintDesign.invalid("schematic_format");
            }
            int version = root.getInt("Version");
            if (version < 1 || version > 3) throw PrintDesign.invalid("schematic_version");
            int width = dimension(root, "Width"), height = dimension(root, "Height"), length = dimension(root, "Length");
            long volume = (long) width * height * length;
            if (volume > PrintAssembly.MAX_VOXELS) throw PrintDesign.invalid("schematic_limits");
            var container = version == 3 ? root.getCompound("Blocks") : root;
            if (!container.contains("Palette", 10) || !container.contains(version == 3 ? "Data" : "BlockData", 7))
                throw PrintDesign.invalid("schematic_format");
            var rawPalette = container.getCompound("Palette");
            if (rawPalette.getKeys().isEmpty() || rawPalette.getKeys().size() > 512) throw PrintDesign.invalid("schematic_limits");
            var palette = new ArrayList<String>();
            var indices = new HashMap<Integer, Integer>();
            for (String state : rawPalette.getKeys().stream().sorted().toList()) {
                if (!rawPalette.contains(state, 3)) throw PrintDesign.invalid("schematic_format");
                int id = rawPalette.getInt(state);
                if (id < 0 || indices.putIfAbsent(id, palette.size()) != null) throw PrintDesign.invalid("schematic_format");
                palette.add(PrintBlockState.canonical(state));
            }
            byte[] data = container.getByteArray(version == 3 ? "Data" : "BlockData");
            int[] blocks = new int[(int) volume];
            int offset = 0;
            for (int i = 0; i < blocks.length; i++) {
                int value = 0, shift = 0, next;
                do {
                    if (offset >= data.length || shift > 28) throw PrintDesign.invalid("schematic_format");
                    next = data[offset++] & 255;
                    if (shift == 28 && (next & 0xF8) != 0) throw PrintDesign.invalid("schematic_format");
                    value |= (next & 127) << shift;
                    shift += 7;
                } while ((next & 128) != 0);
                var compact = indices.get(value);
                if (compact == null) throw PrintDesign.invalid("schematic_format");
                blocks[i] = compact;
            }
            if (offset != data.length) throw PrintDesign.invalid("schematic_format");
            return new Schematic(width, height, length, List.copyOf(palette), blocks);
        } catch (IllegalArgumentException failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw PrintDesign.invalid("schematic_format"); }
    }

    private static int dimension(NbtCompound root, String key) {
        if (!root.contains(key, 2)) throw PrintDesign.invalid("schematic_format");
        int value = root.getShort(key) & 65535;
        if (value < 1 || value > PrintAssembly.MAX_AXIS) throw PrintDesign.invalid("schematic_limits");
        return value;
    }

    private SchematicImporter() { }
}
