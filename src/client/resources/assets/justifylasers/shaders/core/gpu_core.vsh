#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Center;
uniform vec3 Right;
uniform vec3 Up;
uniform vec3 Forward;
uniform vec3 BeamColor;
uniform float Time;
out vec4 vertexColor;
void main() {
    int palette = int(Color.r * 255.0 + 0.5);
    vec3 p;
    if (palette < 2) {
        p = Right * Position.x + Up * Position.y + Forward * Position.z;
    } else {
        float angle = Position.x, phase = Position.y;
        float latitude = 0.48 * sin(angle * 2.0 + phase) + 0.26 * sin(angle + phase + Time * 0.012) + Position.z;
        float radius = 0.183 + 0.012 * sin(angle * 3.0 + phase + Time * 0.018) + UV0.x;
        p = vec3(cos(angle) * cos(latitude), sin(latitude), sin(angle) * cos(latitude)) * radius;
        float x = Time * 0.009 + 0.4, y = Time * 0.025;
        p.yz = vec2(p.y * cos(x) + p.z * sin(x), p.z * cos(x) - p.y * sin(x));
        p.xz = vec2(p.x * cos(y) + p.z * sin(y), p.z * cos(y) - p.x * sin(y));
    }
    vec3 rgb = palette == 1 ? vec3(1.0) : BeamColor;
    if (palette == 3) rgb += floor((vec3(1.0) - rgb) * 255.0 * 0.32) / 255.0;
    vertexColor = vec4(rgb, Color.a);
    gl_Position = ProjMat * ModelViewMat * vec4(Center + p, 1.0);
}
