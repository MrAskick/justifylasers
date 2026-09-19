#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 MainTransform;
out vec4 vertexColor;
void main() {
    gl_Position=MainTransform*vec4(Position,1.0);
    vertexColor=Color;
}
