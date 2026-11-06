#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

attribute vec4 mc_Entity;
attribute vec4 mc_midTexCoord;

uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float rainStrength;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    // Water does not cast shadows (it is lit through).
    if (mc_Entity.x == ID_WATER) {
        gl_Position = vec4(10.0, 10.0, 10.0, 1.0);
        return;
    }
    vec4 pp = shadowModelViewInverse * (gl_ModelViewMatrix * gl_Vertex);
#ifdef WAVING_PLANTS
    float id = mc_Entity.x;
    if (id == ID_PLANT || id == ID_TALL_LOWER || id == ID_TALL_UPPER || id == ID_LEAVES) {
        vec3 wp = pp.xyz + cameraPosition;
        bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
        float strength = id == ID_LEAVES ? 0.35 : (id == ID_TALL_UPPER ? (top ? 1.6 : 1.0) : (top ? 1.0 : 0.0));
        float phase = frameTimeCounter * 1.7 + dot(wp.xz, vec2(0.35, 0.21));
        float gust = 0.6 + 0.4 * sin(frameTimeCounter * 0.37 + wp.x * 0.03 + wp.z * 0.02);
        vec3 offset = vec3(sin(phase) * 0.8 + sin(phase * 2.3 + wp.z) * 0.25, 0.0, cos(phase * 0.9 + 1.3) * 0.6);
        pp.xyz += offset * (0.06 + 0.06 * rainStrength) * gust * strength;
    }
#endif
    gl_Position = shadowProjection * (shadowModelView * pp);
    gl_Position.xyz = distortShadow(gl_Position.xyz);
}
