#version 150

uniform sampler2D Scene;
uniform int Textured;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // Scene already contains the shader pack's final exposure and tone mapping.
    fragColor = Textured != 0 ? vec4(texture(Scene, texCoord).rgb, 1.0) : vertexColor;
}
