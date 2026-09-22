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

    public static net.minecraft.client.render.VertexConsumerProvider.Immediate immediateBuffer() {
        return net.minecraft.client.render.VertexConsumerProvider.immediate(new net.minecraft.client.util.BufferAllocator(4096));
    }

    public static BufferBuilder beginQuads(VertexFormat format) {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, format);
    }

    public static net.minecraft.client.gl.VertexBuffer staticEntityMesh(java.util.function.Consumer<VertexConsumer> emit) {
        return staticMesh(net.minecraft.client.render.VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, emit);
    }

    public static net.minecraft.client.gl.VertexBuffer staticMesh(VertexFormat format, java.util.function.Consumer<VertexConsumer> emit) {
        try (var allocator = new net.minecraft.client.util.BufferAllocator(4096)) {
            var builder = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS, format);
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
    }

    public static void endVertex(VertexConsumer consumer) {
        // 1.21 closes a vertex when the next vertex starts or the buffer ends.
    }

    public static void positionColorVertex(BufferBuilder buffer, float x, float y, float z, int rgb, int alpha) {
        buffer.vertex(x, y, z);
        buffer.color(rgb | alpha << 24);
    }

    public static void entityVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                                    int argb, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        consumer.vertex(matrix.m00()*x + matrix.m10()*y + matrix.m20()*z + matrix.m30(),
                matrix.m01()*x + matrix.m11()*y + matrix.m21()*z + matrix.m31(),
                matrix.m02()*x + matrix.m12()*y + matrix.m22()*z + matrix.m32(),
                argb, u, v, overlay, light, nx, ny, nz);
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
