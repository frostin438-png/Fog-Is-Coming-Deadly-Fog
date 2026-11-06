#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

// Tone mapping (filmic), eye adaptation to caves, film grading in thick fog, gamma.

uniform sampler2D colortex0;
uniform float fogEnd;
uniform float far;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;

varying vec2 texcoord;

vec3 acesFilm(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;

    float skyLight = clamp(float(eyeBrightnessSmooth.y) / 240.0, 0.0, 1.0);
    float exposure = EXPOSURE * mix(1.7, 1.0, skyLight);
    color = acesFilm(color * exposure);
    color = pow(color, vec3(1.0 / 2.2));

#ifdef FILM_GRADING
    float mf = isEyeInWater == 0 ? modFogAmount(fogEnd, far) : 0.0;
    float g = smoothstep(0.2, 0.8, mf);
    if (g > 0.0) {
        float l = luminance(color);
        color = mix(color, vec3(l), 0.30 * g);
        color *= mix(vec3(1.0), vec3(0.93, 0.98, 1.06), g);
        color = mix(color, (color - 0.5) * 1.07 + 0.5, g);
        vec2 v = texcoord - 0.5;
        v.x *= viewWidth / viewHeight;
        color *= mix(1.0, smoothstep(1.05, 0.35, length(v)), 0.38 * g);
        color += (hash12(gl_FragCoord.xy + floor(frameTimeCounter * 24.0) * 17.0) - 0.5) * 0.03 * g;
    }
#endif

    gl_FragData[0] = vec4(clamp(color, 0.0, 1.0), 1.0);
}
