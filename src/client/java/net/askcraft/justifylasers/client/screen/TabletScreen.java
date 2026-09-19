package net.askcraft.justifylasers.client.screen;

import net.askcraft.justifylasers.client.render.TabletRenderer;
import net.askcraft.justifylasers.screen.TabletScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Input is projected onto the handheld display; no replacement container is drawn over the world. */
public final class TabletScreen extends Screen implements ScreenHandlerProvider<TabletScreenHandler> {
    private final TabletScreenHandler handler;
    private int firstVisible;
    private float yaw = -28, pitch = 18;
    private boolean rotating;
    private double previousX, previousY;
    public static final int PAGE_SIZE = 5, LIST_TOP = 78;
    private net.minecraft.client.gui.widget.TextFieldWidget search;
    private java.util.List<Integer> matches = java.util.List.of();
    private net.minecraft.client.option.Perspective previousPerspective;

    public TabletScreen(TabletScreenHandler handler, PlayerInventory inventory, Text title) { super(title); this.handler = handler; }
    @Override public TabletScreenHandler getScreenHandler() { return handler; }
    @Override public void close() { if (client.player != null) client.player.closeHandledScreen(); }
    public int firstVisible() { return firstVisible; }
    public java.util.List<Integer> matches() { return matches; }
    public String query() { return search == null ? "" : search.getText(); }
    public boolean searching() { return search != null && search.isFocused(); }
    public int searchCursor() { return search == null ? 0 : search.getCursor(); }
    public float previewYaw() { return yaw; }
    public float previewPitch() { return pitch; }
    @Override protected void init() {
        super.init();
        if (previousPerspective == null) previousPerspective = client.options.getPerspective();
        client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
        String previousQuery = query();
        search = new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.translatable("gui.justifylasers.tablet.search"));
        search.setMaxLength(80);
        search.setChangedListener(value -> filter());
        search.setText(previousQuery);
        filter();
    }
    @Override public void removed() {
        if (previousPerspective != null && client.options.getPerspective().isFirstPerson()) client.options.setPerspective(previousPerspective);
        super.removed();
    }
    @Override public boolean shouldPause() { return false; }
    public void renderBackground(DrawContext context) { }
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { }

    private void send(int id) { if (client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id); }
    private void page(int direction) { firstVisible = MathHelper.clamp(firstVisible + direction * PAGE_SIZE, 0, Math.max(0, (matches.size() - 1) / PAGE_SIZE * PAGE_SIZE)); }

    private void filter() {
        matches = java.util.stream.IntStream.range(0, handler.blueprints().size()).filter(index -> {
            var blueprint = handler.blueprints().get(index);
            var output = blueprint.output(client.world);
            return matchesQuery(query(), output.getName().getString(), net.minecraft.registry.Registries.ITEM.getId(output.getItem()).toString(), blueprint.recipe().toString());
        }).boxed().toList();
        firstVisible = 0;
        if (!matches.isEmpty() && !matches.contains(handler.selected())) send(matches.get(0));
    }

    public static boolean matchesQuery(String query, String... values) {
        String haystack = String.join(" ", values).toLowerCase(java.util.Locale.ROOT).replace('ё', 'е');
        return java.util.Arrays.stream(query.trim().toLowerCase(java.util.Locale.ROOT).replace('ё', 'е').split("\\s+"))
                .allMatch(haystack::contains);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        var point = TabletRenderer.pointer(mx, my);
        if (point == null || button != 0 || handler.charge() <= 0) return false;
        double px = point[0], py = point[1];
        search.setFocused(px >= 12 && px < 168 && py >= 48 && py < 72);
        if (search.isFocused()) { search.keyPressed(GLFW.GLFW_KEY_END, 0, 0); return true; }
        if (px >= 12 && px < 168 && py >= LIST_TOP && py < LIST_TOP + PAGE_SIZE * 31) {
            int row = (int)(py - LIST_TOP) / 31, index = firstVisible + row;
            if ((py - LIST_TOP) % 31 < 27 && index < matches.size()) send(matches.get(index));
            return true;
        }
        if (py >= 259 && py < 287) {
            if (px >= 12 && px < 86) page(-1);
            else if (px >= 94 && px < 168) page(1);
            else if (px >= 344 && px < 468 && !matches.isEmpty()) send(TabletScreenHandler.RECORD);
            return true;
        }
        if (px >= 180 && px < 468 && py >= 74 && py < 212) {
            rotating = true; previousX = px; previousY = py; return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        var point = TabletRenderer.pointer(mx, my);
        if (!rotating || point == null) return false;
        yaw = MathHelper.wrapDegrees(yaw + (float)(point[0] - previousX) * .8F);
        pitch = MathHelper.clamp(pitch + (float)(point[1] - previousY) * .7F, -85, 85);
        previousX = point[0]; previousY = point[1]; return true;
    }
    @Override public boolean mouseReleased(double mx, double my, int button) { rotating = false; return true; }
    public boolean mouseScrolled(double mx, double my, double amount) { if (handler.charge() > 0) page(amount > 0 ? -1 : 1); return true; }
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) { return mouseScrolled(mx, my, vertical); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (searching() && key == GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); return true; }
        if (searching() && handler.charge() > 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { search.setFocused(false); return true; }
            return search.keyPressed(key, scan, modifiers);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || client.options.inventoryKey.matchesKey(key, scan)) { close(); return true; }
        if (handler.charge() > 0) {
            if (key == GLFW.GLFW_KEY_F && hasControlDown()) { search.setFocused(true); return true; }
            if (key == GLFW.GLFW_KEY_ENTER && !matches.isEmpty()) { send(TabletScreenHandler.RECORD); return true; }
            if (!matches.isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                int next = MathHelper.clamp(matches.indexOf(handler.selected()) + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, matches.size() - 1);
                firstVisible = next / PAGE_SIZE * PAGE_SIZE; send(matches.get(next)); return true;
            }
        }
        return false;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return searching() && handler.charge() > 0 && search.charTyped(character, modifiers);
    }
}
