#version 150

uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
uniform vec2 Center;
uniform float Magnification;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    float radius = length(texCoord);
    if (radius > 1.0) discard;
    vec2 screen = gl_FragCoord.xy / Viewport;
    float edge = smoothstep(0.82, 1.0, radius);
    float zoom = mix(Magnification, 1.0, edge);
    vec2 sampleUv = Center + (screen - Center) / zoom;
    // Do not pull a foreground wall or held item into a lens behind it.
    if (texture(SceneDepth, sampleUv).r + 0.000001 < gl_FragCoord.z) sampleUv = screen;
    vec3 color = texture(Scene, clamp(sampleUv, vec2(0.0), vec2(1.0))).rgb;
    float glint = pow(max(0.0, dot(normalize(vec3(texCoord * 0.55, 1.0)), normalize(vec3(-0.42, 0.53, 1.0)))), 110.0);
    color = mix(color, vec3(0.83, 0.94, 1.0), 0.065 * glint * (1.0 - edge));
    fragColor = vec4(color, 1.0) * vertexColor;
}
