#version 330

in vec2 tex_coord; // From the vertex shader
in vec4 color_pass;

uniform sampler2D tex; // The image created by the raytracer (in theory)

out vec4 color;

void main() {
    color = texture(tex, tex_coord);
}