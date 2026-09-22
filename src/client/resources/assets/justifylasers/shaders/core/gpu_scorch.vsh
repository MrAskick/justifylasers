#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Origin;
uniform float MarkOpacity[64];
uniform float MarkHeat[64];
uniform int Hot;
out vec4 vertexColor;
void main() {
    int mark = int(UV0.x + 0.5);
    float heat = MarkHeat[mark];
    float opacity = MarkOpacity[mark];
    vec3 rgb = Color.rgb;
    if (Hot != 0) {
        rgb = floor(Color.rgb * 255.0 * vec3(0.3 + 0.7 * heat, heat * heat, heat * heat * heat)) / 255.0;
        opacity *= min(1.0, heat * 2.0);
    }
    float alpha = clamp(floor(Color.a * 255.0 * opacity + 0.5), 0.0, 255.0) / 255.0;
    vertexColor = vec4(rgb, alpha);
    gl_Position = ProjMat * ModelViewMat * vec4(Origin + Position, 1.0);
}
