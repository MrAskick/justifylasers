#version 150

uniform sampler2D OpaqueDepth;
uniform sampler2D BeforeHandDepth;
uniform sampler2D AfterHandDepth;
uniform vec2 SourceSize;
uniform float RenderScale;

in vec2 texCoord;

out vec4 fragColor;

float mergedDepth(ivec2 pixel, ivec2 maximumPixel) {
    pixel = clamp(pixel, ivec2(0), maximumPixel);

    float opaqueDepth = texelFetch(OpaqueDepth, pixel, 0).r;
    float beforeHand = texelFetch(BeforeHandDepth, pixel, 0).r;
    float afterHand = texelFetch(AfterHandDepth, pixel, 0).r;

    // Only a depth change between the two snapshots belongs to the first-person hand/item.
    return abs(afterHand - beforeHand) > 0.000001 ? afterHand : opaqueDepth;
}

void main() {
    ivec2 scaledSize = max(ivec2(1), ivec2(ceil(SourceSize * RenderScale)));
    ivec2 centerPixel = ivec2(floor(texCoord * vec2(scaledSize)));
    ivec2 maximumPixel = scaledSize - ivec2(1);

    // A one-to-one resolve is important for beams grazing a surface: expanding nearby depth
    // creates a stippled mask as Kappa jitters the scene for temporal anti-aliasing.
    gl_FragDepth = mergedDepth(centerPixel, maximumPixel);
    fragColor = vec4(0.0);
}
