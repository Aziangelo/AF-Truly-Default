$input a_color0, a_position, a_texcoord0, a_texcoord1
#ifdef INSTANCING
    $input i_data0, i_data1, i_data2
#endif
$output v_color0, v_fog, v_texcoord0, v_lightmapUV, v_cpos, v_wpos, v_color1, v_color2, v_color3, v_color4, v_color5, v_color6, v_color7, v_color8, v_color9

#include <bgfx_shader.sh>
#include <azify/utils/functions.glsl>
// AziFy Truly Default;
uniform vec4 RenderChunkFogAlpha;
uniform vec4 FogAndDistanceControl;
uniform vec4 ViewPositionAndTime;
uniform vec4 FogColor;
uniform vec4 SkyColor;

void main() {
    mat4 model;
#ifdef INSTANCING
    model = mtxFromCols(i_data0, i_data1, i_data2, vec4(0, 0, 0, 1));
#else
    model = u_model[0];
#endif
#include <azify/utils/components.glsl> // Components Files

    vec3 worldPos = mul(model, vec4(a_position, 1.0)).xyz;
    vec4 color;
    color = a_color0;

    vec3 modelCamPos = (ViewPositionAndTime.xyz - worldPos);
    float camDis = length(modelCamPos);
    float relativeDist = camDis / FogAndDistanceControl.z;
    relativeDist += RenderChunkFogAlpha.x;
    vec4 fogColor;
    fogColor.rgb = FogColor.rgb;
    float density = mix(mix(0.15, 5.5, AFdusk), 0.25, AFnight);
    float rrt = exp(-relativeDist*relativeDist*density);
    float fog;
    fog = smoothstep(FogAndDistanceControl.x, FogAndDistanceControl.y, relativeDist);
    fog += (1.0-fog)*(0.5-0.5*rrt);

    // SKY BASED FOG
    vec3 skyPos = (worldPos.xyz + vec3(0.0, 0.128, 0.0));
    vec3 nskyposP = normalize(skyPos);
    vec3 fogMie;
    vec3 skyMIEP = dynamicSky(FogColor.rgb, nskyposP, AFnight, AFdusk, AFrain, SkyColor.rgb, FogColor.rgb);
    if (dev_UnWater) {
      fogMie = UNDERWATER_COLOR;
    } else if (dev_Nether || dev_End) {
      fogMie = FogColor.rgb;
    } else {
      fogMie = skyMIEP;
    }
    fogColor.rgb = fogMie;
    fogColor.a = fog;
#ifdef TRANSPARENT
    if(a_color0.a < 0.95) {
        color.a = mix(a_color0.a, 1.0, clamp((camDis / FogAndDistanceControl.w), 0.0, 1.0));
    };
#endif

  // MANIFIGULATOR (-_-?)
  float isCaveX = smoothstep(1.0, 0.35, a_texcoord1.y);
  float isCave = smoothstep(0.91, 0.77, a_texcoord1.y);
  float isLight = pow(a_texcoord1.x, 3.0);

  // DIRECT LIGHT REPLICA
  vec4 DirectLightColor;
  #ifdef DIRLIGHT_BOTTOM
    DirectLightColor.rgb = mix(vec3(1.0,1.0,1.0), vec3(0.7, 0.75, 0.8), a_texcoord1.y);
  #endif
  
  // SMOOTH AMBIENT OCCLUSION
  vec4 AOColor;
   float minColor = min(a_color0.r, a_color0.b);
   float aoFactor = a_color0.g * 2.0 - minColor;
   float vanillaAO = 1.0 - aoFactor * 1.4;
   float fakeAO = clamp(1.0- vanillaAO * 0.5, 0.0, 1.0);
   AOColor.rgb = mix(vec3(1.0,1.0,1.0), vec3(0.1, 0.11, 0.15), 1.0-fakeAO);
  
  
  // WORLD COLORS
  vec3 WorldColor;
  #ifdef ENABLE_LIGHTS
    vec3 dayColor = vec3(0.9, 0.94, 1.0);
    vec3 duskColor = vec3(0.54, 0.46, 0.42);
    vec3 nightColor = vec3(0.43, 0.43, 0.67);
    vec3 caveDayColor = vec3(0.45, 0.5, 0.6);
    vec3 caveDuskColor = vec3(0.2, 0.2, 0.2);
    vec3 caveNightColor = vec3(0.2, 0.2, 0.3);
    vec3 caveColor = mix(mix(caveDayColor, caveDuskColor, AFdusk), caveNightColor, AFnight);

    WorldColor = mix(mix(dayColor, duskColor, AFdusk), nightColor, AFnight);
    WorldColor = mix(WorldColor, vec3(0.14, 0.14, 0.14), isCaveX);
    WorldColor = mix(WorldColor, caveColor, isCave * (1.0 - isCaveX));
    WorldColor = mix(WorldColor, vec3(1.0, 1.0, 1.0), isLight);
  #endif


  // TORCH LIGHTS
  vec3 TorchColor;
  #if TORCHLIGHT_MODES == 0
    TorchColor = vec3(0.9, 0.9, 0.9) * a_texcoord1.x;
  #elif TORCHLIGHT_MODES == 1
    float torchPower = pow(a_texcoord1.x * 1.09, 4.0);
    TorchColor = torchColor * torchPower;
  #elif TORCHLIGHT_MODES == 2
    float smotherLight = smoothstep(0.7, 1.1, a_texcoord1.x);
    TorchColor = torchColor * smotherLight;
  #endif
  vec4 finalWColor;
  finalWColor = vec4(AOColor.rgb * WorldColor + TorchColor,1.0);


  // SUN BLOOM WHEN DUSK
  vec4 sunblPos;
  #ifdef SUN_BLOOM
    float invWorldPosX = 1.0 / worldPos.x;
    float sunlength = length(worldPos.zy * 4.5 * invWorldPosX);
    float sunBloom = clamp(1.0 - sunlength, 0.0, 1.0);
    float bloomFactor = smoothstep(0.0, 1.0, sunBloom) * SUN_BLOOM_STRENGTH * (1.0 - AFnight) * (1.0 - AFrain) * (AFdusk * a_texcoord1.y) * fogColor.a;
    vec3 bloomColor = vec3(1.0, 0.7, 0.1);
    
    sunblPos = vec4(bloomColor, bloomFactor);
  #endif


  // GROUND BLOOM WHEN DUSK
  vec4 grblColor;
  #ifdef GROUND_BLOOM
    vec2 posRatio = worldPos.zy / worldPos.x;
    float grblLen = length(posRatio * vec2(1.0, 0.5));
    float sunRayBloom = clamp(1.0 - grblLen, 0.0, 1.0);
    float mixFactor = smoothstep(0.0, 1.0, sunRayBloom) * GROUND_BLOOM_STRENGTH * (1.0 - isCave) * (1.0 - AFnight) * (1.0 - AFrain) * AFdusk;
    
    grblColor = vec4(skyMIEP, mixFactor);
  #endif


  // CALCULATE NOISE  =============>>>>>>>>
  float wpx = a_position.x * 6.0;
  float wpy = a_position.y * 0.5;
  float wpz = a_position.z * 1.0;
  vec2 waterDisp = vec2(wpz + ViewPositionAndTime.w* 0.03, wpx + ViewPositionAndTime.w* 1.45 + wpz + wpy);


  // WATER WAVES  =============>>>>>>>>
  vec3 nskyposN = normalize(-skyPos);
  vec3 skyMIEX = dynamicSky(FogColor.rgb, nskyposN, AFnight, AFdusk, AFrain, SkyColor.rgb, FogColor.rgb);
  vec4 waterOpa;
  vec4 waterSim;
  vec4 waterRy1;
  vec4 waterRy2;
  float wdisp = clamp(sin(noise(waterDisp)), 0.23, 1.0);
  waterOpa = vec4(skyMIEX, 1.0) * 0.3;
 
  #ifdef SIMULATED_WATER
   float fadeFact = clamp(length(vec2(worldPos.xz * 0.3 / worldPos.y * 0.6)), 0.0, 1.0) * 0.8;
   vec3 simCol = vec3(skyMIEX * mix(1.0, 2.5, AFrain * AFnight));
    waterSim.rgb = simCol;
    waterSim.a = fadeFact;
  #endif
    
  #ifdef WATER_SUNRAY
    waterRy1.rgb = skyMIEP;
    waterRy1.a = a_texcoord1.y * (1.0 - AFnight) * (1.0 - AFrain) * (AFdusk);
    waterRy2.rgb = vec3(1.0, 0.7, 0.15) * 1.8;
    waterRy2.a = a_texcoord1.y * (1.0 - AFnight) * (1.0 - AFrain) * (AFdusk);
  #endif

  // THICK RAIN FOG
  vec4 rainTfog;
  #ifdef RAIN_THICK_FOG
    float fogDist = clamp(dot(worldPos, worldPos) * 0.0008, 0.0, 1.0);
    rainTfog.rgb = skyMIEP;
    rainTfog.a = 0.85 * fogDist * AFrain;
  #endif


    v_texcoord0 = a_texcoord0;
    v_lightmapUV = a_texcoord1;
    v_color0 = a_color0;
    v_fog = fogColor;
    v_cpos = a_position;
    v_wpos = worldPos;
    v_color1 = DirectLightColor;
    v_color2 = finalWColor;
    v_color3 = sunblPos;
    v_color4 = grblColor;
    v_color5 = waterOpa;
    v_color6 = waterSim;
    v_color7 = waterRy1;
    v_color8 = waterRy2;
    v_color9 = rainTfog;
    gl_Position = mul(u_viewProj, vec4(worldPos, 1.0));

  // WAVE EFFECTS STARTS HERE =============>>>>>>>>
  float htime = ViewPositionAndTime.w;
  vec3 tlpos = mod(a_position, vec3(2.0,2.0,2.0));

  // CALCULATE HEAT WAVE - Licensed By: Azi Angelo
  #ifdef HEAT_WAVE
  if (dev_Nether) {
      float hw1 = sin(tlpos.z + htime * 11.0) * 0.005;
      float hw2 = sin(tlpos.x + htime * 11.0) * 0.003;
      float hw4 = sin(tlpos.y + htime * 11.0) * 0.00;
      float hwSum = hw1 + hw2 + hw4;
      
      gl_Position.xy += hwSum;
  }
  #endif

  // DETERMINE WATER TEXTURE
  #ifdef WATER_WAVE
  #if !defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY)
  #ifdef TRANSPARENT
  if (waterFlag) {
    highp float wtime = ViewPositionAndTime.w * WATER_WAVE_SPEED;
    float timeFactor1 = wtime * 2.0;
    float timeFactor2 = wtime * 1.5;
    float timeFactor4 = wtime * 5.0;
    
    float sinZ = sin(tlpos.z);
    float sinZ_t1 = sin(tlpos.z + timeFactor1) * 0.05;
    float sinX_t2 = sin(tlpos.x + timeFactor2) * 0.03;
    float sinY_t4 = sin(tlpos.y * 1.5 + timeFactor4) * 0.02;
    float sinZ_0 = sinZ * 0.1;
    
    gl_Position.y += sinZ_t1 + sinX_t2 + sinY_t4 * sinZ_0;
  }
  #endif
  #endif
  #endif
 
  // WAVE MOVEMENTS - Licensed By: Azi Angelo
  #ifdef PLANTS_WAVE
  bool isColors = color.r != color.g || color.r != color.b;
  #if defined(ALPHA_TEST)
   if (isColors) {
    highp float time = ViewPositionAndTime.w * PLANTS_WAVE_SPEED;
    float t1 = time * 1.5;
    float t2 = time * 0.4;
    float t3 = time * 1.2;
    float t4 = time * 3.0;
    
    float sinZ = sin(tlpos.z);
    float sinZ_t1 = sin(tlpos.z + t1) * 0.07;
    float sinX_t2 = sin(tlpos.x + t2) * 0.04;
    float cosZ_t3 = cos(tlpos.z + t3) * 0.05;
    float sinY_t4 = sin(tlpos.y * 1.5 + t4) * 0.1;
    float sinZ_0 = sinZ * 0.1;
    
    gl_Position.x += sinZ_t1 + sinX_t2 + cosZ_t3 + sinY_t4 * sinZ_0;
   }
  #endif
  #endif
}
