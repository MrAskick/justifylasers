#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Start;
uniform vec3 Sun;
uniform vec3 Side;
uniform float Length;
uniform float Time;
uniform float ShaftIndex;
out vec4 vertexColor;
void main() {
    float t = Position.y, section = Position.z;
    float distance = Length * t * t;
    vec3 a = Start + Sun * (Length * section * section);
    float motion = 0.9 + 0.1 * sin(Time * 0.018 + ShaftIndex * 1.7 + section * 6.0);
    float nearFade = min(1.0, length(a) / 0.8);
    float alpha = floor(15.0 * motion * nearFade * pow(1.0 - t, 1.7));
    alpha = floor(alpha * UV0.x) / 255.0;
    vec3 p = Start + Sun * distance + Side * Position.x * (0.20 + min(3.2, distance * 0.014));
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    vertexColor = vec4(1.0, 243.0 / 255.0, 210.0 / 255.0, alpha);
}
