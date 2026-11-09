#version 150

// FOG IS COMING: volumetric fog, light halos and cinematic grading.
//
// Runs once per frame after the world is rendered (before the hand and the HUD). It reads a copy of the
// scene colour and depth, rebuilds the world-space view ray of every pixel and ray-marches through a
// height-shaped, slowly drifting 3D noise field. Light sources near the player scatter in the fog
// (analytic in-scattering integral), and the final image gets a subtle cold film look in thick fog.

uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
// Same texture as SceneDepth normally; under a shader pack the depth with the hand (the nearer one wins).
uniform sampler2D HandDepth;

uniform mat4 InvViewProj;     // clip space -> camera-relative world space
uniform vec3 NoiseOrigin;     // camera position, wrapped horizontally (noise space)
uniform vec3 FogTint;
uniform vec4 FogParams;       // x: extinction per block, y: height falloff, z: noise amount, w: time (s)
uniform vec4 FogParams2;      // x: march distance, y: steps, z: indoor factor, w: grading amount
uniform vec4 WindParams;      // xyz: wind drift (blocks/s), w: noise scale
uniform vec4 HaloParams;      // x: strength, y: light count, z: deadliness, w: frame counter
uniform vec2 ScreenSize;

uniform vec4 Light0; uniform vec4 Light1; uniform vec4 Light2; uniform vec4 Light3;
uniform vec4 Light4; uniform vec4 Light5; uniform vec4 Light6; uniform vec4 Light7;
uniform vec4 LightColor0; uniform vec4 LightColor1; uniform vec4 LightColor2; uniform vec4 LightColor3;
uniform vec4 LightColor4; uniform vec4 LightColor5; uniform vec4 LightColor6; uniform vec4 LightColor7;

in vec2 texCoord;
out vec4 fragColor;

// ------------------------------------------------------------------ noise

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float valueNoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amp * valueNoise(p);
        p = p * 2.07 + vec3(17.3, 5.1, 9.7);
        amp *= 0.5;
    }
    return sum / 0.875;
}

// Interleaved gradient noise: per-pixel jitter that hides ray-march banding.
float ign(vec2 pixel, float frame) {
    pixel += frame * 5.588238;
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}

// ------------------------------------------------------------------ fog field

float fogDensity(vec3 rel, float t) {
    float k = FogParams.x;
    float time = FogParams.w;

    // Thick near the ground and around the player, thinning out above.
    float above = max(0.0, rel.y - 3.0);
    float height = exp(-above / FogParams.y);

    // Drifting, slowly evolving clumps of fog.
    vec3 p = (NoiseOrigin + rel) * WindParams.w;
    p -= WindParams.xyz * WindParams.w * time;
    p.y += time * 0.015;
    float n = fbm(p + vec3(0.0, 0.0, time * 0.02));
    float clumps = smoothstep(0.18, 0.82, n) * 2.0;
    float noiseTerm = mix(1.0, clumps, FogParams.z);

    // Indoors the fog does not fill the room around the camera.
    float indoor = mix(FogParams2.z, 1.0, smoothstep(3.0, 12.0, t));

    return k * height * noiseTerm * indoor;
}

// ------------------------------------------------------------------ light halos

vec3 halo(vec4 light, vec4 color, vec3 dir, float rayLength, float k, float time, float seed) {
    if (light.w <= 0.0) return vec3(0.0);
    float t0 = dot(light.xyz, dir);
    float h2 = max(dot(light.xyz, light.xyz) - t0 * t0, 0.0);
    float h = sqrt(h2 + 0.09);
    // Integral of 1 / distance^2 along the visible part of the ray.
    float integral = (atan((rayLength - t0) / h) + atan(t0 / h)) / h;
    // Light hidden behind the surface this pixel hits does not glow through it.
    float occlusion = exp(-max(0.0, t0 - rayLength - 0.6) * 1.5);
    // Light reaching the camera is itself dimmed by the fog in between.
    float attenuation = exp(-k * max(t0, 0.0) * 0.7);
    // Real glow is concentrated around the source: fade quickly with the ray's distance from the light
    // and with the light's distance from the camera (the tracker only reports nearby lights).
    float lateral = exp(-h * 0.55);
    float range = smoothstep(18.0, 9.0, length(light.xyz));
    float flicker = 1.0 - color.w * (0.10 * sin(time * 10.3 + seed * 7.1) + 0.06 * sin(time * 23.7 + seed * 3.3));
    return color.rgb * (integral * lateral * range * light.w * occlusion * attenuation * flicker);
}

