#version 120
#include "/lib/common.glsl"

// Sun and moon: drawn bright so they bloom; the fog pass hides them in thick fog.

uniform sampler2D gtexture;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(c.rgb) * 4.0, c.a);
}
