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

    public static net.minecraft.client.render.VertexConsumerProvider.Immediate immediateBuffer() {
        return net.minecraft.client.render.VertexConsumerProvider.immediate(new BufferBuilder(4096));
    }

    public static BufferBuilder beginQuads(VertexFormat format) {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.QUADS, format);
        return builder;
    }

    public static net.minecraft.client.gl.VertexBuffer staticEntityMesh(java.util.function.Consumer<VertexConsumer> emit) {
        return staticMesh(net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, emit);
    }

    public static net.minecraft.client.gl.VertexBuffer staticMesh(VertexFormat format, java.util.function.Consumer<VertexConsumer> emit) {
        var builder = new BufferBuilder(4096);
        builder.begin(VertexFormat.DrawMode.QUADS, format);
        emit.accept(builder);
        var gpu = new net.minecraft.client.gl.VertexBuffer(net.minecraft.client.gl.VertexBuffer.Usage.STATIC);
        try {
            gpu.bind();
            gpu.upload(builder.end());
            return gpu;
        } catch (RuntimeException failure) {
            gpu.close();
            throw failure;
        } finally { net.minecraft.client.gl.VertexBuffer.unbind(); }
    }

    public static void endVertex(VertexConsumer consumer) {
        consumer.next();
    }

    public static void positionColorVertex(BufferBuilder buffer, float x, float y, float z, int rgb, int alpha) {
        buffer.vertex(x, y, z);
        buffer.color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
        buffer.next();
    }

    public static void entityVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                                    int argb, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        consumer.vertex(matrix.m00()*x + matrix.m10()*y + matrix.m20()*z + matrix.m30(),
                matrix.m01()*x + matrix.m11()*y + matrix.m21()*z + matrix.m31(),
                matrix.m02()*x + matrix.m12()*y + matrix.m22()*z + matrix.m32(),
                (argb >> 16 & 255)/255F, (argb >> 8 & 255)/255F, (argb & 255)/255F, (argb >>> 24)/255F,
                u, v, overlay, light, nx, ny, nz);
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
