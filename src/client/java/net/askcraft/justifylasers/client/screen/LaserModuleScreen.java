package net.askcraft.justifylasers.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.JustifyLasers;
import net.askcraft.justifylasers.block.entity.LaserPartBlockEntity;
import net.askcraft.justifylasers.energy.LaserModule;
import net.askcraft.justifylasers.laser.LaserBeamEffects;
import net.askcraft.justifylasers.laser.LaserDamage;
import net.askcraft.justifylasers.laser.LaserEntityMode;
import net.askcraft.justifylasers.laser.LaserMining;
import net.askcraft.justifylasers.laser.LuminousFlux;
import net.askcraft.justifylasers.network.LaserSettingsPacket;
import net.askcraft.justifylasers.platform.ClientPlatform;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.askcraft.justifylasers.screen.LaserEmitterScreenHandler;
import net.askcraft.justifylasers.screen.LaserModuleScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

public final class LaserModuleScreen extends HandledScreen<LaserModuleScreenHandler> {
    private final List<ModuleSlider> sliders = new ArrayList<>();
    private boolean storage, types;
    private LaserModule displayed;
    private EntityFilterPanel entityTypes;
    private SpectrumPanel spectrum;

    public LaserModuleScreen(LaserModuleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title); backgroundWidth = 320; backgroundHeight = 234;
    }
    @Override protected void init() {
        sliders.forEach(ModuleSlider::submit); sliders.clear(); entityTypes = null; spectrum = null;
        super.init();
        displayed = handler.module();
        handler.showStorage(storage);
        button(8, 31, 41, 28, "powered.back", () -> { if (storage || types) { storage = types = false; clearAndInit(); } else close(); });
        addDrawableChild(new TechTextButton(x + 8, y + 64, 41, 28,
                () -> Text.translatable("gui.justifylasers." + (handler.value(1) != 0 ? "on" : "off")), () -> handler.value(1) != 0, () -> true,
                () -> send(LaserEmitterScreenHandler.BUTTON_ENABLED)));
        if (handler.hasStorage())
            button(8, 97, 41, 28, "powered.storage", () -> { storage = !storage; types = false; clearAndInit(); });
        if (storage || displayed == null) return;
        if (types) {
            entityTypes = new EntityFilterPanel(x + 64, y + 44, widget -> addDrawableChild(widget),
                    () -> client.world != null && client.world.getBlockEntity(handler.pos()) instanceof LaserPartBlockEntity block ? block.filter() : null,
                    id -> ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.BUTTON_FILTER_TYPE, id)),
                    () -> send(LaserEmitterScreenHandler.BUTTON_FILTER_MODE));
        } else if (displayed == LaserModule.SPECTRUM) {
            spectrum = new SpectrumPanel(x + 64, y + 48, () -> (handler.value(11) & 0xFFFF) | (handler.value(12) & 255) << 16,
                    widget -> addDrawableChild(widget), value -> ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, LaserEmitterScreenHandler.BUTTON_SPECTRUM, value)));
        } else if (displayed == LaserModule.TARGET_FILTER) {
            String[] labels = {"hostile", "passive", "players", "exclude_owner"};
            for (int i = 0; i < 4; i++) {
                int flag = i;
                addDrawableChild(new TechTextButton(x + 64 + i % 2 * 98, y + 48 + i / 2 * 22, 92, 18,
                        () -> text("powered." + labels[flag]), () -> (handler.value(7) & 1 << flag) != 0, () -> true,
                        () -> send(LaserEmitterScreenHandler.FILTER_BUTTON_BASE + flag)));
            }
            button(64, 96, 190, 18, "filter.mob_list", () -> { types = true; clearAndInit(); });
        } else if (displayed == LaserModule.BLOCK_DESTRUCTION) {
            slider(51, 5, 0, LaserMining.MAX_SPEED_STEP, LaserEmitterScreenHandler.MINING_SPEED_BUTTON_BASE,
                    step -> Text.translatable("gui.justifylasers.mining_speed", step + "%"));
        } else if (displayed.isEntityMode() && displayed != LaserModule.ENTITY_HEAL) {
            LaserEntityMode mode = LaserEntityMode.of(displayed);
            slider(49, 2, 1, mode.movesEntities() ? 50 : LaserDamage.MAX_DAMAGE_STEP, LaserEmitterScreenHandler.DAMAGE_BUTTON_BASE,
                    step -> Text.translatable(mode.movesEntities() ? "gui.justifylasers.movement_speed"
                                    : mode == LaserEntityMode.HEAL ? "gui.justifylasers.heal_per_hit" : "gui.justifylasers.damage_per_hit",
                            String.format(Locale.ROOT, "%.1f", mode.movesEntities() ? LaserBeamEffects.movementSpeed(step) * 20 : LaserDamage.damageForStep(step))));
            if (mode == LaserEntityMode.DAMAGE) slider(72, 3, 0, 100, LaserEmitterScreenHandler.KNOCKBACK_BUTTON_BASE,
                    step -> Text.translatable("gui.justifylasers.knockback", String.format(Locale.ROOT, "%.1fx", LaserDamage.knockbackForStep(step))));
            if (!mode.movesEntities()) slider(95, 4, 1, 20, LaserEmitterScreenHandler.HIT_RATE_BUTTON_BASE,
                    step -> Text.translatable("gui.justifylasers.hits_per_second", step));
        }
    }
    private void button(int left, int top, int width, int height, String key, Runnable action) {
        addDrawableChild(new TechTextButton(x + left, y + top, width, height, () -> text(key), () -> false, () -> true, action));
    }
    private void slider(int top, int property, int min, int max, int base, IntFunction<Text> label) {
        ModuleSlider slider = new ModuleSlider(x + 64, y + top, property, min, max, base, label);
        sliders.add(addDrawableChild(slider));
    }
    private static Text text(String key) { return Text.translatable("gui.justifylasers." + key); }
    private void send(int id) { ClientPlatform.sendSettings(new LaserSettingsPacket(handler.syncId, id)); }
    @Override protected void handledScreenTick() {
        super.handledScreenTick();
        if (displayed != handler.module()) { storage = types = false; clearAndInit(); }
        sliders.forEach(ModuleSlider::tick);
        if (spectrum != null) spectrum.tick();
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!RenderVersion.SCREEN_RENDERS_BACKGROUND) renderBackground(context);
        super.render(context, mouseX, mouseY, delta); drawMouseoverTooltip(context, mouseX, mouseY);
    }
    public void renderBackground(DrawContext context) { context.fill(0, 0, width, height, 0x28050810); }
    public void renderBackground(DrawContext context, int mx, int my, float delta) { renderBackground(context); drawBackground(context, delta, mx, my); }
    @Override protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.draw(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        context.drawTexture(JustifyLasers.id("textures/gui/powered_emitter.png"), x, y, backgroundWidth, backgroundHeight, 0, 0, 1, 1, 1, 1);
        RenderSystem.disableBlend();
        PoweredLaserEmitterScreen.panel(context, x + 56, y + 31, 208, 94, false);
        for (var slot : handler.slots) if (slot.isEnabled()) PoweredLaserEmitterScreen.slot(context, x + slot.x, y + slot.y, slot.id < 9 && slot.hasStack());
        if (entityTypes != null) entityTypes.render(context);
        if (spectrum != null) spectrum.render(context);
    }
    @Override protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        fit(context, title, 160, 14, 206, 0xD6EDF4, .95F);
        fit(context, text(storage ? "powered.storage" : types ? "filter.mob_list" : "world_module.settings"), 160, 35, 190, 0x5DDFEC, .8F);
        if (!types && !storage && displayed != null && spectrum == null && !displayed.isEntityMode() && displayed != LaserModule.TARGET_FILTER && displayed != LaserModule.BLOCK_DESTRUCTION) {
            fit(context, text("world_module." + displayed.id()), 160, 61, 184, 0xD6EDF4, .85F);
            fit(context, text("world_module.priority"), 160, 82, 184, 0x7EA7B6, .75F);
        }
        if (storage) fit(context, text("powered.overflow_hint"), 160, 115, 188, 0x7EA7B6, .65F);
        else if (!types) fit(context, Text.translatable("gui.justifylasers.world_module.cost", LuminousFlux.format(handler.cost())), 160, 116, 190, 0x5DDFEC, .7F);
        if (!storage && displayed == LaserModule.ENTITY_HEAL) fit(context, text("healing_rate"), 160, 70, 184, 0xD6EDF4, .85F);
        if (!storage && !types && displayed == LaserModule.BLOCK_DESTRUCTION) {
            String[] names = {"collect", "silk", "drops", "smelt"};
            for (int i = 0; i < names.length; i++) fit(context, text("powered." + names[i]), 74 + i * 46, 71, 42, 0xD6EDF4, .65F);
        } else if (!storage && displayed == LaserModule.ENTITY_DAMAGE) fit(context, text("powered.ignite"), 222, 71, 61, 0xD6EDF4, .7F);
    }
    private void fit(DrawContext context, Text label, int left, int top, int maxWidth, int color, float maxScale) {
        float scale = Math.min(maxScale, maxWidth / (float) Math.max(1, textRenderer.getWidth(label)));
        var matrices = context.getMatrices(); matrices.push(); matrices.translate(left, top, 0); matrices.scale(scale, scale, 1);
        context.drawCenteredTextWithShadow(textRenderer, label, 0, 0, color); matrices.pop();
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (spectrum != null && spectrum.keyPressed(key, scan, modifiers)) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE && (storage || types)) { storage = types = false; clearAndInit(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (spectrum != null && spectrum.drag(mx, my, button, dx, dy)) return true;
        if (button == 0 && getFocused() instanceof ModuleSlider slider && slider.dragging) return slider.mouseDragged(mx, my, button, dx, dy);
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (spectrum != null && spectrum.release(mx, my, button)) { setDragging(false); return true; }
        if (button == 0 && getFocused() instanceof ModuleSlider slider && slider.dragging) { slider.onRelease(mx, my); setDragging(false); return true; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public void removed() { sliders.forEach(ModuleSlider::submit); super.removed(); }

    private final class ModuleSlider extends SliderWidget {
        private final int property, min, max, base;
        private final IntFunction<Text> label;
        private boolean dragging;
        private int lastSent, pending = -1, countdown, acknowledgement;
        ModuleSlider(int left, int top, int property, int min, int max, int base, IntFunction<Text> label) {
            super(left, top, displayed == LaserModule.ENTITY_DAMAGE ? 128 : 190, 18, Text.empty(), (MathHelper.clamp(handler.value(property), min, max) - min) / (double) (max - min));
            this.property = property; this.min = min; this.max = max; this.base = base; this.label = label;
            lastSent = step(); updateMessage();
        }
        int step() { return MathHelper.clamp(min + (int) Math.round(value * (max - min)), min, max); }
        @Override protected void updateMessage() { if (label != null) setMessage(label.apply(step())); }
        @Override protected void applyValue() { updateMessage(); countdown = 2; }
        @Override public void onClick(double mx, double my) { dragging = true; super.onClick(mx, my); }
        @Override public void onRelease(double mx, double my) { super.onRelease(mx, my); dragging = false; submit(); }
        void submit() {
            countdown = 0;
            if (step() != lastSent) { pending = lastSent = step(); acknowledgement = 40; send(base + step()); }
        }
        void tick() {
            if (countdown > 0 && --countdown == 0) submit();
            if (dragging || countdown > 0) return;
            int current = MathHelper.clamp(handler.value(property), min, max);
            if (pending >= 0 && current != pending && --acknowledgement > 0) return;
            pending = -1; lastSent = current; value = (current - min) / (double) (max - min); updateMessage();
        }
        public void renderButton(DrawContext context, int mx, int my, float delta) { renderWidget(context, mx, my, delta); }
        public void renderWidget(DrawContext context, int mx, int my, float delta) {
            TechGui.slider(context, getX(), getY(), width, height, value, TechGui.State.of(active, dragging, isHovered(), isFocused(), false));
            fit(context, getMessage(), getX() + width / 2, getY() + 5, width - 12, 0xD6EDF4, .8F);
        }
    }
}
