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
import org.joml.Vector3f;

public final class RenderVersion {
    public static final boolean SCREEN_RENDERS_BACKGROUND = true;

    public static BufferBuilder beginQuads(VertexFormat format) {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, format);
    }

    public static void endVertex(VertexConsumer consumer) {
        // 1.21 closes a vertex when the next vertex starts or the buffer ends.
    }

    public static VertexConsumer normal(VertexConsumer consumer, Matrix3f matrix, float x, float y, float z) {
        Vector3f normal = matrix.transform(x, y, z, new Vector3f());
        return consumer.normal(normal.x, normal.y, normal.z);
    }

    public static void pushModelView(Matrix4f matrix) {
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(matrix);
        RenderSystem.applyModelViewMatrix();
    }

    public static void popModelView() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    public static void screenBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        screen.renderBackground(context, mouseX, mouseY, delta);
    }

    private RenderVersion() {
    }
}
