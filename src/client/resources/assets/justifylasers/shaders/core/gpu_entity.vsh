#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform int FogShape;
uniform int jl_ModelActive;
uniform mat4 jl_ModelMatrix;
uniform mat3 jl_NormalMatrix;
uniform vec4 jl_Tint;
uniform ivec2 jl_LightUV;
uniform ivec2 jl_OverlayUV;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    vec3 position = jl_ModelActive != 0 ? (jl_ModelMatrix * vec4(Position, 1.0)).xyz : Position;
    vec3 normal = jl_ModelActive != 0 ? jl_NormalMatrix * Normal : Normal;
    vec4 color = jl_ModelActive != 0 ? Color * jl_Tint : Color;
    ivec2 light = jl_ModelActive != 0 ? jl_LightUV : UV2;
    ivec2 overlay = jl_ModelActive != 0 ? jl_OverlayUV : UV1;
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color);
    lightMapColor = texelFetch(Sampler2, light / 16, 0);
    overlayColor = texelFetch(Sampler1, overlay, 0);
    texCoord0 = UV0;
}
