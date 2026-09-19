#version 150
uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform sampler2D WorldDepth;
uniform mat4 DepthToMain;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 uv=gl_FragCoord.xy/vec2(textureSize(Scene,0));
    if(gl_FragCoord.z>texture(WorldDepth,uv).r+0.000002) discard;
    float depth=texture(SceneDepth,uv).r;
    vec4 projected=DepthToMain*vec4(uv*2.0-1.0,depth*2.0-1.0,1.0);
    gl_FragDepth=depth>=0.999999 ? 1.0 : max(gl_FragCoord.z,clamp(projected.z/projected.w*.5+.5,0.0,1.0));
    fragColor=vec4(texture(Scene,uv).rgb,1.0)*vertexColor;
}
