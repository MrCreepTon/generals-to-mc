#version 330
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
#ifdef GENERALS_ALPHA_TEST
    if (color.a < 0.5) discard;
#endif
    fragColor = color;
}