// ------------------------------------------------------------------ main

void main() {
    vec3 scene = texture(SceneColor, texCoord).rgb;
    float depth = min(texture(SceneDepth, texCoord).r, texture(HandDepth, texCoord).r);
    vec2 ndcXY = texCoord * 2.0 - 1.0;

    vec4 farPoint = InvViewProj * vec4(ndcXY, 0.5, 1.0);
    vec3 dir = normalize(farPoint.xyz / farPoint.w);

    bool sky = depth >= 0.99999;
    float marchMax = FogParams2.x;
    float rayLength;
    if (sky) {
        rayLength = marchMax;
    } else {
        vec4 hit = InvViewProj * vec4(ndcXY, depth * 2.0 - 1.0, 1.0);
        rayLength = min(length(hit.xyz / hit.w), marchMax);
    }

    // Ray march the fog field.
    int steps = int(FogParams2.y);
    float stepLength = rayLength / float(steps);
    float jitter = ign(gl_FragCoord.xy, HaloParams.w);
    float optical = 0.0;
    for (int i = 0; i < 32; i++) {
        if (i >= steps) break;
        float t = (float(i) + jitter) * stepLength;
        optical += fogDensity(dir * t, t) * stepLength;
    }
    // Beyond the march distance the fog is opaque anyway; keep the far field consistent.
    if (sky || rayLength >= marchMax) optical += FogParams.x * 0.5 * marchMax;
    float transmittance = exp(-optical);

    vec3 color = mix(FogTint, scene, transmittance);

    // Light sources scattering in the fog.
    if (HaloParams.y > 0.0) {
        float k = FogParams.x;
        float time = FogParams.w;
        vec3 glow = vec3(0.0);
        glow += halo(Light0, LightColor0, dir, rayLength, k, time, 0.0);
        glow += halo(Light1, LightColor1, dir, rayLength, k, time, 1.0);
        glow += halo(Light2, LightColor2, dir, rayLength, k, time, 2.0);
        glow += halo(Light3, LightColor3, dir, rayLength, k, time, 3.0);
        glow += halo(Light4, LightColor4, dir, rayLength, k, time, 4.0);
        glow += halo(Light5, LightColor5, dir, rayLength, k, time, 5.0);
        glow += halo(Light6, LightColor6, dir, rayLength, k, time, 6.0);
        glow += halo(Light7, LightColor7, dir, rayLength, k, time, 7.0);
        glow *= HaloParams.x * min(k * 8.0, 1.0);
        // Soft shoulder so overlapping halos never clip to white.
        color += glow / (1.0 + max(max(glow.r, glow.g), glow.b));
    }

    // Cinematic grading in thick fog: colder, slightly desaturated, soft vignette, fine film grain.
    float g = FogParams2.w;
    if (g > 0.0) {
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        color = mix(color, vec3(luma), 0.30 * g);
        color *= mix(vec3(1.0), vec3(0.93, 0.98, 1.06), g);
        color = mix(color, (color - 0.5) * 1.07 + 0.5, g);
        vec2 v = texCoord - 0.5;
        v.x *= ScreenSize.x / ScreenSize.y;
        float vignette = smoothstep(1.05, 0.35, length(v));
        color *= mix(1.0, vignette, 0.38 * g);
        float grain = hash13(vec3(gl_FragCoord.xy, floor(FogParams.w * 24.0))) - 0.5;
        color += grain * 0.03 * g;
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
