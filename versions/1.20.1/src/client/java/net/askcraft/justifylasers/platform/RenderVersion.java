package net.askcraft.justifylasers.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class RenderVersion {
    public static final boolean SCREEN_RENDERS_BACKGROUND = false;

    public static BufferBuilder beginQuads(VertexFormat format) {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, format);
        return builder;
    }

    public static void endVertex(VertexConsumer consumer) {
        consumer.next();
    }

    public static VertexConsumer normal(VertexConsumer consumer, Matrix3f matrix, float x, float y, float z) {
        return consumer.normal(matrix, x, y, z);
    }

    public static void pushModelView(Matrix4f matrix) {
        var stack = RenderSystem.getModelViewStack();
        stack.push();
        stack.loadIdentity();
        stack.multiplyPositionMatrix(matrix);
        RenderSystem.applyModelViewMatrix();
    }

    public static void popModelView() {
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();
    }

    public static void screenBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        screen.renderBackground(context);
    }

    private RenderVersion() {
    }
}
