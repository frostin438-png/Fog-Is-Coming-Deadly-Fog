#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

/*
 * Light glow. Bright parts of the image (light sources, the sun) are blurred through the mip chain and
 * added back. In the Fog Is Coming fog the glow gets much wider and stronger: light scatters in the fog,
 * so torches and lanterns get soft halos.
 */

const bool colortex0MipmapEnabled = true;

uniform sampler2D colortex0;
uniform float fogEnd;
uniform float far;
uniform float rainStrength;
uniform int isEyeInWater;

varying vec2 texcoord;

vec3 brightPart(vec3 c) {
    float l = luminance(c);
    return c * smoothstep(0.9, 3.0, l);
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;
#ifdef LIGHT_GLOW
    float mf = isEyeInWater == 0 ? modFogAmount(fogEnd, far) : 0.4;
    vec3 glow = vec3(0.0);
    // Tight glow in clear air; in fog the wide mip levels take over and form soft halos.
    glow += brightPart(texture2D(colortex0, texcoord, 2.0).rgb) * mix(0.30, 0.10, mf);
    glow += brightPart(texture2D(colortex0, texcoord, 3.5).rgb) * 0.25;
    glow += brightPart(texture2D(colortex0, texcoord, 5.0).rgb) * mix(0.25, 0.35, mf);
    glow += brightPart(texture2D(colortex0, texcoord, 6.5).rgb) * mix(0.15, 0.55, mf);
    glow += brightPart(texture2D(colortex0, texcoord, 8.0).rgb) * mix(0.0, 0.6, mf);
    float strength = 0.08 + 0.45 * mf + 0.15 * rainStrength;
    color += glow * strength;
#endif
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, 1.0);
}
