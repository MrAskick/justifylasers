package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.laser.LaserTargetFilter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Registry-backed selection; no hard-coded vanilla mob list and no model rendering in the menu. */
final class EntityFilterPanel {
    private static final int ROWS = 3;
    private final int x, y;
    private final List<EntityType<?>> entries = new ArrayList<>();
    private List<EntityType<?>> visible = List.of();
    private final List<TechTextButton> rows = new ArrayList<>();
    private final Supplier<LaserTargetFilter> filter;
    private int page;

    EntityFilterPanel(int x, int y, Consumer<ClickableWidget> add, Supplier<LaserTargetFilter> filter, Consumer<String> toggle, Runnable mode) {
        this.x = x; this.y = y; this.filter = filter;
        var client = MinecraftClient.getInstance();
        for (var type : Registries.ENTITY_TYPE) {
            boolean living = type == EntityType.PLAYER || type.getSpawnGroup() != SpawnGroup.MISC;
            if (!living && client.world != null) {
                try { living = type.create(client.world) instanceof LivingEntity; }
                catch (RuntimeException ex) { JustifyLasers.LOGGER.debug("Cannot inspect optional entity type {}", Registries.ENTITY_TYPE.getId(type), ex); }
            }
            if (living) entries.add(type);
        }
        entries.sort(Comparator.comparing(type -> type.getName().getString().toLowerCase(Locale.ROOT)));
        TextFieldWidget search = new TextFieldWidget(client.textRenderer, x + 4, y + 3, 103, 11, text("search"));
        search.setDrawsBackground(false); search.setMaxLength(128); search.setPlaceholder(text("search"));
        search.setChangedListener(query -> {
            String term = query.toLowerCase(Locale.ROOT);
            visible = entries.stream().filter(type -> type.getName().getString().toLowerCase(Locale.ROOT).contains(term)
                    || Registries.ENTITY_TYPE.getId(type).toString().contains(term)).toList();
            page = 0;
        });
        add.accept(search);
        add.accept(new TechTextButton(x + 112, y, 78, 16, () -> text(current().whitelist() ? "whitelist" : "blacklist"), () -> current().whitelist(), () -> true, mode));
        for (int i = 0; i < ROWS; i++) {
            int row = i;
            var button = new TechTextButton(x, y + 19 + i * 16, 190, 15,
                    () -> entry(row) == null ? Text.empty() : Text.literal(selected(row) ? "[x] " : "[ ] ").append(entry(row).getName()),
                    () -> selected(row), () -> entry(row) != null,
                    () -> { if (entry(row) != null) toggle.accept(id(row).toString()); });
            rows.add(button); add.accept(button);
        }
        add.accept(new TechTextButton(x, y + 68, 26, 13, () -> Text.literal("<"), () -> false, () -> page > 0, () -> page--));
        add.accept(new TechTextButton(x + 164, y + 68, 26, 13, () -> Text.literal(">"), () -> false,
                () -> (page + 1) * ROWS < visible.size(), () -> page++));
        visible = List.copyOf(entries);
    }
    private LaserTargetFilter current() { LaserTargetFilter value = filter.get(); return value == null ? new LaserTargetFilter() : value; }
    private EntityType<?> entry(int row) { int index = page * ROWS + row; return index < visible.size() ? visible.get(index) : null; }
    private Identifier id(int row) { return Registries.ENTITY_TYPE.getId(entry(row)); }
    private boolean selected(int row) { return entry(row) != null && current().types().contains(id(row)); }
    void render(DrawContext context) {
        context.fill(x, y, x + 109, y + 16, 0x70041723);
        var renderer = MinecraftClient.getInstance().textRenderer;
        context.drawCenteredTextWithShadow(renderer, Text.literal((page + 1) + " / " + Math.max(1, (visible.size() + ROWS - 1) / ROWS)), x + 95, y + 70, 0x9CB4C3);
        for (int i = 0; i < rows.size(); i++) rows.get(i).setTooltip(entry(i) == null ? null : Tooltip.of(Text.literal(id(i).toString())));
    }
    private static Text text(String key) { return Text.translatable("gui.justifylasers.filter." + key); }
}
