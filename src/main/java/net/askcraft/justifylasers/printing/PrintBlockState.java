package net.askcraft.justifylasers.printing;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.TreeMap;

/** Only block states are retained. Block-entity data and executable world content never enter a print. */
public final class PrintBlockState {
    public record Description(String id, Map<String, String> properties) { }

    public static Description describe(String text) {
        if (text == null || text.isEmpty() || text.length() > 1024) throw PrintDesign.invalid("schematic_state");
        int bracket = text.indexOf('[');
        String id = PrintDesign.textureId(bracket < 0 ? text : text.substring(0, bracket));
        var properties = new TreeMap<String, String>();
        if (bracket >= 0) {
            if (!text.endsWith("]")) throw PrintDesign.invalid("schematic_state");
            String body = text.substring(bracket + 1, text.length() - 1);
            if (!body.isEmpty()) for (String property : body.split(",", -1)) {
                String[] pair = property.split("=", -1);
                if (pair.length != 2 || !pair[0].matches("[a-z0-9_]+") || !pair[1].matches("[a-z0-9_.:-]+")
                        || properties.size() >= 32 || properties.putIfAbsent(pair[0], pair[1]) != null)
                    throw PrintDesign.invalid("schematic_state");
            }
        }
        return new Description(id, Map.copyOf(properties));
    }

    public static String canonical(String text) {
        var state = describe(text);
        if (state.properties.isEmpty()) return state.id;
        return state.id + "[" + new TreeMap<>(state.properties).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    public static BlockState resolve(String text) {
        var description = describe(text);
        String[] parts = description.id.split(":", 2);
        Identifier id = Identifier.of(parts[0], parts[1]);
        if (!Registries.BLOCK.containsId(id)) throw new ImportFailure("schematic_missing_block", description.id);
        var block = Registries.BLOCK.get(id);
        BlockState result = block.getDefaultState();
        for (var entry : description.properties.entrySet()) {
            var property = block.getStateManager().getProperty(entry.getKey());
            if (property == null) throw new ImportFailure("schematic_property", text);
            result = apply(result, property, entry.getValue(), text);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value, String source) {
        return state.with(property, property.parse(value).orElseThrow(() -> new ImportFailure("schematic_property", source)));
    }

    public static final class ImportFailure extends IllegalArgumentException {
        private final String detail;
        public ImportFailure(String reason, String detail) { super(reason); this.detail = detail; }
        public String detail() { return detail; }
    }

    private PrintBlockState() { }
}
