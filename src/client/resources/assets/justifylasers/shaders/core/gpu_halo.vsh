#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Start;
uniform vec3 End;
uniform vec3 Side;
uniform vec3 CapAxis;
uniform vec3 Surface;
uniform vec3 BeamColor;
uniform float Width;
uniform float Intensity;
uniform vec4 StartPlane;
uniform vec4 EndPlane;
uniform vec3 StartPush;
uniform vec3 EndPush;
out vec4 vertexColor;
void main() {
    bool core = Color.r > 0.0;
    float intensity = core ? 1.0 : Intensity;
    float alpha = clamp(floor(Color.a * 255.0 * (intensity * UV0.x) + 0.5), 0.0, 255.0) / 255.0;
    vec3 p = mix(Start, End, Position.y) + Surface + Width * (Side * Position.x + CapAxis * Position.z);
    vec4 plane = Position.y < 0.5 ? StartPlane : EndPlane;
    vec3 push = Position.y < 0.5 ? StartPush : EndPush;
    p += push * max(0.0, -dot(plane, vec4(p, 1.0)));
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    vertexColor = vec4(core ? vec3(1.0) : BeamColor, alpha);
}
