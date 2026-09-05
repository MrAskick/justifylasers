package net.askcraft.justifylasers.client.render;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class LaserRenderLayers extends RenderLayer {
    private static final Identifier WHITE_TEXTURE = new Identifier("minecraft", "textures/misc/white.png");
    private static final Identifier EMISSION_TEXTURE = new Identifier("justifylasers", "textures/effect/beam.png");
    private static final RenderPhase.Texture WHITE_TEXTURE_PHASE = new RenderPhase.Texture(
            WHITE_TEXTURE,
            false,
            false
    );

    private static final RenderPhase.ShaderProgram POSITION_COLOR_PROGRAM =
            new RenderPhase.ShaderProgram(GameRenderer::getPositionColorProgram);

    private static final MultiPhaseParameters GLOW_PHASES = MultiPhaseParameters.builder()
            .program(EYES_PROGRAM)
            .texture(WHITE_TEXTURE_PHASE)
            .transparency(ADDITIVE_TRANSPARENCY)
            .depthTest(LEQUAL_DEPTH_TEST)
            .cull(DISABLE_CULLING)
            .writeMaskState(COLOR_MASK)
            .build(false);

    // Deferred reflections need opaque depth and material data. Flat LabPBR maps supply
    // texture emission without POM displacement; blending would corrupt packed G-buffer data.
    private static final MultiPhaseParameters SHADER_EMISSION_PHASES = MultiPhaseParameters.builder()
            .program(ENTITY_SOLID_PROGRAM)
            .texture(new RenderPhase.Texture(EMISSION_TEXTURE, false, false))
            .transparency(NO_TRANSPARENCY)
            .depthTest(LEQUAL_DEPTH_TEST)
            .cull(ENABLE_CULLING)
            .writeMaskState(ALL_MASK)
            .build(false);

    // The late pass uses resolved world/hand depth but must not overwrite it.
    private static final MultiPhaseParameters FINAL_HALO_PHASES = MultiPhaseParameters.builder()
            .program(POSITION_COLOR_PROGRAM)
            .transparency(TRANSLUCENT_TRANSPARENCY)
            .depthTest(LEQUAL_DEPTH_TEST)
            // Avoid z-fighting between Kappa's jittered depth and the unjittered beam surface.
            .layering(POLYGON_OFFSET_LAYERING)
            .cull(DISABLE_CULLING)
            .writeMaskState(COLOR_MASK)
            .build(false);

    public static final RenderLayer BEAM_GLOW = RenderLayer.of(
            "justifylasers_beam_glow",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS,
            8192,
            false,
            true,
            GLOW_PHASES
    );

    public static final RenderLayer FLARE_GLOW = RenderLayer.of(
            "justifylasers_flare_glow",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.TRIANGLES,
            8192,
            false,
            true,
            GLOW_PHASES
    );

    public static final RenderLayer SHADER_BEAM_HALO = RenderLayer.of(
            "justifylasers_shader_beam_halo",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            8192,
            false,
            true,
            FINAL_HALO_PHASES
    );

    public static final RenderLayer SHADER_EMISSION = RenderLayer.of(
            "justifylasers_shader_emission",
            VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS,
            2048,
            false,
            false,
            SHADER_EMISSION_PHASES
    );

    public static void initialize() {
        // Forces custom layers to be constructed during client initialization.
    }

    private LaserRenderLayers() {
        super(
                "justifylasers_unused",
                VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS,
                0,
                false,
                false,
                () -> {
                },
                () -> {
                }
        );
    }
}
