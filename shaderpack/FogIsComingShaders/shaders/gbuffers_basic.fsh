#version 120
#include "/lib/common.glsl"

// Block outlines, leash lines and other untextured geometry.

varying vec4 glcolor;

void main() {
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(glcolor.rgb), glcolor.a);
}
