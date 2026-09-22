package net.askcraft.justifylasers.client.render;

import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormats;

/** Keeps CPU fallbacks and cached draws in the same alpha-compositing order. */
final class LateEffectBatch implements AutoCloseable {
    private BufferBuilder buffer;
    BufferBuilder buffer() {
        if (buffer == null) buffer = RenderVersion.beginQuads(VertexFormats.POSITION_COLOR);
        return buffer;
    }
    void flush() {
        if (buffer == null) return;
        var built = buffer.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        buffer = null;
    }
    @Override public void close() { flush(); }
}
