#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

// Procedural sky: zenith-to-horizon gradient, warm glow around the sun, stars at night.

uniform mat4 gbufferProjectionInverse;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform float viewWidth;
uniform float viewHeight;
uniform float rainStrength;
uniform int renderStage;

varying vec4 glcolor;

void main() {
#ifdef MC_RENDER_STAGE_STARS
    if (renderStage == MC_RENDER_STAGE_STARS) {
        /* DRAWBUFFERS:0 */
        gl_FragData[0] = vec4(toLinear(glcolor.rgb) * 3.0, glcolor.a);
        return;
    }
#endif
    vec4 p = gbufferProjectionInverse * vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    vec3 dir = normalize(p.xyz / p.w);
    vec3 up = normalize(upPosition);
    vec3 sunDir = normalize(sunPosition);
    float upDot = dot(dir, up);
    float sunH = dot(sunDir, up);
    float day = smoothstep(-0.1, 0.25, sunH);

    vec3 zenith = toLinear(skyColor) * mix(0.6, 1.1, day);
    vec3 horizon = toLinear(fogColor) * 1.05;
    float h = pow(clamp(upDot, 0.0, 1.0), 0.45);
    vec3 sky = mix(horizon, zenith, h);
    if (upDot < 0.0) sky = horizon * mix(1.0, 0.55, clamp(-upDot * 3.0, 0.0, 1.0));

    // Sun halo and sunset glow along the horizon.
    float sd = max(dot(dir, sunDir), 0.0);
    float sunset = clamp(1.0 - abs(sunH) * 3.0, 0.0, 1.0);
    vec3 glow = vec3(1.0, 0.55, 0.25) * (pow(sd, 6.0) * 0.9 * sunset + pow(sd, 32.0) * 0.6 * day);
    sky += glow * (1.0 - rainStrength) * (1.0 - h * 0.6);

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(sky, 1.0);
}
