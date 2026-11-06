// Shared fragment program: forward-lit, linear HDR output to colortex0.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/lighting.glsl"

uniform sampler2D gtexture;
uniform mat4 gbufferModelView;
uniform vec4 entityColor;
uniform float frameTimeCounter;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 normalView;
varying vec3 worldPos;
varying float blockId;

#ifdef WATER
// Two layers of moving noise give a wave height field; its gradient tilts the normal.
float waveHeight(vec2 p, float t) {
    float h = 0.0;
    h += valueNoise(vec3(p * 0.45 + vec2(t * 0.35, t * 0.22), t * 0.15)) * 0.6;
    h += valueNoise(vec3(p * 1.30 - vec2(t * 0.28, -t * 0.4), t * 0.3)) * 0.3;
    h += valueNoise(vec3(p * 3.10 + vec2(t * 0.5, t * 0.1), 0.0)) * 0.1;
    return h;
}
#endif

void main() {
    vec4 albedo = texture2D(gtexture, texcoord) * glcolor;
#ifdef ENTITIES
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif
    if (albedo.a < 0.1) discard;

    vec3 base = toLinear(albedo.rgb);
    vec2 lm = remapLightmap(lmcoord);
    vec3 n = normalize(normalView);
#ifdef NO_NORMAL_LIGHT
    n = normalize(upPosition);
#endif

    float foliage = (blockId == ID_PLANT || blockId == ID_LEAVES || blockId == ID_TALL_LOWER || blockId == ID_TALL_UPPER) ? 1.0 : 0.0;
    float emissive = 0.0;
    if (blockId == ID_EMISSIVE) emissive = smoothstep(0.30, 0.75, max(base.r, max(base.g, base.b)));

#ifdef WATER
    if (blockId == ID_WATER) {
        vec3 N = n;
#ifdef WATER_WAVES
        vec3 up = normalize(upPosition);
        if (dot(n, up) > 0.6) {
            float t = frameTimeCounter;
            float e = 0.12;
            float h0 = waveHeight(worldPos.xz, t);
            float hx = waveHeight(worldPos.xz + vec2(e, 0.0), t);
            float hz = waveHeight(worldPos.xz + vec2(0.0, e), t);
            vec3 worldN = normalize(vec3((h0 - hx) / e * 0.18, 1.0, (h0 - hz) / e * 0.18));
            N = normalize(mat3(gbufferModelView) * worldN);
        }
#endif
        vec3 V = normalize(-viewPos);
        float fresnel = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
        float mf = isEyeInWater == 0 ? modFogAmount(fogEnd, far) : 0.0;
        vec3 reflected = mix(toLinear(skyColor) * mix(0.15, 1.0, dayFactor()), toLinear(fogColor), max(mf, 0.25));
        reflected *= smoothstep(0.0, 0.6, lm.y) * 0.85 + 0.15;
        vec3 body = shade(base * 0.45, n, lm, viewPos, 0.0, 0.0);
        vec3 H = normalize(normalize(shadowLightPosition) + V);
        float spec = pow(max(dot(N, H), 0.0), 220.0) * 6.0 * sampleShadow(viewPos, n, 0.0) * (1.0 - mf) * lm.y;
        vec3 color = mix(body, reflected, fresnel) + directLightColor() * spec;
        float alpha = mix(0.62, 0.95, fresnel);
        /* DRAWBUFFERS:02 */
        gl_FragData[0] = vec4(color, alpha);
        gl_FragData[1] = vec4(lm.x, 0.0, 0.0, 1.0);
        return;
    }
#endif

    vec3 color = shade(base, n, lm, viewPos, emissive, foliage);
    /* DRAWBUFFERS:02 */
    gl_FragData[0] = vec4(color, albedo.a);
    gl_FragData[1] = vec4(max(lm.x, emissive), 0.0, 0.0, 1.0); // block light, for light scattering in the fog
}
