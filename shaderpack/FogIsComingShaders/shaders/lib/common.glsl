// ---------------------------------------------------------------- shared helpers
#ifndef SHADOW_DISTORT
#define SHADOW_DISTORT 0.85
#endif

#define ID_PLANT        10001.0
#define ID_LEAVES       10002.0
#define ID_WATER        10003.0
#define ID_EMISSIVE     10004.0
#define ID_TALL_LOWER   10005.0
#define ID_TALL_UPPER   10006.0

vec3 toLinear(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(2.2));
}

float luminance(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec2 remapLightmap(vec2 lm) {
    return clamp((lm - 0.03125) * 1.06667, 0.0, 1.0);
}

// Shadow map distortion: more resolution close to the player.
vec3 distortShadow(vec3 p) {
    float d = length(p.xy);
    p.xy /= d * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT);
    p.z *= 0.25;
    return p;
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), f.x),
                   mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), f.x),
                   mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        s += a * valueNoise(p);
        p = p * 2.07 + vec3(17.3, 5.1, 9.7);
        a *= 0.5;
    }
    return s / 0.875;
}

/*
 * How much of the Fog Is Coming fog is active, 0..1. The mod drives Minecraft's own fog distance,
 * which Oculus passes to shader packs as fogEnd: the closer the fog end, the thicker the fog.
 * Without the mod (or before the fog arrives) fogEnd is the render distance and this returns 0.
 */
float modFogAmount(float fogEnd, float far) {
    return clamp(1.0 - fogEnd / max(far * 0.9, 1.0), 0.0, 1.0);
}
