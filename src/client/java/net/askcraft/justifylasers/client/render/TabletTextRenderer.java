package net.askcraft.justifylasers.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.askcraft.justifylasers.platform.RenderVersion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Screen glyphs keep the hand's perspective, but bypass shader-pack material lighting and temporal noise. */
public final class TabletTextRenderer {
    private record Label(Text text, int color, Matrix4f transform) { }
    private static final List<Label> LABELS = new ArrayList<>();
    private static final VertexConsumerProvider.Immediate BUFFER = RenderVersion.immediateBuffer();
    private static Matrix4f projection, modelView;
    private static boolean collecting;

    public static void beginFrame() { LABELS.clear(); collecting = false; }
    static void beginDisplay() {
        LABELS.clear(); collecting = true;
        projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    }
    static void endDisplay() { collecting = false; }
    static boolean queue(Text text, int color, Matrix4f transform) {
        if (!collecting) return false;
        LABELS.add(new Label(text, color, new Matrix4f(transform)));
        return true;
    }

    public static void render() {
        if (LABELS.isEmpty()) return;
        var client = MinecraftClient.getInstance();
        try {
            if (client.options.hudHidden || client.player == null || !client.options.getPerspective().isFirstPerson()) return;
            for (Label label : LABELS) {
                var clip = new Matrix4f(projection).mul(modelView).mul(label.transform);
                Vector4f origin = project(clip, 0, 0), right = project(clip, 1, 0), down = project(clip, 0, 1);
                if (origin == null || right == null || down == null) continue;
                var display = new Matrix4f().m00(right.x - origin.x).m01(right.y - origin.y)
                        .m10(down.x - origin.x).m11(down.y - origin.y).m30(origin.x).m31(origin.y);
                client.textRenderer.draw(label.text, 0, 0, label.color, false, display, BUFFER,
                        TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            }
            BUFFER.draw();
        } finally { LABELS.clear(); }
    }

    private static Vector4f project(Matrix4f transform, float x, float y) {
        Vector4f point = transform.transform(new Vector4f(x, y, 0, 1));
        if (point.w <= 0 || !Float.isFinite(point.w)) return null;
        point.div(point.w);
        var window = MinecraftClient.getInstance().getWindow();
        point.x = (point.x + 1) * .5F * window.getScaledWidth();
        point.y = (1 - point.y) * .5F * window.getScaledHeight();
        return point;
    }

    private TabletTextRenderer() { }
}
