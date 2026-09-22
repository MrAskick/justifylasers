#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float SurfaceY;
uniform vec2 Size;
uniform vec3 PhaseA;
uniform vec2 PhaseB;
uniform vec3 BeamColor;
uniform vec3 FilamentColor;
uniform vec3 EdgeColor;
out vec4 vertexColor;

void main() {
    int kind = int(UV0.x + 0.5);
    int palette = int(Color.r * 255.0 + 0.5);
    float x = Position.x;
    float y = SurfaceY;
    float z = Position.z;
    float alpha = Color.a;
    if (kind == 0) {
        float a = sin(z * 1.85 + sin(x * 5.1 + PhaseA.x) * 1.7 + PhaseA.y);
        float b = sin(x * 8.3 - z * 0.78 + PhaseA.z);
        alpha = float(70 + int(14.0 * a * b)) / 255.0;
    } else if (kind == 1) {
        float center = x / 3.0 + sin(z * 0.87 + x * 1.9 + PhaseB.x) * 0.23
                + sin(z * 0.34 - x + PhaseA.x) * 0.12;
        x = clamp(center + Position.y, 0.0, Size.x);
    } else if (kind == 2) {
        z = clamp(z + sin(x * 3.8 + z * 2.1 + PhaseB.y) * 0.26 + Position.y, 0.0, Size.y);
    } else if (kind == 4) {
        y = Position.y;
    }
    vec3 rgb = palette == 0 ? BeamColor : palette == 1 ? FilamentColor
            : palette == 2 ? EdgeColor : vec3(244.0 / 255.0, 1.0, 1.0);
    vertexColor = vec4(rgb, alpha);
    gl_Position = ProjMat * ModelViewMat * vec4(x, y, z, 1.0);
}
