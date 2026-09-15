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
    private net.minecraft.client.option.Perspective previousPerspective;

    public TabletScreen(TabletScreenHandler handler, PlayerInventory inventory, Text title) { super(title); this.handler = handler; }
    @Override public TabletScreenHandler getScreenHandler() { return handler; }
    @Override public void close() { if (client.player != null) client.player.closeHandledScreen(); }
    public int firstVisible() { return firstVisible; }
    public float previewYaw() { return yaw; }
    public float previewPitch() { return pitch; }
    @Override protected void init() {
        super.init();
        if (previousPerspective == null) previousPerspective = client.options.getPerspective();
        client.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
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
    private void page(int direction) { firstVisible = MathHelper.clamp(firstVisible + direction * 6, 0, (handler.blueprints().size() - 1) / 6 * 6); }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        var point = TabletRenderer.pointer(mx, my);
        if (point == null || button != 0 || handler.charge() <= 0) return false;
        double px = point[0], py = point[1];
        if (px >= 12 && px < 168 && py >= 50 && py < 236) {
            int row = (int)(py - 50) / 31, index = firstVisible + row;
            if ((py - 50) % 31 < 27 && index < handler.blueprints().size()) send(index);
            return true;
        }
        if (py >= 259 && py < 287) {
            if (px >= 12 && px < 86) page(-1);
            else if (px >= 94 && px < 168) page(1);
            else if (px >= 344 && px < 468) send(TabletScreenHandler.RECORD);
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
        if (key == GLFW.GLFW_KEY_ESCAPE || client.options.inventoryKey.matchesKey(key, scan)) { close(); return true; }
        if (handler.charge() > 0) {
            if (key == GLFW.GLFW_KEY_ENTER) { send(TabletScreenHandler.RECORD); return true; }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
                int next = MathHelper.clamp(handler.selected() + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, handler.blueprints().size() - 1);
                firstVisible = next / 6 * 6; send(next); return true;
            }
        }
        return false;
    }
}
