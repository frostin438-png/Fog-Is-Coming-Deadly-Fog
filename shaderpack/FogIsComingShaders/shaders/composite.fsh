#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

/*
 * Volumetric fog. With the Fog Is Coming mod the fog distance and colour come from the mod
 * (fogEnd / fogColor); the shader turns them into a height-shaped, drifting 3D fog volume.
 * Without the mod fog it only adds a light aerial haze. Underwater / in lava: dense coloured fog.
 */

/*
const int colortex0Format = RGBA16F;
const int colortex2Format = R8;
*/
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);

uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform vec3 fogColor;
uniform float fogEnd;
uniform float far;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform float blindness;
uniform int isEyeInWater;
uniform int frameCounter;
uniform ivec2 eyeBrightnessSmooth;

varying vec2 texcoord;

float ign(vec2 pixel) {
    pixel += mod(float(frameCounter), 64.0) * 5.588238;
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;
    vec2 ndc = texcoord * 2.0 - 1.0;

    vec4 farPoint = gbufferProjectionInverse * vec4(ndc, 1.0, 1.0);
    vec3 dirView = normalize(farPoint.xyz / farPoint.w);
    vec3 dir = mat3(gbufferModelViewInverse) * dirView;
    bool sky = depth >= 1.0;
    float dist;
    if (sky) {
        dist = far * 2.0;
    } else {
        vec4 vp = gbufferProjectionInverse * vec4(ndc, depth * 2.0 - 1.0, 1.0);
        dist = length(vp.xyz / vp.w);
    }

    vec3 fogLin = toLinear(fogColor);

    if (isEyeInWater == 1) {
        // Underwater: short, blue-green visibility.
        float t = exp(-dist * 0.075);
        color = mix(fogLin * 0.9, color, t);
    } else if (isEyeInWater == 2) {
        float t = exp(-dist * 1.2);
        color = mix(vec3(1.0, 0.25, 0.04), color, t);
    } else {
        float mf = modFogAmount(fogEnd, far);
#ifdef VOLUMETRIC_FOG
        if (mf > 0.001) {
            float k = 3.0 / max(fogEnd, 4.0);
            float deadly = smoothstep(0.72, 0.88, mf);
            float heightFalloff = mix(22.0, 70.0, deadly);
            float noiseAmount = mix(0.7, 0.95, deadly);
            float indoor = mix(0.3, 1.0, clamp(float(eyeBrightnessSmooth.y) / 200.0, 0.0, 1.0));
            float marchMax = min(fogEnd * 1.6, far);
            float rayLength = min(dist, marchMax);
            float stepLength = rayLength / float(FOG_QUALITY);
            float jitter = ign(gl_FragCoord.xy);
            vec3 origin = mod(cameraPosition, vec3(4096.0));
            vec3 wind = vec3(0.9, 0.0, 0.45) * (0.8 + 0.6 * deadly);
            float optical = 0.0;
            for (int i = 0; i < FOG_QUALITY; i++) {
                float t = (float(i) + jitter) * stepLength;
                vec3 rel = dir * t;
                float height = exp(-max(0.0, rel.y - 3.0) / heightFalloff);
                vec3 p = (origin + rel) * 0.045 - wind * 0.045 * frameTimeCounter;
                p.y += frameTimeCounter * 0.015;
                float n = fbm3(p + vec3(0.0, 0.0, frameTimeCounter * 0.02));
                float clumps = mix(1.0, smoothstep(0.18, 0.82, n) * 2.0, noiseAmount);
                float near = mix(indoor, 1.0, smoothstep(3.0, 12.0, t));
                optical += k * height * clumps * near * stepLength;
            }
            if (dist >= marchMax) optical += k * 0.5 * marchMax;
            float transmittance = exp(-optical);
            // The sky (sun, moon, stars) disappears completely as the fog thickens.
            if (sky) transmittance *= 1.0 - smoothstep(0.25, 0.75, mf);
            // Light scattering: fog in front of surfaces lit by torches, lanterns and fires glows warm.
            float blockLight = sky ? 0.0 : texture2D(colortex2, texcoord).r;
            vec3 scatter = vec3(1.0, 0.56, 0.26) * (pow(blockLight, 2.5) * 0.30 + pow(blockLight, 8.0) * 0.45);
            color = mix(fogLin + min(scatter, vec3(0.6)) * mf, color, transmittance);
        }
#else
        if (mf > 0.001) {
            float k = 3.0 / max(fogEnd, 4.0);
            color = mix(fogLin, color, exp(-k * min(dist, far)));
        }
#endif
        // Light aerial haze in the normal world (stronger in rain).
        if (!sky) {
            float haze = 1.0 - exp(-dist * (0.0018 + 0.006 * rainStrength));
            color = mix(color, fogLin, haze * (1.0 - mf));
        }
    }

    if (blindness > 0.0) color *= 1.0 - blindness * clamp(dist / 6.0, 0.0, 1.0);

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, 1.0);
}
