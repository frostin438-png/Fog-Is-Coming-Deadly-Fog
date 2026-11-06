#version 120
#include "/lib/common.glsl"

uniform sampler2D gtexture;
uniform vec3 sunPosition;
uniform vec3 upPosition;
uniform vec3 fogColor;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    if (c.a < 0.05) discard;
    float day = smoothstep(-0.1, 0.25, dot(normalize(sunPosition), normalize(upPosition)));
    vec3 lit = toLinear(c.rgb) * mix(0.06, 1.15, day) + toLinear(fogColor) * 0.15;
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(lit, c.a * 0.85);
}
