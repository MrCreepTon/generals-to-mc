#version 330
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
layout(std140) uniform FogData {mat4 InverseViewProjection;vec4 CameraPosition;vec4 FogBounds;};
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
void main(){
    float depth=texture(Sampler0,texCoord).r;
    vec4 relative=InverseViewProjection*vec4(texCoord*2.0-1.0,depth,1.0);
    vec3 world=CameraPosition.xyz+relative.xyz/relative.w;
    vec2 uv=(world.xz-FogBounds.xy)/FogBounds.z;
    float opacity=depth>0.000001?0.58:0.0;
    if(depth>0.000001&&all(greaterThanEqual(uv,vec2(0.0)))&&all(lessThanEqual(uv,vec2(1.0))))opacity=texture(Sampler1,uv).r;
    fragColor=vec4(0.0,0.0,0.0,opacity);
}
