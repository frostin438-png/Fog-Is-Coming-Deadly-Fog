// ---------------------------------------------------------------- forward lighting (linear HDR)

uniform sampler2DShadow shadowtex0;
uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 shadowLightPosition;
uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float fogEnd;
uniform float far;
uniform float rainStrength;
uniform float nightVision;
uniform int isEyeInWater;

float sunElevation() {
    return dot(normalize(sunPosition), normalize(upPosition));
}

float dayFactor() {
    return smoothstep(-0.08, 0.22, sunElevation());
}

vec3 directLightColor() {
    float h = sunElevation();
    vec3 sun = mix(vec3(1.0, 0.42, 0.16), vec3(1.0, 0.93, 0.82), smoothstep(0.0, 0.35, h)) * 2.6;
    vec3 moon = vec3(0.10, 0.14, 0.24) * 0.9;
    return mix(moon, sun, dayFactor());
}

float sampleShadow(vec3 viewPos, vec3 normalView, float foliage) {
#ifdef SHADOWS
    vec3 playerPos = (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
    float dist = length(playerPos);
    float fade = smoothstep(shadowDistance * 0.8, shadowDistance * 0.97, dist);
    if (fade >= 1.0) return 1.0;
    vec3 normalWorld = mat3(gbufferModelViewInverse) * normalView;
    playerPos += normalWorld * (0.03 + dist * 0.002) * (1.0 - foliage);
    vec4 sp = shadowProjection * (shadowModelView * vec4(playerPos, 1.0));
    sp.xyz = distortShadow(sp.xyz) * 0.5 + 0.5;
    sp.z -= 0.0003 + foliage * 0.0006;
    float texel = 1.0 / float(shadowMapResolution);
    float s;
#if SHADOW_FILTER == 0
    s = shadow2D(shadowtex0, sp.xyz).r;
#elif SHADOW_FILTER == 1
    s = 0.0;
    s += shadow2D(shadowtex0, sp.xyz + vec3(-0.5, -0.5, 0.0) * texel).r;
    s += shadow2D(shadowtex0, sp.xyz + vec3( 0.5, -0.5, 0.0) * texel).r;
    s += shadow2D(shadowtex0, sp.xyz + vec3(-0.5,  0.5, 0.0) * texel).r;
    s += shadow2D(shadowtex0, sp.xyz + vec3( 0.5,  0.5, 0.0) * texel).r;
    s *= 0.25;
#else
    s = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            s += shadow2D(shadowtex0, sp.xyz + vec3(float(x), float(y), 0.0) * texel * 1.25).r;
        }
    }
    s /= 9.0;
#endif
    return mix(s, 1.0, fade);
#else
    return 1.0;
#endif
}

/*
 * albedo: linear colour; lm: remapped lightmap (block, sky); emissive: 0..1; foliage: 0/1 (light passes through).
 * Thick Fog Is Coming fog hides the sun: direct light fades and the scene is lit by the grey, diffuse fog.
 */
vec3 shade(vec3 albedo, vec3 normalView, vec2 lm, vec3 viewPos, float emissive, float foliage) {
    float mf = isEyeInWater == 0 ? modFogAmount(fogEnd, far) : 0.0;
    float day = dayFactor();

    vec3 L = normalize(shadowLightPosition);
    float NdotL = dot(normalView, L);
    float diffuse = foliage > 0.5 ? 0.55 + 0.45 * abs(NdotL) : max(NdotL, 0.0);
    float shadow = (NdotL > 0.0 || foliage > 0.5) ? sampleShadow(viewPos, normalView, foliage) : 0.0;
    float skyAccess = smoothstep(0.05, 0.5, lm.y);
    vec3 direct = directLightColor() * diffuse * shadow * skyAccess
                * (1.0 - 0.85 * mf) * (1.0 - 0.75 * rainStrength);

    vec3 skyAmbient = toLinear(skyColor) * mix(0.10, 0.65, day) + vec3(0.004, 0.006, 0.012);
    vec3 fogAmbient = toLinear(fogColor) * 1.6 + vec3(0.003);
    vec3 ambient = mix(skyAmbient, fogAmbient, mf) * (lm.y * lm.y) * (0.75 + 0.25 * normalView.y * normalView.y);
    ambient += vec3(0.012, 0.013, 0.016) + vec3(0.25) * nightVision;

    float bl = lm.x;
    vec3 block = vec3(1.0, 0.58, 0.28) * (pow(bl, 3.2) * 1.3 + pow(bl, 9.0) * 0.8);

    return albedo * (direct + ambient + block) + albedo * emissive * 5.0;
}
