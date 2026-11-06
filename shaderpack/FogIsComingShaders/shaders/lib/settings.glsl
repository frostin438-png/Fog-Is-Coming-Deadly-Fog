// ---------------------------------------------------------------- FOG IS COMING shaders: options
// Every option below shows up in the Oculus/Iris shader settings screen.

#define SHADOWS                 // Sun and moon shadows
#define SHADOW_FILTER 2         // Shadow softness [0 1 2]
#define WAVING_PLANTS           // Grass, flowers and leaves sway in the wind
#define WATER_WAVES             // Animated water surface with reflections
#define VOLUMETRIC_FOG          // Ray-marched fog with drifting clumps (driven by the Fog Is Coming mod)
#define FOG_QUALITY 14          // Fog ray-march steps [8 14 22]
#define LIGHT_GLOW              // Glow of light sources, much stronger inside the fog
#define FILM_GRADING            // Cold, slightly desaturated film look with grain in thick fog
#define EXPOSURE 1.0            // Overall brightness [0.8 0.9 1.0 1.1 1.2 1.35]

const int shadowMapResolution = 2048;   // [1024 2048 3072 4096]
const float shadowDistance = 96.0;      // [64.0 96.0 128.0 160.0]
const float sunPathRotation = -25.0;    // [-40.0 -25.0 -10.0 0.0 10.0 25.0]
const bool shadowHardwareFiltering = true;

#define SHADOW_DISTORT 0.85
