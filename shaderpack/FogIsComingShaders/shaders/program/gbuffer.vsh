// Shared vertex program for terrain, water, entities, particles and the hand.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

attribute vec4 mc_Entity;
attribute vec4 mc_midTexCoord;

uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform vec3 cameraPosition;
uniform float frameTimeCounter;
uniform float rainStrength;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 viewPos;
varying vec3 normalView;
varying vec3 worldPos;
varying float blockId;

vec3 plantWave(vec3 wp, float id, bool top, float time) {
    float strength = 0.0;
    if (id == ID_PLANT || id == ID_TALL_LOWER) strength = top ? 1.0 : 0.0;
    else if (id == ID_TALL_UPPER) strength = top ? 1.6 : 1.0;
    else if (id == ID_LEAVES) strength = 0.35;
    if (strength == 0.0) return vec3(0.0);
    float wind = 0.06 + 0.06 * rainStrength;
    float phase = time * 1.7 + dot(wp.xz, vec2(0.35, 0.21));
    float gust = 0.6 + 0.4 * sin(time * 0.37 + wp.x * 0.03 + wp.z * 0.02);
    vec3 offset;
    offset.x = sin(phase) * 0.8 + sin(phase * 2.3 + wp.z) * 0.25;
    offset.z = cos(phase * 0.9 + 1.3) * 0.6;
    offset.y = 0.0;
    return offset * wind * gust * strength;
}

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    glcolor = gl_Color;
    normalView = normalize(gl_NormalMatrix * gl_Normal);
    blockId = mc_Entity.x;

    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
    vec4 pp = gbufferModelViewInverse * vp;
    worldPos = pp.xyz + cameraPosition;
#if defined TERRAIN && defined WAVING_PLANTS
    bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
    pp.xyz += plantWave(worldPos, mc_Entity.x, top, frameTimeCounter);
    vp = gbufferModelView * pp;
#endif
    viewPos = vp.xyz;
    gl_Position = gl_ProjectionMatrix * vp;
}
